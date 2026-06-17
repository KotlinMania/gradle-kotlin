/*
 * Copyright 2022 the original author or authors.
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
package org.gradle.tooling.internal.provider.runner

import org.gradle.internal.build.event.types.AbstractOperationResult
import org.gradle.internal.build.event.types.DefaultBuildPhaseDescriptor
import org.gradle.internal.build.event.types.DefaultFailure
import org.gradle.internal.build.event.types.DefaultFailureResult
import org.gradle.internal.build.event.types.DefaultOperationFinishedProgressEvent
import org.gradle.internal.build.event.types.DefaultOperationStartedProgressEvent
import org.gradle.internal.build.event.types.DefaultSuccessResult
import org.gradle.internal.operations.BuildOperationCategory
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationIdFactory
import org.gradle.internal.operations.BuildOperationListener
import org.gradle.internal.operations.OperationFinishEvent
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.operations.OperationProgressEvent
import org.gradle.internal.operations.OperationStartEvent
import org.gradle.tooling.internal.protocol.InternalFailure
import java.util.Collections
import java.util.EnumSet
import java.util.concurrent.ConcurrentHashMap

class BuildPhaseOperationListener(private val eventConsumer: ProgressEventConsumer, private val idFactory: BuildOperationIdFactory) : BuildOperationListener {
    private val descriptors: MutableMap<OperationIdentifier?, DefaultBuildPhaseDescriptor?>

    init {
        this.descriptors = ConcurrentHashMap<OperationIdentifier?, DefaultBuildPhaseDescriptor?>()
    }

    override fun started(buildOperation: BuildOperationDescriptor, startEvent: OperationStartEvent) {
        if (!isSupportedBuildOperation(buildOperation)) {
            return
        }
        val descriptor = toBuildOperationDescriptor(buildOperation)
        descriptors.put(buildOperation.getId(), descriptor)
        eventConsumer.started(DefaultOperationStartedProgressEvent(startEvent.getStartTime(), descriptor))
    }

    private fun toBuildOperationDescriptor(buildOperation: BuildOperationDescriptor): DefaultBuildPhaseDescriptor {
        val operationId = OperationIdentifier(idFactory.nextId())
        val parent = eventConsumer.findStartedParentId(buildOperation)
        val name = buildOperation.getName()
        val displayName = "Build phase: " + buildOperation.getDisplayName()
        val buildPhase: String? = buildOperation.getMetadata().toString()
        return DefaultBuildPhaseDescriptor(operationId, name, displayName, parent, buildPhase, buildOperation.getTotalProgress())
    }

    override fun progress(operationIdentifier: OperationIdentifier, progressEvent: OperationProgressEvent) {
    }

    override fun finished(buildOperation: BuildOperationDescriptor, finishEvent: OperationFinishEvent) {
        if (!isSupportedBuildOperation(buildOperation)) {
            return
        }
        val descriptor = descriptors.remove(buildOperation.getId())
        if (descriptor != null) {
            val endTime = finishEvent.getEndTime()
            val result = toOperationResult(finishEvent)
            eventConsumer.finished(DefaultOperationFinishedProgressEvent(endTime, descriptor, result))
        }
    }

    private fun toOperationResult(finishEvent: OperationFinishEvent): AbstractOperationResult {
        val startTime = finishEvent.getStartTime()
        val endTime = finishEvent.getEndTime()
        if (finishEvent.getFailure() != null) {
            return DefaultFailureResult(startTime, endTime, mutableListOf<InternalFailure?>(DefaultFailure.fromThrowable(finishEvent.getFailure())))
        } else {
            return DefaultSuccessResult(startTime, endTime)
        }
    }

    private fun isSupportedBuildOperation(buildOperation: BuildOperationDescriptor): Boolean {
        return buildOperation.getMetadata() is BuildOperationCategory && SUPPORTED_CATEGORIES.contains(buildOperation.getMetadata())
    }

    companion object {
        private val SUPPORTED_CATEGORIES: MutableSet<BuildOperationCategory?> = Collections.unmodifiableSet<BuildOperationCategory?>(
            EnumSet.of<BuildOperationCategory?>(
                BuildOperationCategory.CONFIGURE_ROOT_BUILD,
                BuildOperationCategory.CONFIGURE_BUILD,
                BuildOperationCategory.RUN_MAIN_TASKS,
                BuildOperationCategory.RUN_WORK
            )
        )
    }
}
