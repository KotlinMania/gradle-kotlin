/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.internal.logging.events.OutputEvent
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.logging.events.ProgressCompleteEvent
import org.gradle.internal.logging.events.ProgressEvent
import org.gradle.internal.logging.events.ProgressStartEvent
import org.gradle.internal.logging.events.RenderableOutputEvent
import org.gradle.internal.operations.BuildOperationCategory
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.util.internal.GUtil
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Transforms the stream of output events to discard progress operations that are not interesting to the logging subsystem. This reduces the amount of work that downstream consumers have to do to process the stream. For example, these discarded events don't need to be written to the daemon client.
 */
class OutputEventTransformer(private val listener: OutputEventListener, private val lock: Any) : OutputEventListener {
    // A map from progress operation id seen in event -> progress operation id that should be forwarded
    private val effectiveProgressOperation: MutableMap<OperationIdentifier?, OperationIdentifier?> = ConcurrentHashMap<OperationIdentifier?, OperationIdentifier?>()

    // A set of progress operations that have been forwarded
    private val forwarded: MutableSet<OperationIdentifier?> = Collections.newSetFromMap<OperationIdentifier?>(ConcurrentHashMap<OperationIdentifier?, Boolean?>())

    override fun onOutput(event: OutputEvent) {
        if (event is ProgressStartEvent) {
            var startEvent = event
            if (!startEvent.isBuildOperationStart) {
                forwarded.add(startEvent.getProgressOperationId())
                val parentProgressOperationId = startEvent.parentProgressOperationId
                if (parentProgressOperationId != null) {
                    val mappedId = effectiveProgressOperation.get(parentProgressOperationId)
                    if (mappedId != null) {
                        startEvent = startEvent.withParentProgressOperation(mappedId)
                    }
                }
                invokeListener(startEvent)
                return
            }

            if (startEvent.parentProgressOperationId == null || GUtil.isTrue(startEvent.getLoggingHeader()) || GUtil.isTrue(startEvent.status) || startEvent.buildOperationCategory != BuildOperationCategory.UNCATEGORIZED) {
                forwarded.add(startEvent.getProgressOperationId())
                val parentProgressOperationId = startEvent.parentProgressOperationId
                if (parentProgressOperationId != null) {
                    val mappedId = effectiveProgressOperation.get(parentProgressOperationId)
                    if (mappedId != null) {
                        startEvent = startEvent.withParentProgressOperation(mappedId)
                    }
                }
                invokeListener(startEvent)
            } else {
                // Ignore this progress operation, and map any reference to it to its parent (or whatever its parent is mapped to)
                var mappedParent = effectiveProgressOperation.get(startEvent.parentProgressOperationId)
                if (mappedParent == null) {
                    mappedParent = startEvent.parentProgressOperationId
                }
                effectiveProgressOperation.put(startEvent.getProgressOperationId(), mappedParent)
            }
        } else if (event is ProgressCompleteEvent) {
            val completeEvent = event
            val mappedEvent = effectiveProgressOperation.remove(completeEvent.progressOperationId)
            if (mappedEvent == null && forwarded.remove(completeEvent.progressOperationId)) {
                invokeListener(event)
            }
        } else if (event is ProgressEvent) {
            val progressEvent = event
            if (forwarded.contains(progressEvent.progressOperationId)) {
                invokeListener(event)
            }
        } else if (event is RenderableOutputEvent) {
            var outputEvent = event as RenderableOutputEvent?
            val operationId = outputEvent!!.buildOperationId
            if (operationId != null) {
                val mappedId = effectiveProgressOperation.get(operationId)
                if (mappedId != null) {
                    outputEvent = outputEvent.withBuildOperationId(mappedId)
                }
            }
            invokeListener(outputEvent!!)
        } else {
            invokeListener(event)
        }
    }

    private fun invokeListener(event: OutputEvent) {
        synchronized(lock) {
            listener.onOutput(event)
        }
    }
}
