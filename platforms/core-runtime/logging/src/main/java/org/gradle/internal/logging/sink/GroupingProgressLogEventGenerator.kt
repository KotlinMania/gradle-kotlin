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

import com.google.common.base.Objects
import org.gradle.api.logging.LogLevel
import org.gradle.internal.logging.events.EndOutputEvent
import org.gradle.internal.logging.events.LogEvent
import org.gradle.internal.logging.events.OutputEvent
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.logging.events.ProgressCompleteEvent
import org.gradle.internal.logging.events.ProgressEvent
import org.gradle.internal.logging.events.ProgressStartEvent
import org.gradle.internal.logging.events.RenderableOutputEvent
import org.gradle.internal.logging.events.StyledTextOutputEvent
import org.gradle.internal.logging.events.UpdateNowEvent
import org.gradle.internal.logging.format.LogHeaderFormatter
import org.gradle.internal.operations.BuildOperationCategory
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.util.internal.GUtil
import java.util.concurrent.TimeUnit

/**
 * An `org.gradle.logging.internal.OutputEventListener` implementation which generates output events to log the
 * progress of operations.
 *
 *
 * This listener forwards nothing unless it receives periodic [UpdateNowEvent] clock events.
 */
class GroupingProgressLogEventGenerator(private val listener: OutputEventListener, private val headerFormatter: LogHeaderFormatter, private val verbose: Boolean) : OutputEventListener {
    // Maintain a hierarchy of all progress operations in progress — heads up: this is a *forest*, not just 1 tree
    private val operationsInProgress: MutableMap<OperationIdentifier?, OperationState> = LinkedHashMap<OperationIdentifier?, OperationState>()

    private var lastRenderedBuildOpId: Any? = null
    private var needHeaderSeparator = false
    private var currentTimePeriod: Long = 0

    override fun onOutput(event: OutputEvent) {
        if (event is ProgressStartEvent) {
            onStart(event)
        } else if (event is RenderableOutputEvent) {
            handleOutput(event)
        } else if (event is ProgressCompleteEvent) {
            onComplete(event)
        } else if (event is EndOutputEvent) {
            onEnd(event)
        } else if (event is UpdateNowEvent) {
            onUpdateNow(event)
        } else if (event !is ProgressEvent) {
            listener.onOutput(event)
        }
    }

    private fun onStart(startEvent: ProgressStartEvent) {
        val isGrouped = startEvent.buildOperationCategory.isGrouped()
        val progressId = startEvent.getProgressOperationId()
        if (startEvent.isBuildOperationStart && isGrouped) {
            // Create a new group for tasks or configure project
            operationsInProgress.put(
                progressId,
                GroupingProgressLogEventGenerator.OperationGroup(
                    startEvent.category,
                    startEvent.getDescription(),
                    startEvent.timestamp,
                    startEvent.parentProgressOperationId,
                    progressId,
                    startEvent.buildOperationCategory
                )
            )
        } else {
            operationsInProgress.put(progressId, OperationState(startEvent.parentProgressOperationId, progressId))
        }

        // Preserve logging of headers for progress operations started outside of the build operation executor as was done in Gradle 3.x
        // Basically, if we see an operation with a logging header and it's not grouped, just log it
        if (!isGrouped && GUtil.isTrue(startEvent.getLoggingHeader())) {
            onUngroupedOutput(LogEvent(startEvent.timestamp, startEvent.category, startEvent.getLogLevel(), startEvent.getLoggingHeader()!!, null, null))
        }
    }

    private fun handleOutput(event: RenderableOutputEvent) {
        val group = getGroupFor(event.buildOperationId)
        if (group != null) {
            group.bufferOutput(event)
        } else {
            onUngroupedOutput(event)
        }
    }

    private fun onComplete(completeEvent: ProgressCompleteEvent) {
        val state = operationsInProgress.remove(completeEvent.progressOperationId)
        if (state is OperationGroup) {
            val group = state
            group.setStatus(completeEvent.status, completeEvent.isFailed)
            group.flushOutput()
            if (group.hasForeground()) {
                lastRenderedBuildOpId = null
            }
        }
    }

    private fun onEnd(event: EndOutputEvent) {
        for (state in operationsInProgress.values) {
            state.flushOutput()
        }
        listener.onOutput(event)
        operationsInProgress.clear()
    }

    private fun onUpdateNow(event: UpdateNowEvent) {
        currentTimePeriod = event.timestamp
        for (state in operationsInProgress.values) {
            state.maybeFlushOutput(event.timestamp)
        }
    }

    private fun onUngroupedOutput(event: RenderableOutputEvent) {
        if (lastRenderedBuildOpId != null) {
            listener.onOutput(spacerLine(event.timestamp, event.category))
            lastRenderedBuildOpId = null
            needHeaderSeparator = true
        }
        listener.onOutput(event)
    }

    // Return the group to use for the given build operation, searching up the build operation hierarchy for the first group
    private fun getGroupFor(progressId: OperationIdentifier?): OperationGroup? {
        var current = progressId
        while (current != null) {
            val state = operationsInProgress.get(current)
            if (state == null) {
                // This shouldn't be the case, however, start and complete events are filtered in the prior stage when the logging level is > lifecycle
                // Should instead move the filtering after this stage
                break
            }
            if (state is OperationGroup) {
                return state
            }
            current = state.parentProgressOp
        }
        return null
    }

