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

import org.gradle.internal.build.event.BuildEventSubscriptions
import org.gradle.internal.build.event.types.DefaultOperationFinishedProgressEvent
import org.gradle.internal.build.event.types.DefaultOperationStartedProgressEvent
import org.gradle.internal.build.event.types.DefaultWorkItemDescriptor
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.OperationFinishEvent
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.operations.OperationStartEvent
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.internal.protocol.events.InternalOperationFinishedProgressEvent
import org.gradle.tooling.internal.protocol.events.InternalOperationStartedProgressEvent
import org.gradle.tooling.internal.provider.runner.ClientForwardingBuildOperationListener.Companion.toOperationResult
import org.gradle.workers.internal.ExecuteWorkItemBuildOperationType

/**
 * Work item listener that forwards all receiving events to the client via the provided `ProgressEventConsumer` instance.
 *
 * @since 5.1
 */
internal class WorkItemOperationMapper : BuildOperationMapper<ExecuteWorkItemBuildOperationType.Details?, DefaultWorkItemDescriptor?> {
    override fun isEnabled(subscriptions: BuildEventSubscriptions): Boolean {
        return subscriptions.isRequested(OperationType.TASK) && subscriptions.isRequested(OperationType.WORK_ITEM)
    }

    val detailsType: Class<ExecuteWorkItemBuildOperationType.Details?>?
        get() = ExecuteWorkItemBuildOperationType.Details::class.java

    override fun createDescriptor(details: ExecuteWorkItemBuildOperationType.Details, buildOperation: BuildOperationDescriptor, parent: OperationIdentifier?): DefaultWorkItemDescriptor? {
        val id = buildOperation.getId()
        val className = details.getClassName()
        val displayName = buildOperation.getDisplayName()
        return DefaultWorkItemDescriptor(id, className, displayName, parent)
    }

    override fun createStartedEvent(
        descriptor: DefaultWorkItemDescriptor?,
        details: ExecuteWorkItemBuildOperationType.Details?,
        startEvent: OperationStartEvent
    ): InternalOperationStartedProgressEvent? {
        return DefaultOperationStartedProgressEvent(startEvent.getStartTime(), descriptor)
    }

    override fun createFinishedEvent(
        descriptor: DefaultWorkItemDescriptor?,
        details: ExecuteWorkItemBuildOperationType.Details?,
        finishEvent: OperationFinishEvent
    ): InternalOperationFinishedProgressEvent? {
        return DefaultOperationFinishedProgressEvent(finishEvent.getEndTime(), descriptor, toOperationResult(finishEvent))
    }
}
