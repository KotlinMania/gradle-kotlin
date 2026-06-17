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
package org.gradle.internal.logging.sink

import org.gradle.api.internal.file.temp.TemporaryFileProvider
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.StandardOutputListener
import org.gradle.api.logging.configuration.ConsoleOutput
import org.gradle.api.logging.configuration.ConsoleUnicodeSupport
import org.gradle.internal.Factory
import org.gradle.internal.event.ListenerBroadcast
import org.gradle.internal.logging.config.LoggingRouter
import org.gradle.internal.logging.config.LoggingSystem
import org.gradle.internal.logging.console.BuildLogLevelFilterRenderer
import org.gradle.internal.logging.console.BuildStatusRenderer
import org.gradle.internal.logging.console.ColorMap
import org.gradle.internal.logging.console.Console
import org.gradle.internal.logging.console.ConsoleFlushRenderer
import org.gradle.internal.logging.console.ConsoleLayoutCalculator
import org.gradle.internal.logging.console.DefaultColorMap
import org.gradle.internal.logging.console.DefaultWorkInProgressFormatter
import org.gradle.internal.logging.console.FlushConsoleListener
import org.gradle.internal.logging.console.GlobalUserInputReceiver
import org.gradle.internal.logging.console.StyledTextOutputBackedRenderer
import org.gradle.internal.logging.console.ThrottlingOutputEventListener
import org.gradle.internal.logging.console.UserInputConsoleRenderer
import org.gradle.internal.logging.console.UserInputStandardOutputRenderer
import org.gradle.internal.logging.console.WorkInProgressRenderer
import org.gradle.internal.logging.events.EndOutputEvent
import org.gradle.internal.logging.events.FlushOutputEvent
import org.gradle.internal.logging.events.LogLevelChangeEvent
import org.gradle.internal.logging.events.OutputEvent
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.logging.events.ProgressCompleteEvent
import org.gradle.internal.logging.events.ProgressEvent
import org.gradle.internal.logging.events.ProgressStartEvent
import org.gradle.internal.logging.events.RenderableOutputEvent
import org.gradle.internal.logging.format.PrettyPrefixedLogHeaderFormatter
import org.gradle.internal.logging.text.StreamBackedStandardOutputListener
import org.gradle.internal.logging.text.StreamingStyledTextOutput
import org.gradle.internal.nativeintegration.console.ConsoleMetaData
import org.gradle.internal.nativeintegration.console.FallbackConsoleMetaData
import org.gradle.internal.time.Clock
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicReference
import javax.annotation.concurrent.ThreadSafe

/**
 * A [OutputEventListener] implementation which renders output events to various
 * destinations. This implementation is thread-safe.
 */
@ThreadSafe
class OutputEventRenderer(private val clock: Clock, private val userInput: GlobalUserInputReceiver, private val temporaryFileProvider: TemporaryFileProvider) : OutputEventListener, LoggingRouter {
    private val lock = Any()
    private val logLevel = AtomicReference<LogLevel?>(LogLevel.LIFECYCLE)
    private val formatters = ListenerBroadcast<OutputEventListener?>(OutputEventListener::class.java)
    private val transformer = OutputEventTransformer(formatters.getSource(), lock)

    var colourMap: ColorMap? = null
        get() {
            synchronized(lock) {
                if (field == null) {
                    field = DefaultColorMap(isNoColorRequested)
                }
            }
            return field
        }
        private set
    var originalStdOut: OutputStream? = null
        private set
    var originalStdErr: OutputStream? = null
        private set
    private var stdOutListener: OutputEventListener? = null
    private var stdErrListener: OutputEventListener? = null
    private var console: OutputEventListener? = null
    private var userListenerChain: OutputEventListener? = null
    private var userStdoutListeners: ListenerBroadcast<StandardOutputListener?>? = null
    private var userStderrListeners: ListenerBroadcast<StandardOutputListener?>? = null

