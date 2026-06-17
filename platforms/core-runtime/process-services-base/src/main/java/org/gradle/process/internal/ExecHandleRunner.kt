/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.process.internal

import com.google.common.io.CharStreams
import net.rubygrapefruit.platform.ProcessLauncher
import org.apache.commons.lang3.StringUtils
import org.gradle.api.JavaVersion
import org.gradle.api.logging.Logging.getLogger
import org.gradle.internal.operations.BuildOperationRef
import org.gradle.internal.operations.CurrentBuildOperationRef
import org.gradle.internal.os.OperatingSystem
import org.gradle.process.internal.streams.StreamsHandler
import java.io.InputStreamReader
import java.lang.reflect.InvocationTargetException
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executor
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import java.util.stream.Stream
import kotlin.concurrent.Volatile

class ExecHandleRunner(
    execHandle: DefaultExecHandle, streamsHandler: StreamsHandler, processLauncher: ProcessLauncher, executor: Executor?,
    associatedBuildOperation: BuildOperationRef?
) : Runnable {
    private val processBuilderFactory: ProcessBuilderFactory
    private val execHandle: DefaultExecHandle
    private val lock: Lock = ReentrantLock()
    private val processLauncher: ProcessLauncher
    private val executor: Executor?

    private var process: Process? = null
    private var aborted = false
    private val streamsHandler: StreamsHandler

    @Volatile
    private var associatedBuildOperation: BuildOperationRef?

    init {
        requireNotNull(execHandle) { "execHandle == null!" }
        this.execHandle = execHandle
        this.streamsHandler = streamsHandler
        this.processLauncher = processLauncher
        this.executor = executor
        this.associatedBuildOperation = associatedBuildOperation
        this.processBuilderFactory = ProcessBuilderFactory()
    }

    fun sendSignal(signal: Int) {
        if (OperatingSystem.current().isWindows()) {
            throw UnsupportedOperationException("Sending signals is not supported on Windows")
        }
        lock.lock()
        try {
            checkNotNull(process) { "Cannot send signal " + signal + ": the process has not started yet" }
            try {
                val pid: Long = Companion.getProcessId(process!!)
                val command = arrayOf<String?>("kill", "-" + signal, pid.toString())
                val kill = ProcessBuilder(*command)
                    .redirectErrorStream(true)
                    .start()
                val exitCode = kill.waitFor()
                if (exitCode != 0) {
                    val output = CharStreams.toString(InputStreamReader(kill.getInputStream(), StandardCharsets.UTF_8)).trim { it <= ' ' }
                    val message = StringUtils.join(command, " ") + " failed with exit code " + exitCode
                    throw RuntimeException(message + (if (output.isEmpty()) "" else output))
                }
            } catch (e: RuntimeException) {
                throw e
            } catch (e: Exception) {
                throw RuntimeException("Failed to send signal " + signal + " to process", e)
            }
        } finally {
            lock.unlock()
        }
    }

    fun abortProcess() {
        lock.lock()
        try {
            if (aborted) {
                return
            }
            aborted = true
            if (process != null) {
                streamsHandler.disconnect()
                LOGGER!!.debug("Abort requested. Destroying process: {}.", execHandle.getDisplayName())
                destroyProcessTree()
            }
        } finally {
            lock.unlock()
        }
    }

    /**
     * Destroys the process of this runner and its known (grand)children.
     * Falls back to only destroying the main process if the code runs on Java 8 or lower, which is the Gradle 8 or lower behavior.
     */
    private fun destroyProcessTree() {
        if (JavaVersion.current().isJava9Compatible()) {
            destroyDescendants()
        }
        process!!.destroy()
    }

    private fun destroyDescendants() {
        try {
            val descendants = Process::class.java.getMethod("descendants").invoke(process) as Stream<Any?>
            val destroyMethod = Class.forName("java.lang.ProcessHandle").getMethod("destroy")
            val it = descendants.iterator()
            while (it.hasNext()) {
                destroyMethod.invoke(it.next())
            }
        } catch (e: NoSuchMethodException) {
            throw RuntimeException("Failed to destroy descendants of process: " + execHandle.getDisplayName(), e)
        } catch (e: IllegalAccessException) {
            throw RuntimeException("Failed to destroy descendants of process: " + execHandle.getDisplayName(), e)
        } catch (e: InvocationTargetException) {
            throw RuntimeException("Failed to destroy descendants of process: " + execHandle.getDisplayName(), e)
        } catch (e: ClassNotFoundException) {
            throw RuntimeException("Failed to destroy descendants of process: " + execHandle.getDisplayName(), e)
        }
    }

    override fun run() {
        // Split the `with` operation so that the `associatedBuildOperation` can be discarded when we wait in `process.waitFor()`
        try {
            CurrentBuildOperationRef.instance().with(this.associatedBuildOperation, Runnable {
                startProcess()
                execHandle.started()

                LOGGER!!.debug("waiting until streams are handled...")
                streamsHandler.start()
            })

            if (execHandle.isDaemon()) {
                CurrentBuildOperationRef.instance().with(this.associatedBuildOperation, Runnable {
                    streamsHandler.stop()
                    detached()
                })
            } else {
                val exitValue = process!!.waitFor()
                CurrentBuildOperationRef.instance().with(this.associatedBuildOperation, Runnable {
                    streamsHandler.stop()
                    completed(exitValue)
                })
            }
        } catch (t: Throwable) {
            CurrentBuildOperationRef.instance().with(this.associatedBuildOperation, Runnable {
                execHandle.failed(t)
            })
        }
    }

    /**
     * Remove any context associated with tracking the startup of this process.
     */
    fun removeStartupContext() {
        this.associatedBuildOperation = null
        streamsHandler.removeStartupContext()
    }

    private fun startProcess() {
        lock.lock()
        try {
            check(!aborted) { "Process has already been aborted" }
            val processBuilder = processBuilderFactory.createProcessBuilder(execHandle)
            val process = processLauncher.start(processBuilder)
            streamsHandler.connectStreams(process, execHandle.getDisplayName(), executor)
            this.process = process
        } finally {
            lock.unlock()
        }
    }

    private fun completed(exitValue: Int) {
        if (aborted) {
            execHandle.aborted(exitValue)
        } else {
            execHandle.finished(exitValue)
        }
    }

    private fun detached() {
        execHandle.detached()
    }

    companion object {
        private val LOGGER = getLogger(ExecHandleRunner::class.java)

        @Throws(Exception::class)
        private fun getProcessId(process: Process): Long {
            try {
                // Java 9+: Process.pid()
                return (java.lang.Process::class.java.getMethod("pid").invoke(process) as kotlin.Long?)!!
            } catch (e: NoSuchMethodException) {
                // Java 8 fallback: UNIXProcess exposes a private 'pid' int field
                val pidField = process.javaClass.getDeclaredField("pid")
                pidField.setAccessible(true)
                return (pidField.get(process) as Number).toLong()
            }
        }
    }
}
