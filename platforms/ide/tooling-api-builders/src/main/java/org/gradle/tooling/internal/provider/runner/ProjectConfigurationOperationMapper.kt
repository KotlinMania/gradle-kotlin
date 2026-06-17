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
package org.gradle.tooling.internal.provider.runner

import com.google.common.collect.ImmutableList
import org.gradle.configuration.project.ConfigureProjectBuildOperationType
import org.gradle.internal.build.event.BuildEventSubscriptions
import org.gradle.internal.build.event.types.AbstractProjectConfigurationResult
import org.gradle.internal.build.event.types.DefaultFailure
import org.gradle.internal.build.event.types.DefaultOperationFinishedProgressEvent
import org.gradle.internal.build.event.types.DefaultOperationStartedProgressEvent
import org.gradle.internal.build.event.types.DefaultProjectConfigurationDescriptor
import org.gradle.internal.build.event.types.DefaultProjectConfigurationFailureResult
import org.gradle.internal.build.event.types.DefaultProjectConfigurationSuccessResult
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.OperationFinishEvent
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.operations.OperationStartEvent
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.internal.protocol.InternalFailure
import org.gradle.tooling.internal.protocol.events.InternalOperationFinishedProgressEvent
import org.gradle.tooling.internal.protocol.events.InternalOperationStartedProgressEvent
import org.gradle.tooling.internal.protocol.events.InternalProjectConfigurationResult

internal class ProjectConfigurationOperationMapper(private val projectConfigurationTracker: ProjectConfigurationTracker) :
    BuildOperationMapper<ConfigureProjectBuildOperationType.Details?, DefaultProjectConfigurationDescriptor?> {
    override fun isEnabled(subscriptions: BuildEventSubscriptions): Boolean {
        return subscriptions.isRequested(OperationType.PROJECT_CONFIGURATION)
    }

    val detailsType: Class<ConfigureProjectBuildOperationType.Details?>?
        get() = ConfigureProjectBuildOperationType.Details::class.java

    val trackers: MutableList<BuildOperationTracker?>?
        get() = ImmutableList.of<BuildOperationTracker?>(projectConfigurationTracker)

    override fun createDescriptor(details: ConfigureProjectBuildOperationType.Details, buildOperation: BuildOperationDescriptor, parent: OperationIdentifier?): DefaultProjectConfigurationDescriptor? {
        val id = buildOperation.getId()
        val displayName = buildOperation.getDisplayName()
        return DefaultProjectConfigurationDescriptor(id, displayName, parent, details.rootDir, details.projectPath)
    }

    override fun createStartedEvent(
        descriptor: DefaultProjectConfigurationDescriptor?,
        details: ConfigureProjectBuildOperationType.Details?,
        startEvent: OperationStartEvent
    ): InternalOperationStartedProgressEvent? {
        return DefaultOperationStartedProgressEvent(startEvent.getStartTime(), descriptor)
    }

    override fun createFinishedEvent(
        descriptor: DefaultProjectConfigurationDescriptor,
        details: ConfigureProjectBuildOperationType.Details?,
        finishEvent: OperationFinishEvent
    ): InternalOperationFinishedProgressEvent? {
        val result = toProjectConfigurationOperationResult(finishEvent, projectConfigurationTracker.resultsFor(descriptor.getId()))
        return DefaultOperationFinishedProgressEvent(finishEvent.getEndTime(), descriptor, result)
    }

    private fun toProjectConfigurationOperationResult(
        finishEvent: OperationFinishEvent,
        pluginApplicationResults: MutableList<InternalProjectConfigurationResult.InternalPluginApplicationResult?>?
    ): AbstractProjectConfigurationResult {
        val startTime = finishEvent.getStartTime()
        val endTime = finishEvent.getEndTime()
        val failure = finishEvent.getFailure()
        if (failure != null) {
            return DefaultProjectConfigurationFailureResult(startTime, endTime, mutableListOf<InternalFailure?>(DefaultFailure.fromThrowable(failure)), pluginApplicationResults)
        }
        return DefaultProjectConfigurationSuccessResult(startTime, endTime, pluginApplicationResults)
    }
}