    override fun snapshot(): LoggingSystem.Snapshot? {
        synchronized(lock) {
            // Currently only snapshot the console output listener. Should snapshot all output listeners, and cleanup in restore()
            return OutputEventRenderer.SnapshotImpl(logLevel.get()!!, console)
        }
    }

    override fun restore(state: LoggingSystem.Snapshot?) {
        synchronized(lock) {
            val snapshot = state as SnapshotImpl
            if (snapshot.logLevel != logLevel.get()) {
                configure(snapshot.logLevel)
            }

            // TODO - also close console when it is replaced
            if (snapshot.console !== console) {
                if (snapshot.console == null) {
                    removeChain(console!!)
                    console = null
                } else {
                    throw UnsupportedOperationException("Cannot restore previous console. This is not implemented yet.")
                }
            }
        }
    }

    private fun addChain(listener: OutputEventListener) {
        listener.onOutput(LogLevelChangeEvent(logLevel.get()!!))
        formatters.add(listener)
    }

    private fun removeChain(listener: OutputEventListener) {
        formatters.remove(listener)
        listener.onOutput(EndOutputEvent())
    }

    override fun flush() {
        onOutput(FlushOutputEvent())
    }

    override fun attachProcessConsole(consoleOutput: ConsoleOutput?, consoleUnicodeSupport: ConsoleUnicodeSupport?) {
        synchronized(lock) {
            ConsoleConfigureAction.execute(this, consoleOutput, consoleUnicodeSupport)
        }
    }

    override fun attachConsole(outputStream: OutputStream?, errorStream: OutputStream?, consoleOutput: ConsoleOutput?) {
        attachConsole(outputStream, errorStream, consoleOutput, null)
    }

    override fun attachConsole(outputStream: OutputStream?, errorStream: OutputStream?, consoleOutput: ConsoleOutput?, consoleMetadata: ConsoleMetaData?) {
        var consoleMetadata = consoleMetadata
        synchronized(lock) {
            if (consoleMetadata == null) {
                consoleMetadata = FallbackConsoleMetaData.NOT_ATTACHED
            }
            ConsoleConfigureAction.execute(this, consoleOutput, consoleMetadata, outputStream, errorStream)
        }
    }

    override fun attachSystemOutAndErr() {
        addSystemOutAsLoggingDestination()
        addSystemErrAsLoggingDestination()
    }

    private fun addSystemOutAsLoggingDestination() {
        synchronized(lock) {
            originalStdOut = System.out
            if (stdOutListener != null) {
                removeChain(stdOutListener!!)
            }
            stdOutListener = LazyListener(object : Factory<OutputEventListener?> {
                override fun create(): OutputEventListener {
                    return onNonError(StyledTextOutputBackedRenderer(StreamingStyledTextOutput(StreamBackedStandardOutputListener(originalStdOut as Appendable?))))
                }
            })
            addChain(stdOutListener!!)
        }
    }

    private fun addSystemErrAsLoggingDestination() {
        synchronized(lock) {
            originalStdErr = System.err
            if (stdErrListener != null) {
                removeChain(stdErrListener!!)
            }
            stdErrListener = LazyListener(object : Factory<OutputEventListener?> {
                override fun create(): OutputEventListener {
                    return onError(StyledTextOutputBackedRenderer(StreamingStyledTextOutput(StreamBackedStandardOutputListener(originalStdErr as Appendable?))))
                }
            })
            addChain(stdErrListener!!)
        }
    }

    private fun removeSystemOutAsLoggingDestination() {
        synchronized(lock) {
            if (stdOutListener != null) {
                removeChain(stdOutListener!!)
                stdOutListener = null
            }
        }
    }

    private fun removeSystemErrAsLoggingDestination() {
        synchronized(lock) {
            if (stdErrListener != null) {
                removeChain(stdErrListener!!)
                stdErrListener = null
            }
        }
    }

    override fun addOutputEventListener(listener: OutputEventListener) {
        synchronized(lock) {
            addChain(listener)
        }
    }

