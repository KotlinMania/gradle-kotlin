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

import com.google.common.base.Joiner
import jnr.constants.platform.Signal
import net.rubygrapefruit.platform.ProcessLauncher
import org.gradle.api.logging.Logging.getLogger
import org.gradle.initialization.BuildCancellationToken
import org.gradle.internal.UncheckedException
import org.gradle.internal.event.ListenerBroadcast
import org.gradle.internal.nativeintegration.services.NativeServices.Companion.getInstance
import org.gradle.internal.operations.CurrentBuildOperationRef
import org.gradle.internal.os.OperatingSystem
import org.gradle.process.ExecResult
import org.gradle.process.ProcessExecutionException
import org.gradle.process.internal.shutdown.ShutdownHooks
import org.gradle.process.internal.streams.StreamsHandler
import org.gradle.process.internal.util.LongCommandLineDetectionUtil
import java.io.File
import java.util.Arrays
import java.util.Collections
import java.util.concurrent.Executor
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import java.util.stream.Stream

/**
 * Default implementation for the ExecHandle interface.
 *
 * <h2>State flows</h2>
 *
 *
 *  * INIT -&gt; STARTED -&gt; [SUCCEEDED|FAILED|ABORTED|DETACHED]
 *  * INIT -&gt; FAILED
 *  * INIT -&gt; STARTED -&gt; DETACHED -&gt; ABORTED
 *
 *
 * State is controlled on all control methods:
 *
 *  * [.start] allowed when state is INIT
 *  * [.abort] allowed when state is STARTED or DETACHED
 *
 */
