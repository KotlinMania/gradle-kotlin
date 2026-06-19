/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.internal.logging.services

import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.StandardOutputListener
import org.gradle.api.logging.configuration.ConsoleOutput
import org.gradle.api.logging.configuration.ConsoleUnicodeSupport
import org.gradle.internal.concurrent.CompositeStoppable
import org.gradle.internal.concurrent.Stoppable
import org.gradle.internal.logging.LoggingManagerInternal
import org.gradle.internal.logging.LoggingOutputInternal
import org.gradle.internal.logging.config.LoggingRouter
import org.gradle.internal.logging.config.LoggingSourceSystem
import org.gradle.internal.logging.config.LoggingSystem
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.logging.text.StreamBackedStandardOutputListener
import org.gradle.internal.nativeintegration.console.ConsoleMetaData
import java.io.Closeable
import java.io.OutputStream

class DefaultLoggingManager(
    slf4jLoggingSystem: LoggingSourceSystem, javaUtilLoggingSystem: LoggingSourceSystem, stdOutLoggingSystem: LoggingSourceSystem,
    stdErrLoggingSystem: LoggingSourceSystem, loggingRouter: LoggingRouter
) : LoggingManagerInternal, Closeable {
    private var started = false
    private val slf4jLoggingSystem: StartableLoggingSystem
    private val stdOutLoggingSystem: StartableLoggingSystem
    private val stdErrLoggingSystem: StartableLoggingSystem
    private val javaUtilLoggingSystem: StartableLoggingSystem
    private val loggingRouter: StartableLoggingRouter
    private var enableStdOutListeners = false
    private val loggingOutput: LoggingOutputInternal
    private val stdoutListeners: MutableSet<StandardOutputListener?> = LinkedHashSet<StandardOutputListener?>()
    private val stderrListeners: MutableSet<StandardOutputListener?> = LinkedHashSet<StandardOutputListener?>()
    private val outputEventListeners: MutableSet<OutputEventListener> = LinkedHashSet<OutputEventListener>()

    init {
        this.loggingOutput = loggingRouter
        this.loggingRouter = StartableLoggingRouter(loggingRouter)
        this.slf4jLoggingSystem = StartableLoggingSystem(slf4jLoggingSystem, null)
        this.stdOutLoggingSystem = StartableLoggingSystem(stdOutLoggingSystem, null)
        this.stdErrLoggingSystem = StartableLoggingSystem(stdErrLoggingSystem, null)
        this.javaUtilLoggingSystem = StartableLoggingSystem(javaUtilLoggingSystem, null)
    }

    override fun start(): DefaultLoggingManager {
        started = true
        if (enableStdOutListeners) {
            loggingRouter.loggingRouter.enableUserStandardOutputListeners()
        }
        for (stdoutListener in stdoutListeners) {
            loggingOutput.addStandardOutputListener(stdoutListener)
        }
        for (stderrListener in stderrListeners) {
            loggingOutput.addStandardErrorListener(stderrListener)
        }
        for (outputEventListener in outputEventListeners) {
            loggingOutput.addOutputEventListener(outputEventListener)
        }
        loggingRouter.start()

        slf4jLoggingSystem.enableCapture()
        slf4jLoggingSystem.start()

        javaUtilLoggingSystem.start()
        stdOutLoggingSystem.start()
        stdErrLoggingSystem.start()

        return this
    }

    override fun stop(): DefaultLoggingManager {
        try {
            CompositeStoppable.stoppable(slf4jLoggingSystem, javaUtilLoggingSystem, stdOutLoggingSystem, stdErrLoggingSystem).stop()
            for (stdoutListener in stdoutListeners) {
                loggingOutput.removeStandardOutputListener(stdoutListener)
            }
            for (stderrListener in stderrListeners) {
                loggingOutput.removeStandardErrorListener(stderrListener)
            }
            for (listener in outputEventListeners) {
                loggingOutput.removeOutputEventListener(listener)
            }
            loggingRouter.stop()
        } finally {
            started = false
        }
        return this
    }

    override fun close() {
        stop()
    }

    override fun setLevelInternal(logLevel: LogLevel?): DefaultLoggingManager {
        slf4jLoggingSystem.setLevel(logLevel)
        javaUtilLoggingSystem.setLevel(logLevel)
        loggingRouter.setLevel(logLevel)
        return this
    }

    override val level: LogLevel?
        get() = slf4jLoggingSystem.levelValue

    override fun captureSystemSources(): DefaultLoggingManager {
        stdOutLoggingSystem.enableCapture()
        stdErrLoggingSystem.enableCapture()
        javaUtilLoggingSystem.enableCapture()
        return this
    }

    override val standardOutputCaptureLevel: LogLevel?
        get() = stdOutLoggingSystem.levelValue

    override fun captureStandardOutput(level: LogLevel?): DefaultLoggingManager {
        stdOutLoggingSystem.setLevel(level)
        return this
    }

    override fun captureStandardError(level: LogLevel?): DefaultLoggingManager {
        stdErrLoggingSystem.setLevel(level)
        return this
    }

    override val standardErrorCaptureLevel: LogLevel?
        get() = stdErrLoggingSystem.levelValue

    override fun enableUserStandardOutputListeners(): LoggingManagerInternal {
        enableStdOutListeners = true
        return this
    }

    override fun addStandardOutputListener(listener: StandardOutputListener?) {
        if (stdoutListeners.add(listener) && started) {
            loggingOutput.addStandardOutputListener(listener)
        }
    }

    override fun addStandardErrorListener(listener: StandardOutputListener?) {
        if (stderrListeners.add(listener) && started) {
            loggingOutput.addStandardErrorListener(listener)
        }
    }

    override fun addStandardOutputListener(outputStream: OutputStream) {
        addStandardOutputListener(StreamBackedStandardOutputListener(outputStream))
    }

    override fun addStandardErrorListener(outputStream: OutputStream) {
        addStandardErrorListener(StreamBackedStandardOutputListener(outputStream))
    }

    override fun removeStandardOutputListener(listener: StandardOutputListener?) {
        if (stdoutListeners.remove(listener) && started) {
            loggingOutput.removeStandardOutputListener(listener)
        }
    }

    override fun removeStandardErrorListener(listener: StandardOutputListener?) {
        if (stderrListeners.remove(listener) && started) {
            loggingOutput.removeStandardErrorListener(listener)
        }
    }

    override fun addOutputEventListener(listener: OutputEventListener) {
        if (outputEventListeners.add(listener) && started) {
            loggingOutput.addOutputEventListener(listener)
        }
    }

    override fun removeOutputEventListener(listener: OutputEventListener) {
        if (outputEventListeners.remove(listener) && started) {
            loggingOutput.removeOutputEventListener(listener)
        }
    }

    override fun attachProcessConsole(consoleOutput: ConsoleOutput, consoleUnicodeSupport: ConsoleUnicodeSupport?) {
        loggingRouter.attachProcessConsole(consoleOutput, consoleUnicodeSupport)
    }

    override fun attachConsole(outputStream: OutputStream, errorStream: OutputStream, consoleOutput: ConsoleOutput?) {
        loggingRouter.attachConsole(outputStream, errorStream, consoleOutput, null)
    }

    override fun attachConsole(outputStream: OutputStream, errorStream: OutputStream, consoleOutput: ConsoleOutput?, consoleMetadata: ConsoleMetaData?) {
        loggingRouter.attachConsole(outputStream, errorStream, consoleOutput, consoleMetadata)
    }

    override fun attachSystemOutAndErr() {
        loggingOutput.attachSystemOutAndErr()
    }

    override fun flush() {
        loggingRouter.flush()
    }

    private class StartableLoggingRouter(val loggingRouter: LoggingRouter) : Stoppable {
        private var level: LogLevel? = null
        private var originalState: LoggingSystem.Snapshot? = null
        private var consoleAttachment: Runnable? = null

        fun start() {
            originalState = loggingRouter.snapshot()
            if (level != null) {
                loggingRouter.configure(level)
            }
            if (consoleAttachment != null) {
                consoleAttachment!!.run()
            }
        }

        fun addConsoleAttachment(consoleAttachment: Runnable) {
            if (consoleAttachment == this.consoleAttachment) {
                return
            }
            if (this.consoleAttachment != null) {
                throw UnsupportedOperationException("Not implemented yet.")
            }

            if (originalState != null) {
                // Already started
                consoleAttachment.run()
            }
            this.consoleAttachment = consoleAttachment
        }

        fun attachProcessConsole(consoleOutput: ConsoleOutput, consoleUnicodeSupport: ConsoleUnicodeSupport?) {
            addConsoleAttachment(ProcessConsoleAttachment(loggingRouter, consoleOutput, consoleUnicodeSupport))
        }

        fun attachConsole(outputStream: OutputStream, errorStream: OutputStream, consoleOutput: ConsoleOutput?, consoleMetadata: ConsoleMetaData?) {
            addConsoleAttachment(ConsoleAttachment(loggingRouter, outputStream, errorStream, consoleOutput, consoleMetadata))
        }

        fun setLevel(logLevel: LogLevel?) {
            if (this.level == logLevel) {
                return
            }

            if (originalState != null) {
                // Already started
                loggingRouter.configure(logLevel)
            }
            level = logLevel
        }

        override fun stop() {
            try {
                if (originalState != null) {
                    loggingRouter.restore(originalState)
                }
            } finally {
                originalState = null
            }
        }

        fun flush() {
            loggingRouter.flush()
        }
    }

    private class StartableLoggingSystem(private val loggingSystem: LoggingSourceSystem, var levelValue: LogLevel?) : Stoppable {
        private var enabled = false
        private var originalState: LoggingSystem.Snapshot? = null

        /**
         * Start this logging system: take a snapshot of the current state and start capturing events if enabled.
         */
        fun start() {
            originalState = loggingSystem.snapshot()
            if (levelValue != null) {
                loggingSystem.setLevel(levelValue)
            }
            if (enabled) {
                loggingSystem.startCapture()
            }
        }

        /**
         * Start capturing events from this logging system. Does not take effect until started.
         */
        fun enableCapture() {
            if (enabled) {
                return
            }

            enabled = true
            if (originalState != null) {
                //started, enable
                loggingSystem.startCapture()
            }
        }

        /**
         * Sets the logging level for this log system. Does not take effect until started .
         */
        fun setLevel(logLevel: LogLevel?) {
            if (this.levelValue == logLevel) {
                return
            }

            this.levelValue = logLevel
            if (originalState != null) {
                // started, update the log level
                loggingSystem.setLevel(logLevel)
            }
        }

        /**
         * Stops this logging system. Restores state from when started.
         */
        override fun stop() {
            try {
                if (originalState != null) {
                    loggingSystem.restore(originalState)
                }
            } finally {
                enabled = false
                originalState = null
            }
        }
    }

    private class ProcessConsoleAttachment(private val loggingRouter: LoggingRouter, private val consoleOutput: ConsoleOutput, private val consoleUnicodeSupport: ConsoleUnicodeSupport?) : Runnable {
        override fun run() {
            loggingRouter.attachProcessConsole(consoleOutput, consoleUnicodeSupport)
        }

        override fun equals(o: Any?): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }

            val that = o as ProcessConsoleAttachment

            return consoleOutput == that.consoleOutput
        }

        override fun hashCode(): Int {
            return consoleOutput.hashCode()
        }
    }

    private class ConsoleAttachment(
        private val loggingRouter: LoggingRouter,
        private val outputStream: OutputStream,
        private val errorStream: OutputStream,
        private val consoleOutput: ConsoleOutput?,
        private val consoleMetadata: ConsoleMetaData?
    ) : Runnable {
        override fun run() {
            loggingRouter.attachConsole(outputStream, errorStream, consoleOutput, consoleMetadata)
        }

        override fun equals(o: Any?): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }

            val that = o as ConsoleAttachment

            return outputStream === that.outputStream && errorStream === that.errorStream && consoleOutput == that.consoleOutput && consoleMetadata === that.consoleMetadata
        }

        override fun hashCode(): Int {
            return outputStream.hashCode() xor errorStream.hashCode()
        }
    }
}