    override fun removeOutputEventListener(listener: OutputEventListener) {
        synchronized(lock) {
            removeChain(listener)
        }
    }

    fun addRichConsoleWithErrorOutputOnStdout(stdout: Console, consoleMetaData: ConsoleMetaData, verbose: Boolean) {
        val consoleListener: OutputEventListener = StyledTextOutputBackedRenderer(stdout.buildOutputArea!!)
        val consoleChain = throttled(
            getUserInputConsoleRenderer(stdout, consoleMetaData, verbose, consoleListener)
        )
        addConsoleChain(consoleChain)
    }

    fun addRichConsole(stdout: Console, stderr: Console, consoleMetaData: ConsoleMetaData, verbose: Boolean) {
        val stdoutChain: OutputEventListener = StyledTextOutputBackedRenderer(stdout.buildOutputArea!!)
        val stderrChain: OutputEventListener = FlushConsoleListener(stderr, StyledTextOutputBackedRenderer(stderr.buildOutputArea!!))
        val consoleListener: OutputEventListener = ErrorOutputDispatchingListener(stderrChain, stdoutChain)
        val consoleChain = throttled(
            getUserInputConsoleRenderer(stdout, consoleMetaData, verbose, consoleListener)
        )
        addConsoleChain(consoleChain)
    }

    fun addRichConsole(stdout: Console, stderr: OutputStream?, consoleMetaData: ConsoleMetaData, verbose: Boolean) {
        val stdoutChain: OutputEventListener = StyledTextOutputBackedRenderer(stdout.buildOutputArea!!)
        val stderrChain: OutputEventListener = StyledTextOutputBackedRenderer(StreamingStyledTextOutput(StreamBackedStandardOutputListener(stderr)))
        val consoleListener: OutputEventListener = ErrorOutputDispatchingListener(stderrChain, stdoutChain)
        val consoleChain = throttled(
            getUserInputConsoleRenderer(stdout, consoleMetaData, verbose, consoleListener)
        )
        addConsoleChain(consoleChain)
    }

    fun addRichConsole(stdout: OutputStream?, stderr: Console, verbose: Boolean) {
        val stdoutChain: OutputEventListener = StyledTextOutputBackedRenderer(StreamingStyledTextOutput(StreamBackedStandardOutputListener(stdout)))
        val stderrChain: OutputEventListener = FlushConsoleListener(stderr, StyledTextOutputBackedRenderer(stderr.buildOutputArea!!))
        val consoleListener: OutputEventListener = ErrorOutputDispatchingListener(stderrChain, stdoutChain)
        val consoleChain = throttled(
            getInputStandardOutputRenderer(consoleListener, verbose)
        )
        addConsoleChain(consoleChain)
    }

    fun addPlainConsoleWithErrorOutputOnStdout(stdout: OutputStream?) {
        val stdoutListener: OutputEventListener = StyledTextOutputBackedRenderer(StreamingStyledTextOutput(StreamBackedStandardOutputListener(stdout)))
        addConsoleChain(
            throttled(
                getInputStandardOutputRenderer(stdoutListener, true)
            )
        )
    }

    fun addPlainConsole(stdout: OutputStream?, stderr: OutputStream?) {
        val stdoutChain: OutputEventListener = StyledTextOutputBackedRenderer(StreamingStyledTextOutput(StreamBackedStandardOutputListener(stdout)))
        val stderrChain: OutputEventListener = StyledTextOutputBackedRenderer(StreamingStyledTextOutput(StreamBackedStandardOutputListener(stderr)))
        val outputListener: OutputEventListener = ErrorOutputDispatchingListener(stderrChain, stdoutChain)
        addConsoleChain(
            throttled(
                getInputStandardOutputRenderer(outputListener, true)
            )
        )
    }

    fun addColoredConsoleWithErrorOutputOnStdout(stdout: Console) {
        val consoleListener: OutputEventListener = FlushConsoleListener(stdout, StyledTextOutputBackedRenderer(stdout.buildOutputArea!!))
        val consoleChain = throttled(
            flushing(
                getInputStandardOutputRenderer(consoleListener, true),
                stdout
            )

        )
        addConsoleChain(consoleChain)
    }

