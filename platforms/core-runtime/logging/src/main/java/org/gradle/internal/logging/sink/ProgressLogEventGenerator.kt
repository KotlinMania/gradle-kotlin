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

import org.gradle.api.logging.LogLevel
import org.gradle.internal.SystemProperties
import org.gradle.internal.logging.events.OutputEvent
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.logging.events.ProgressCompleteEvent
import org.gradle.internal.logging.events.ProgressEvent
import org.gradle.internal.logging.events.ProgressStartEvent
import org.gradle.internal.logging.events.RenderableOutputEvent
import org.gradle.internal.logging.events.StyledTextOutputEvent
import org.gradle.internal.logging.text.StyledTextOutput
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.util.internal.GUtil
import java.util.Arrays

/**
 * An `org.gradle.logging.internal.OutputEventListener` implementation which generates output events to log the
 * progress of operations.
 */
class ProgressLogEventGenerator(private val listener: OutputEventListener) : OutputEventListener {
    private val operations: MutableMap<OperationIdentifier?, Operation> = LinkedHashMap<OperationIdentifier?, Operation>()

    override fun onOutput(event: OutputEvent) {
        if (event is ProgressStartEvent) {
            onStart(event)
        } else if (event is ProgressCompleteEvent) {
            onComplete(event)
        } else if (event is RenderableOutputEvent) {
            doOutput(event)
        } else if (event !is ProgressEvent) {
            listener.onOutput(event)
        }
    }

    private fun doOutput(event: RenderableOutputEvent) {
        for (operation in operations.values) {
            operation.completeHeader()
        }
        listener.onOutput(event)
    }

    private fun onComplete(progressCompleteEvent: ProgressCompleteEvent) {
        if (operations.isEmpty()) {
            return
        }
        val operation = operations.remove(progressCompleteEvent.progressOperationId)
        if (operation == null) {
            return
        }
        completeOperation(progressCompleteEvent, operation)
    }

    private fun completeOperation(progressCompleteEvent: ProgressCompleteEvent, operation: Operation) {
        operation.status = progressCompleteEvent.status
        operation.completeTime = progressCompleteEvent.timestamp
        operation.complete()
    }

    private fun onStart(progressStartEvent: ProgressStartEvent) {
        val operation: Operation =
            ProgressLogEventGenerator.Operation(progressStartEvent.category, progressStartEvent.getLoggingHeader(), progressStartEvent.timestamp, progressStartEvent.buildOperationId)
        operations.put(progressStartEvent.getProgressOperationId(), operation)
    }

    internal enum class State {
        None, HeaderStarted, HeaderCompleted, Completed
    }

    private inner class Operation(private val category: String, private val loggingHeader: String?, private val startTime: Long, private val buildOperationIdentifier: OperationIdentifier?) {
        private val hasLoggingHeader: Boolean
        private var status = ""
        private var state = State.None
        private var completeTime: Long = 0

        init {
            this.hasLoggingHeader = GUtil.isTrue(loggingHeader)
        }

        fun plainTextEvent(timestamp: Long, text: String): StyledTextOutputEvent {
            return StyledTextOutputEvent(timestamp, category, LogLevel.LIFECYCLE, buildOperationIdentifier, mutableListOf<StyledTextOutputEvent.Span>(StyledTextOutputEvent.Span(text)))
        }

        fun styledTextEvent(timestamp: Long, vararg spans: StyledTextOutputEvent.Span?): StyledTextOutputEvent {
            return StyledTextOutputEvent(timestamp, category, LogLevel.LIFECYCLE, buildOperationIdentifier, Arrays.asList<StyledTextOutputEvent.Span>(*spans))
        }

        fun doOutput(event: RenderableOutputEvent) {
            for (pending in operations.values) {
                if (pending === this) {
                    break
                }
                pending.completeHeader()
            }
            listener.onOutput(event)
        }

        fun completeHeader() {
            when (state) {
                State.None -> if (hasLoggingHeader) {
                    listener.onOutput(plainTextEvent(startTime, loggingHeader + EOL))
                }

                State.HeaderStarted -> listener.onOutput(plainTextEvent(startTime, EOL))
                State.HeaderCompleted -> return
                else -> throw IllegalStateException("state is " + state)
            }
            state = State.HeaderCompleted
        }

        fun complete() {
            val hasStatus = GUtil.isTrue(status)
            when (state) {
                State.None -> if (hasLoggingHeader && hasStatus) {
                    doOutput(
                        styledTextEvent(
                            completeTime,
                            StyledTextOutputEvent.Span(loggingHeader + ' '),
                            StyledTextOutputEvent.Span(StyledTextOutput.Style.ProgressStatus, status),
                            StyledTextOutputEvent.Span(EOL)
                        )
                    )
                } else if (hasLoggingHeader) {
                    doOutput(plainTextEvent(completeTime, loggingHeader + EOL))
                }

                State.HeaderStarted -> {
                    assert(hasLoggingHeader)
                    if (hasStatus) {
                        doOutput(
                            styledTextEvent(
                                completeTime,
                                StyledTextOutputEvent.Span(" "),
                                StyledTextOutputEvent.Span(StyledTextOutput.Style.ProgressStatus, status),
                                StyledTextOutputEvent.Span(EOL)
                            )
                        )
                    } else {
                        doOutput(plainTextEvent(completeTime, EOL))
                    }
                }

                State.HeaderCompleted -> if (hasLoggingHeader && hasStatus) {
                    doOutput(
                        styledTextEvent(
                            completeTime,
                            StyledTextOutputEvent.Span(loggingHeader + ' '),
                            StyledTextOutputEvent.Span(StyledTextOutput.Style.ProgressStatus, status),
                            StyledTextOutputEvent.Span(EOL)
                        )
                    )
                }

                else -> throw IllegalStateException("state is " + state)
            }
            state = State.Completed
        }
    }

    companion object {
        private val EOL = SystemProperties.getInstance().getLineSeparator()
    }
}