class DefaultExecHandle internal constructor(
    private val displayName: String?,
    /**
     * The working directory of the process.
     */
    private val directory: File,
    /**
     * The executable to run.
     */
    private val command: String,
    /**
     * Arguments to pass to the executable.
     */
    private val arguments: MutableList<String>,
    /**
     * The variables to set in the environment the executable is run in.
     */
    private val environment: MutableMap<String, String>, private val outputHandler: StreamsHandler, private val inputHandler: StreamsHandler,
    listeners: MutableList<ExecHandleListener>, private val redirectErrorStream: Boolean, val timeout: Int, val isDaemon: Boolean,
    private val executor: Executor, private val buildCancellationToken: BuildCancellationToken
) : ExecHandle, ProcessSettings {
    private val processLauncher: ProcessLauncher

    /**
     * Lock to guard all mutable state
     */
    private val lock: Lock
    private val stateChanged: Condition

    /**
     * State of this ExecHandle.
     */
    private var state: ExecHandleState

    /**
     * When not null, the runnable that is waiting
     */
    private var execHandleRunner: ExecHandleRunner? = null

    private var execResult: ExecResultImpl? = null

    private val broadcast: ListenerBroadcast<ExecHandleListener>

    private val shutdownHookAction: ExecHandleShutdownHookAction

    init {
        this.lock = ReentrantLock()
        this.stateChanged = lock.newCondition()
        this.state = ExecHandleState.INIT
        @Suppress("UNCHECKED_CAST")
        processLauncher = getInstance().get<ProcessLauncher>(ProcessLauncher::class.java as Class<ProcessLauncher?>)!!
        shutdownHookAction = ExecHandleShutdownHookAction(this)
        @Suppress("UNCHECKED_CAST")
        broadcast = ListenerBroadcast<ExecHandleListener>(ExecHandleListener::class.java as Class<ExecHandleListener?>)
        broadcast.addAll(listeners)
    }

    override fun getDirectory(): File {
        return directory
    }

    override fun getCommand(): String {
        return command
    }

    override fun toString(): String {
        return displayName!!
    }

    override fun getArguments(): MutableList<String> {
        return Collections.unmodifiableList<String>(arguments)
    }

    override fun getEnvironment(): MutableMap<String, String> {
        return Collections.unmodifiableMap<String, String>(environment)
    }

    override fun getState(): ExecHandleState {
        lock.lock()
        try {
            return state
        } finally {
            lock.unlock()
        }
    }

    private fun setState(state: ExecHandleState) {
        lock.lock()
        try {
            LOGGER!!.debug("Changing state to: {}", state)
            this.state = state
            this.stateChanged.signalAll()
        } finally {
            lock.unlock()
        }
    }

    private fun stateIn(vararg states: ExecHandleState?): Boolean {
        lock.lock()
        try {
            return Arrays.asList<ExecHandleState?>(*states).contains(this.state)
        } finally {
            lock.unlock()
        }
    }

    private fun setEndStateInfo(newState: ExecHandleState, exitValue: Int, failureCause: Throwable?) {
        ShutdownHooks.removeShutdownHook(shutdownHookAction)
        buildCancellationToken.removeCallback(shutdownHookAction)
        var currentState: ExecHandleState
        lock.lock()
        try {
            currentState = this.state
        } finally {
            lock.unlock()
        }

        var newResult = ExecResultImpl(exitValue, execExceptionFor(failureCause, currentState), displayName)
        if (!currentState.isTerminal && newState != ExecHandleState.DETACHED) {
            try {
                broadcast.getSource()!!.executionFinished(this, newResult)
            } catch (e: Exception) {
                newResult = ExecResultImpl(exitValue, execExceptionFor(e, currentState), displayName)
            }
        }

        lock.lock()
        try {
            setState(newState)
            this.execResult = newResult
        } finally {
            lock.unlock()
        }

        LOGGER!!.debug("Process '{}' finished with exit value {} (state: {})", displayName, exitValue, newState)
    }

    private fun execExceptionFor(failureCause: Throwable?, currentState: ExecHandleState?): ProcessExecutionException? {
        return if (failureCause != null)
            ProcessExecutionException(failureMessageFor(failureCause, currentState), failureCause)
        else
            null
    }

    private fun failureMessageFor(failureCause: Throwable, currentState: ExecHandleState?): String {
        if (currentState == ExecHandleState.STARTING) {
            if (LongCommandLineDetectionUtil.hasCommandLineExceedMaxLength(command, arguments) && LongCommandLineDetectionUtil.hasCommandLineExceedMaxLengthException(failureCause)) {
                return String.format("Process '%s' could not be started because the command line exceed operating system limits.", displayName)
            }
            return String.format("A problem occurred starting process '%s'", displayName)
        }
        return String.format("A problem occurred waiting for process '%s' to complete.", displayName)
    }

    override fun start(): ExecHandle {
        LOGGER!!.info(
            "Starting process '{}'. Working directory: {} Command: {} {}",
            displayName, directory, command, ARGUMENT_JOINER.join(arguments)
        )
        lock.lock()
        try {
            check(stateIn(ExecHandleState.INIT)) { String.format("Cannot start process '%s' because it has already been started", displayName) }
            require(directory.exists()) { String.format("Working directory '%s' does not exist.", directory) }
            require(directory.isDirectory()) { String.format("Working directory '%s' is not a directory.", directory) }

            setState(ExecHandleState.STARTING)

            broadcast.getSource()!!.beforeExecutionStarted(this)
            execHandleRunner = ExecHandleRunner(
                this, CompositeStreamsHandler(), processLauncher, executor, CurrentBuildOperationRef.instance().get()
            )
            executor.execute(execHandleRunner)

            while (stateIn(ExecHandleState.STARTING)) {
                LOGGER.debug("Waiting until process started: {}.", displayName)
                try {
                    stateChanged.await()
                } catch (e: InterruptedException) {
                    execHandleRunner!!.abortProcess()
                    throw UncheckedException.throwAsUncheckedException(e)
                }
            }

            if (execResult != null) {
                execResult!!.rethrowFailure()
            }

            LOGGER.info("Successfully started process '{}'", displayName)
        } finally {
            lock.unlock()
        }
        return this
    }

    override fun sendSignal(signal: Int) {
        execHandleRunner!!.sendSignal(signal)
    }

    override fun removeStartupContext() {
        lock.lock()
        try {
            check(stateIn(ExecHandleState.STARTED)) { String.format("Cannot remove start context of process '%s' because it is not in started state", displayName) }
            execHandleRunner!!.removeStartupContext()
        } finally {
            lock.unlock()
        }
    }

    override fun abort() {
        lock.lock()
        try {
            if (stateIn(ExecHandleState.SUCCEEDED, ExecHandleState.FAILED, ExecHandleState.ABORTED)) {
                return
            }
            check(stateIn(ExecHandleState.STARTED, ExecHandleState.DETACHED)) { String.format("Cannot abort process '%s' because it is not in started or detached state", displayName) }
            this.execHandleRunner!!.abortProcess()
            this.waitForFinish()
        } finally {
            lock.unlock()
        }
    }

    override fun waitForFinish(): ExecResult {
        lock.lock()
        try {
            while (!state.isTerminal) {
                try {
                    stateChanged.await()
                } catch (e: InterruptedException) {
                    execHandleRunner!!.abortProcess()
                    throw UncheckedException.throwAsUncheckedException(e)
                }
            }
        } finally {
            lock.unlock()
        }

        // At this point:
        // If in daemon mode, the process has started successfully and all streams to the process have been closed
        // If in fork mode, the process has completed and all cleanup has been done
        // In both cases, all asynchronous work for the process has completed and we're done
        return result()
    }

    override fun getExecResult(): ExecResult? {
        lock.lock()
        try {
            return execResult
        } finally {
            lock.unlock()
        }
    }

    private fun result(): ExecResult {
        lock.lock()
        try {
            return execResult!!.rethrowFailure()
        } finally {
            lock.unlock()
        }
    }

    fun detached() {
        setEndStateInfo(ExecHandleState.DETACHED, 0, null)
    }

    fun started() {
        ShutdownHooks.addShutdownHook(shutdownHookAction)
        buildCancellationToken.addCallback(shutdownHookAction)
        setState(ExecHandleState.STARTED)
        broadcast.getSource()!!.executionStarted(this)
    }

    fun finished(exitCode: Int) {
        if (exitCode != 0) {
            setEndStateInfo(ExecHandleState.FAILED, exitCode, null)
        } else {
            setEndStateInfo(ExecHandleState.SUCCEEDED, 0, null)
        }
    }

    fun aborted(exitCode: Int) {
        var exitCode = exitCode
        if (exitCode == 0) {
            // This can happen on Windows
            exitCode = -1
        }
        setEndStateInfo(ExecHandleState.ABORTED, exitCode, null)
    }

    fun failed(failureCause: Throwable?) {
        setEndStateInfo(ExecHandleState.FAILED, -1, failureCause)
    }

    override fun addListener(listener: ExecHandleListener) {
        broadcast.add(listener)
    }

    override fun removeListener(listener: ExecHandleListener) {
        broadcast.remove(listener)
    }

    override fun getDisplayName(): String? {
        return displayName
    }

    override fun getRedirectErrorStream(): Boolean {
        return redirectErrorStream
    }

    private class ExecResultImpl(override val exitValue: Int, private val failure: ProcessExecutionException?, private val displayName: String?) : ExecResult {
        @Throws(ProcessExecutionException::class)
        override fun assertNormalExitValue(): ExecResult? {
            if (exitValue != 0) {
                val hint: String = getExitCodeHint(exitValue)
                throw ProcessExecutionException(String.format("Process '%s' finished with non-zero exit value %d%s", displayName, exitValue, hint))
            }
            return this
        }

        @Throws(ProcessExecutionException::class)
        override fun rethrowFailure(): ExecResult {
            if (failure != null) {
                throw failure
            }
            return this
        }

        override fun toString(): String {
            return "{exitValue=" + exitValue + ", failure=" + failure + "}"
        }

        companion object {
            private fun getExitCodeHint(exitValue: Int): String {
                if (OperatingSystem.current().isUnix && exitValue > 128) {
                    val signalNumber = exitValue - 128
                    val signal = Stream.of<Signal?>(*Signal.entries.toTypedArray())
                        .filter { s: Signal? -> s!!.intValue() == signalNumber }
                        .findFirst()
                        .orElse(null)

                    if (signal != null) {
                        return String.format(" (this value may indicate that the process was terminated with the %s signal%s)", signal.description(), getAdditionalHint(signal))
                    } else {
                        return ""
                    }
                }
                if (OperatingSystem.current().isWindows && exitValue > -0x40000000 && exitValue < 0) {
                    return String.format(" (NTSTATUS 0x%08X)", exitValue)
                }
                return ""
            }

            private fun getAdditionalHint(signal: Signal?): String {
                if (signal == Signal.SIGKILL) {
                    return ", which is often caused by the system running out of memory"
                }
                return ""
            }
        }
    }

    private inner class CompositeStreamsHandler : StreamsHandler {
        override fun connectStreams(process: Process, processName: String?, executor: Executor) {
            inputHandler.connectStreams(process, processName, executor)
            outputHandler.connectStreams(process, processName, executor)
        }

        override fun start() {
            inputHandler.start()
            outputHandler.start()
        }

        override fun removeStartupContext() {
            inputHandler.removeStartupContext()
            outputHandler.removeStartupContext()
        }

        override fun stop() {
            outputHandler.stop()
            inputHandler.stop()
        }

        override fun disconnect() {
            outputHandler.disconnect()
            inputHandler.disconnect()
        }
    }

    companion object {
        private val LOGGER = getLogger(DefaultExecHandle::class.java)

        private val ARGUMENT_JOINER = Joiner.on(' ').useForNull("null")
    }
}