    fun addColoredConsole(stdout: Console, stderr: Console) {
        val stdoutChain: OutputEventListener = StyledTextOutputBackedRenderer(stdout.buildOutputArea!!)
        val stderrChain: OutputEventListener = FlushConsoleListener(stderr, StyledTextOutputBackedRenderer(stderr.buildOutputArea!!))
        val consoleListener: OutputEventListener = ErrorOutputDispatchingListener(stderrChain, stdoutChain)
        val consoleChain = throttled(
            flushing(
                getInputStandardOutputRenderer(consoleListener, true),
                stdout
            )
        )
        addConsoleChain(consoleChain)
    }

    private fun getUserInputConsoleRenderer(console: Console, consoleMetaData: ConsoleMetaData, verbose: Boolean, consoleListener: OutputEventListener?): UserInputConsoleRenderer {
        return UserInputConsoleRenderer(
            BuildStatusRenderer(
                WorkInProgressRenderer(
                    BuildLogLevelFilterRenderer(
                        GroupingProgressLogEventGenerator(consoleListener, PrettyPrefixedLogHeaderFormatter(), verbose)
                    ),
                    console.buildProgressArea!!,
                    DefaultWorkInProgressFormatter(consoleMetaData),
                    ConsoleLayoutCalculator(consoleMetaData)
                ),
                console.statusBar!!, console, consoleMetaData
            ),
            console,
            userInput,
            temporaryFileProvider
        )
    }

    private fun getInputStandardOutputRenderer(outputListener: OutputEventListener?, verbose: Boolean): UserInputStandardOutputRenderer {
        return UserInputStandardOutputRenderer(
            BuildLogLevelFilterRenderer(
                GroupingProgressLogEventGenerator(
                    outputListener,
                    PrettyPrefixedLogHeaderFormatter(),
                    verbose
                )
            ),
            userInput,
            temporaryFileProvider
        )
    }

    private fun throttled(listener: OutputEventListener): OutputEventListener {
        return ThrottlingOutputEventListener(listener, clock)
    }

    private fun flushing(listener: OutputEventListener, console: Console): OutputEventListener {
        return ConsoleFlushRenderer(listener, console)
    }

    private fun addConsoleChain(consoleChain: OutputEventListener?): OutputEventRenderer {
        synchronized(lock) {
            this.console = consoleChain
            removeSystemOutAsLoggingDestination()
            removeSystemErrAsLoggingDestination()
            addChain(this.console!!)
        }
        return this
    }

    private fun onError(listener: OutputEventListener?): OutputEventListener {
        return LogEventDispatcher(null, listener)
    }

    private fun onNonError(listener: OutputEventListener?): OutputEventListener {
        return LogEventDispatcher(listener, null)
    }

    override fun enableUserStandardOutputListeners() {
        // Create all of the pipeline eagerly as soon as this is enabled, to track the state of build operations.
        // All of the pipelines do this, so should instead have a single stage that tracks this for all pipelines and that can replay the current state to new pipelines
        // Then, a pipeline can be added for each listener as required
        synchronized(lock) {
            if (userStdoutListeners == null) {
                userStdoutListeners = ListenerBroadcast<StandardOutputListener?>(StandardOutputListener::class.java)
                userStderrListeners = ListenerBroadcast<StandardOutputListener?>(StandardOutputListener::class.java)
                val stdOutChain: OutputEventListener = StyledTextOutputBackedRenderer(StreamingStyledTextOutput(userStdoutListeners!!.getSource()))
                val stdErrChain: OutputEventListener = StyledTextOutputBackedRenderer(StreamingStyledTextOutput(userStderrListeners!!.getSource()))
                userListenerChain = BuildLogLevelFilterRenderer(
                    ProgressLogEventGenerator(object : OutputEventListener {
                        override fun onOutput(event: OutputEvent) {
                            // Do not forward events for rendering when there are no listeners to receive
                            if (event is LogLevelChangeEvent) {
                                stdOutChain.onOutput(event)
                                stdErrChain.onOutput(event)
                            } else if (event.logLevel === LogLevel.ERROR && !userStderrListeners!!.isEmpty() && event is RenderableOutputEvent) {
                                stdErrChain.onOutput(event)
                            } else if (event.logLevel !== LogLevel.ERROR && !userStdoutListeners!!.isEmpty() && event is RenderableOutputEvent) {
                                stdOutChain.onOutput(event)
                            }
                        }
                    })
                )
                addChain(userListenerChain!!)
            }
        }
    }