    private open class OperationState(val parentProgressOp: OperationIdentifier?, val buildOpIdentifier: OperationIdentifier) {
        open fun flushOutput() {
        }

        open fun maybeFlushOutput(timestamp: Long) {
        }
    }

    private inner class OperationGroup(
        private val category: String,
        private val description: String?,
        private var lastUpdateTime: Long,
        parentBuildOp: OperationIdentifier?,
        buildOpIdentifier: OperationIdentifier,
        private val buildOperationCategory: BuildOperationCategory
    ) : OperationState(parentBuildOp, buildOpIdentifier) {
        private var status = ""
        private var lastHeaderStatus: String? = ""
        private var failed = false
        private var headerSent = false
        private var outputRendered = false

        /**
         * Approximate size of the buffered logs.
         * This marks a best-case scenario, as codepoints varies in size depending on the character encoding.
         */
        private var approximateBufferCodepointSize = 0L
        private val bufferedLogs: MutableList<RenderableOutputEvent> = ArrayList<RenderableOutputEvent>()

        fun header(): StyledTextOutputEvent {
            return StyledTextOutputEvent(lastUpdateTime, category, LogLevel.LIFECYCLE, buildOpIdentifier, headerFormatter.format(description, status, failed))
        }

        fun bufferOutput(output: RenderableOutputEvent) {
            // Forward output immediately when the focus is on this operation group
            if (Objects.equal(buildOpIdentifier, lastRenderedBuildOpId)) {
                listener.onOutput(output)
                lastUpdateTime = currentTimePeriod
                needHeaderSeparator = true
            } else {
                // We add the output to the buffer
                // This won't consume significant memory as only the reference to `output` is stored
                bufferedLogs.add(output)

                // Update the approximate size of the buffered logs
                if (output is LogEvent) {
                    val logEvent = output
                    val logMessageCodepoints = logEvent.getMessage().length
                    approximateBufferCodepointSize += logMessageCodepoints.toLong()
                } else if (output is StyledTextOutputEvent) {
                    val styledTextOutputEvent = output
                    for (span in styledTextOutputEvent.getSpans()) {
                        approximateBufferCodepointSize += span.getText().length.toLong()
                    }
                }

                if (approximateBufferCodepointSize >= HIGH_WATERMARK_CODEPOINTS) {
                    flushOutput()
                }
            }
        }

        override fun flushOutput() {
            if (shouldForward()) {
                val hasContent = !bufferedLogs.isEmpty()
                if (!hasForeground() || statusHasChanged()) {
                    if (needHeaderSeparator || hasContent) {
                        listener.onOutput(spacerLine(lastUpdateTime, category))
                    }
                    listener.onOutput(header())
                    headerSent = true
                    lastHeaderStatus = status
                }

                for (renderableEvent in bufferedLogs) {
                    outputRendered = true
                    listener.onOutput(renderableEvent)
                }
                this@GroupingProgressLogEventGenerator.needHeaderSeparator = hasContent

                bufferedLogs.clear()
                approximateBufferCodepointSize = 0
                lastUpdateTime = currentTimePeriod
                lastRenderedBuildOpId = buildOpIdentifier
            }
        }

        override fun maybeFlushOutput(eventTimestamp: Long) {
            if (timeoutExpired(eventTimestamp, HIGH_WATERMARK_FLUSH_TIMEOUT) || (timeoutExpired(eventTimestamp, LOW_WATERMARK_FLUSH_TIMEOUT) && canClaimForeground())) {
                flushOutput()
            }
        }

        fun timeoutExpired(eventTimestamp: Long, timeout: Long): Boolean {
            return (eventTimestamp - lastUpdateTime) > timeout
        }

        fun canClaimForeground(): Boolean {
            return hasForeground() || (!bufferedLogs.isEmpty() && lastRenderedBuildOpId == null)
        }

        fun hasForeground(): Boolean {
            return buildOpIdentifier == lastRenderedBuildOpId
        }

        fun statusHasChanged(): Boolean {
            return status != lastHeaderStatus
        }

        fun setStatus(status: String, failed: Boolean) {
            this.status = status
            this.failed = failed
        }

        fun shouldPrintHeader(): Boolean {
            // Print the header if:
            //   we're in verbose mode OR we're in rich mode and some output has already been rendered
            //   AND
            //   we haven't displayed the header yet OR we've displayed the header but the status has since changed
            return (verbose || outputRendered) && (!headerSent || statusHasChanged())
        }

        fun statusIsFailed(): Boolean {
            return failed && statusHasChanged()
        }

        fun shouldForward(): Boolean {
            return !bufferedLogs.isEmpty() || (buildOperationCategory.isShowHeader() && (shouldPrintHeader() || statusIsFailed()))
        }
    }

    companion object {
        /**
         * Maximum amount of codepoints we allow to buffer before we flush the output.
         * The calculation targets 1MiB of buffer space with 2 byte per codepoint
         * (worst case scenario when each codepoint will be stored as UTF-16).
         */
        val HIGH_WATERMARK_CODEPOINTS: Long = 1000000L / 2L
        val HIGH_WATERMARK_FLUSH_TIMEOUT: Long = TimeUnit.SECONDS.toMillis(30)
        val LOW_WATERMARK_FLUSH_TIMEOUT: Long = TimeUnit.SECONDS.toMillis(2)

        private fun spacerLine(timestamp: Long, category: String): LogEvent {
            return LogEvent(timestamp, category, LogLevel.LIFECYCLE, "", null)
        }
    }
}
