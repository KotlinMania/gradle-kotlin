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
package org.gradle.internal.logging.source

import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.StandardOutputListener
import org.gradle.internal.io.LinePerThreadBufferingOutputStream
import org.gradle.internal.io.TextStream
import org.gradle.internal.logging.config.LoggingSourceSystem
import org.gradle.internal.logging.config.LoggingSystem
import org.gradle.internal.logging.events.LogLevelChangeEvent
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.logging.events.StyledTextOutputEvent
import org.gradle.internal.operations.CurrentBuildOperationRef
import org.gradle.internal.time.Clock
import java.io.PrintStream
import java.util.concurrent.atomic.AtomicReference

/**
 * A [LoggingSourceSystem] which routes content written to a `PrintStream` to a [OutputEventListener].
 * Generates a [StyledTextOutputEvent] instance when a line of text is written to the `PrintStream`.
 * Generates a [LogLevelChangeEvent] when the log level for this `LoggingSystem` is changed.
 */
abstract class PrintStreamLoggingSystem protected constructor(private val outputEventListener: OutputEventListener, category: String, clock: Clock) : LoggingSourceSystem {
    private val destination = AtomicReference<StandardOutputListener?>()
    private val outstr: PrintStream = LinePerThreadBufferingOutputStream(object : TextStream {
        override fun text(output: String) {
            destination.get()!!.onOutput(output)
        }

        override fun endOfStream(failure: Throwable?) {
        }
    })
    private var original: PrintStreamDestination? = null
    private var enabled = false
    private var logLevel: LogLevel? = null
    private val listener: StandardOutputListener

    init {
        this.listener = OutputEventDestination(outputEventListener, category, clock)
    }

    /**
     * Returns the current value of the PrintStream
     */
    protected abstract fun get(): PrintStream

    /**
     * Sets the current value of the PrintStream
     */
    protected abstract fun set(printStream: PrintStream)

    override fun snapshot(): LoggingSystem.Snapshot? {
        return SnapshotImpl(enabled, logLevel ?: LogLevel.LIFECYCLE)
    }

    override fun restore(state: LoggingSystem.Snapshot?) {
        val snapshot = state as SnapshotImpl
        enabled = snapshot.enabled
        logLevel = snapshot.logLevel
        if (enabled) {
            install()
        } else {
            uninstall()
        }
    }

    override fun setLevel(logLevel: LogLevel?): LoggingSystem.Snapshot? {
        val snapshot = snapshot()
        if (logLevel != this.logLevel) {
            this.logLevel = logLevel
            if (enabled) {
                outstr.flush()
                if (logLevel != null) {
                    outputEventListener.onOutput(LogLevelChangeEvent(logLevel))
                }
            }
        }
        return snapshot
    }

    override fun startCapture(): LoggingSystem.Snapshot? {
        val snapshot = snapshot()
        if (!enabled) {
            install()
        }
        return snapshot
    }

    private fun uninstall() {
        val original = original
        if (original != null) {
            outstr.flush()
            destination.set(original)
            set(original.originalStream)
            this.original = null
        }
    }

    private fun install() {
        if (original == null) {
            val originalStream = get()
            original = PrintStreamDestination(originalStream)
        }
        enabled = true
        outstr.flush()
        val currentLogLevel = logLevel
        if (currentLogLevel != null) {
            outputEventListener.onOutput(LogLevelChangeEvent(currentLogLevel))
        }
        destination.set(listener)
        if (get() !== outstr) {
            set(outstr)
        }
    }

    private class PrintStreamDestination(val originalStream: PrintStream) : StandardOutputListener {
        override fun onOutput(output: CharSequence?) {
            originalStream.print(output)
        }
    }

    private class SnapshotImpl(val enabled: Boolean, val logLevel: LogLevel) : LoggingSystem.Snapshot

    private inner class OutputEventDestination(private val listener: OutputEventListener, private val category: String, private val clock: Clock) : StandardOutputListener {
        override fun onOutput(output: CharSequence?) {
            val buildOperationId = CurrentBuildOperationRef.instance().getId()
            val event: StyledTextOutputEvent = StyledTextOutputEvent(clock.currentTime, category, logLevel ?: LogLevel.LIFECYCLE, buildOperationId, output.toString())
            listener.onOutput(event)
        }
    }
}