    private fun assertUserListenersEnabled() {
        checkNotNull(userListenerChain) { "Custom standard output listeners not enabled." }
        userListenerChain!!.onOutput(FlushOutputEvent())
    }

    override fun addStandardErrorListener(listener: StandardOutputListener) {
        synchronized(lock) {
            assertUserListenersEnabled()
            userStderrListeners!!.add(listener)
        }
    }

    override fun addStandardOutputListener(listener: StandardOutputListener) {
        synchronized(lock) {
            assertUserListenersEnabled()
            userStdoutListeners!!.add(listener)
        }
    }

    override fun addStandardOutputListener(outputStream: OutputStream?) {
        addStandardOutputListener(StreamBackedStandardOutputListener(outputStream))
    }

    override fun addStandardErrorListener(outputStream: OutputStream?) {
        addStandardErrorListener(StreamBackedStandardOutputListener(outputStream))
    }

    override fun removeStandardOutputListener(listener: StandardOutputListener) {
        synchronized(lock) {
            assertUserListenersEnabled()
            userStdoutListeners!!.remove(listener)
        }
    }

    override fun removeStandardErrorListener(listener: StandardOutputListener) {
        synchronized(lock) {
            assertUserListenersEnabled()
            userStderrListeners!!.remove(listener)
        }
    }

    override fun configure(logLevel: LogLevel) {
        onOutput(LogLevelChangeEvent(logLevel))
    }

    override fun onOutput(event: OutputEvent) {
        if (event.logLevel != null && event.logLevel.compareTo(logLevel.get()) < 0 && !isProgressEvent(event)) {
            return
        }
        if (event is LogLevelChangeEvent) {
            val changeEvent = event
            val newLogLevel = changeEvent.newLogLevel
            if (newLogLevel == this.logLevel.get()) {
                return
            }
            this.logLevel.set(newLogLevel)
        }
        transformer.onOutput(event)
    }

    private fun isProgressEvent(event: OutputEvent?): Boolean {
        return event is ProgressStartEvent || event is ProgressEvent || event is ProgressCompleteEvent
    }

    private class SnapshotImpl(private val logLevel: LogLevel, private val console: OutputEventListener?) : LoggingSystem.Snapshot

    private class LazyListener(private var factory: Factory<OutputEventListener?>?) : OutputEventListener {
        private var delegate: OutputEventListener? = null
        private var pendingLogLevel: LogLevelChangeEvent? = null

        override fun onOutput(event: OutputEvent) {
            if (delegate == null) {
                if (event is EndOutputEvent || event is FlushOutputEvent) {
                    // Ignore
                    return
                }
                if (event is LogLevelChangeEvent) {
                    // Keep until the listener is created
                    pendingLogLevel = event
                    return
                }
                delegate = factory!!.create()
                factory = null
                if (pendingLogLevel != null) {
                    delegate!!.onOutput(pendingLogLevel!!)
                    pendingLogLevel = null
                }
            }
            delegate!!.onOutput(event)
        }
    }

    companion object {
        private val isNoColorRequested: Boolean
            /**
             * Returns true when the NO_COLOR environment variable is present and non-empty,
             * following the [no-color.org](https://no-color.org/) convention.
             */
            get() {
                val noColor = System.getenv("NO_COLOR")
                return noColor != null && !noColor.isEmpty()
            }
    }
}
