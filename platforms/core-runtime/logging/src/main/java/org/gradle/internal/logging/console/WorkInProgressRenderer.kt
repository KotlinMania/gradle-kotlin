/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.internal.logging.console

import com.google.common.base.Function
import com.google.common.collect.Iterables
import com.google.common.collect.Sets
import org.gradle.internal.logging.events.EndOutputEvent
import org.gradle.internal.logging.events.OutputEvent
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.logging.events.ProgressCompleteEvent
import org.gradle.internal.logging.events.ProgressEvent
import org.gradle.internal.logging.events.ProgressStartEvent
import org.gradle.internal.logging.events.UpdateNowEvent
import org.gradle.internal.operations.OperationIdentifier
import java.util.ArrayDeque
import java.util.Deque

class WorkInProgressRenderer(
    private val listener: OutputEventListener,
    progressArea: BuildProgressArea,
    labelFormatter: DefaultWorkInProgressFormatter,
    consoleLayoutCalculator: ConsoleLayoutCalculator
) : OutputEventListener {
    private val operations = ProgressOperations()
    private val progressArea: BuildProgressArea?
    private val labelFormatter: DefaultWorkInProgressFormatter
    private val consoleLayoutCalculator: ConsoleLayoutCalculator

    private val queue: MutableList<OutputEvent?> = ArrayList<OutputEvent?>()

    // Track all unused labels to display future progress operation
    private val unusedProgressLabels: Deque<StyledLabel>

    // Track currently associated label with its progress operation
    private val operationIdToAssignedLabels: MutableMap<OperationIdentifier?, AssociationLabel> = HashMap<OperationIdentifier?, AssociationLabel>()

    // Track any progress operation that either can't be display due to label shortage or child progress operation is already been displayed
    private val unassignedProgressOperations: Deque<ProgressOperation?> = ArrayDeque<ProgressOperation?>()

    init {
        this.progressArea = progressArea
        this.labelFormatter = labelFormatter
        this.consoleLayoutCalculator = consoleLayoutCalculator
        this.unusedProgressLabels = ArrayDeque<StyledLabel>(progressArea.buildProgressLabels)
    }

    override fun onOutput(event: OutputEvent) {
        queue.add(event)

        if (event is UpdateNowEvent) {
            renderNow()
        } else if (event is EndOutputEvent) {
            progressArea!!.setVisible(false)
        }

        listener.onOutput(event)
    }

    // Transform ProgressCompleteEvent into their corresponding progress OperationIdentifier.
    private fun toOperationIdSet(events: Iterable<ProgressCompleteEvent?>): MutableSet<OperationIdentifier?> {
        return Sets.newHashSet<OperationIdentifier?>(
            Iterables.transform<ProgressCompleteEvent?, OperationIdentifier?>(
                events,
                Function { obj: ProgressCompleteEvent? -> obj!!.progressOperationId })
        )
    }

    private fun resizeTo(newBuildProgressLabelCount: Int) {
        var newBuildProgressLabelCount = newBuildProgressLabelCount
        val previousBuildProgressLabelCount: Int = progressArea!!.buildProgressLabels.size()
        newBuildProgressLabelCount = consoleLayoutCalculator.calculateNumWorkersForConsoleDisplay(newBuildProgressLabelCount)
        if (previousBuildProgressLabelCount >= newBuildProgressLabelCount) {
            // We don't support shrinking at the moment
            return
        }

        progressArea.resizeBuildProgressTo(newBuildProgressLabelCount)

        // Add new labels to the unused queue
        for (i in newBuildProgressLabelCount - 1 downTo previousBuildProgressLabelCount) {
            unusedProgressLabels.push(progressArea.buildProgressLabels!!.get(i))
        }
    }

    private fun attach(operation: ProgressOperation) {
        if (operation.hasChildren() || !isRenderable(operation)) {
            return
        }

        // Don't show the parent operation while a child is visible
        // Instead, reuse the parent label, if any, for the child
        if (operation.parent != null) {
            unshow(operation.parent)
        }

        // No more unused label? Try to resize.
        if (unusedProgressLabels.isEmpty()) {
            val newValue = operationIdToAssignedLabels.size + 1
            resizeTo(newValue)
            // At this point, the work-in-progress area may or may not have been resized due to maximum size constraint.
        }

        // Try to use a new label
        if (unusedProgressLabels.isEmpty()) {
            unassignedProgressOperations.add(operation)
            reportLinesNotShown()
        } else {
            attach(operation, unusedProgressLabels.pop())
        }
    }

    private fun attach(operation: ProgressOperation, label: StyledLabel) {
        val association: AssociationLabel = WorkInProgressRenderer.AssociationLabel(operation, label)
        operationIdToAssignedLabels.put(operation.operationId, association)
    }

    // Declares that we're not following updates from this ProgressOperation anymore
    private fun detach(operation: ProgressOperation) {
        if (!isRenderable(operation)) {
            return
        }

        unshow(operation)

        if (operation.parent != null && isRenderable(operation.parent)) {
            attach(operation.parent)
        } else if (!unassignedProgressOperations.isEmpty()) {
            attach(unassignedProgressOperations.pop()!!)
            reportLinesNotShown()
        }
    }

    // Declares that we are stopping showing updates from this ProgressOperation.
    // We might be completely done following this ProgressOperation, or
    // we might simply be waiting for its children to complete.
    private fun unshow(operation: ProgressOperation) {
        val operationId = operation.operationId
        val association = operationIdToAssignedLabels.remove(operationId)
        if (association != null) {
            unusedProgressLabels.push(association.label)
        }
        unassignedProgressOperations.remove(operation)
        reportLinesNotShown()
    }

    private fun reportLinesNotShown() {
        val linesNotShown = unassignedProgressOperations.size

        val text: String?
        if (linesNotShown == 0) {
            text = ""
        } else if (linesNotShown == 1) {
            text = "  (1 line not showing)"
        } else {
            text = "  (" + linesNotShown + " lines not showing)"
        }

        progressArea!!.cursorParkLine!!.setText(text)
    }

    // Any ProgressOperation in the parent chain has a message, the operation is considered renderable.
    private fun isRenderable(operation: ProgressOperation?): Boolean {
        var current = operation
        while (current != null) {
            if (current.message != null) {
                return true
            }
            current = current.parent
        }

        return false
    }

    private fun renderNow() {
        if (queue.isEmpty()) {
            return
        }

        // Skip processing of any operations that both start and complete in the queue
        val completeEventOperationIds = toOperationIdSet(Iterables.filter<ProgressCompleteEvent?>(queue, ProgressCompleteEvent::class.java))
        val operationIdsToSkip: MutableSet<OperationIdentifier?> = HashSet<OperationIdentifier?>()

        for (event in queue) {
            if (event is ProgressStartEvent) {
                progressArea!!.setVisible(true)
                val startEvent = event
                if (completeEventOperationIds.contains(startEvent.getProgressOperationId())) {
                    operationIdsToSkip.add(startEvent.getProgressOperationId())
                    // Don't attach to any labels
                } else {
                    attach(operations.start(startEvent.status, startEvent.category, startEvent.getProgressOperationId(), startEvent.parentProgressOperationId))
                }
            } else if (event is ProgressCompleteEvent) {
                val completeEvent = event
                if (!operationIdsToSkip.contains(completeEvent.progressOperationId)) {
                    detach(operations.complete(completeEvent.progressOperationId))
                }
            } else if (event is ProgressEvent) {
                val progressEvent = event
                if (!operationIdsToSkip.contains(progressEvent.progressOperationId)) {
                    operations.progress(progressEvent.status, progressEvent.progressOperationId)
                }
            }
        }
        queue.clear()

        for (associatedLabel in operationIdToAssignedLabels.values) {
            associatedLabel.renderNow()
        }
        for (emptyLabel in unusedProgressLabels) {
            emptyLabel.setText(labelFormatter.format())
        }
    }

    private inner class AssociationLabel(val operation: ProgressOperation, val label: StyledLabel) {
        fun renderNow() {
            label.setText(labelFormatter.format(operation))
        }
    }
}
