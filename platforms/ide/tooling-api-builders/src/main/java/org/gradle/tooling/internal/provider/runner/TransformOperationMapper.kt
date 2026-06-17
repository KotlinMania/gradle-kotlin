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

import org.gradle.api.internal.artifacts.transform.ExecutePlannedTransformStepBuildOperationDetails
import org.gradle.api.internal.artifacts.transform.TransformStepNode
import org.gradle.execution.plan.Node
import org.gradle.internal.build.event.BuildEventSubscriptions
import org.gradle.internal.build.event.types.DefaultOperationFinishedProgressEvent
import org.gradle.internal.build.event.types.DefaultOperationStartedProgressEvent
import org.gradle.internal.build.event.types.DefaultTransformDescriptor
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.OperationFinishEvent
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.operations.OperationStartEvent
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.internal.protocol.events.InternalOperationDescriptor
import org.gradle.tooling.internal.protocol.events.InternalOperationFinishedProgressEvent
import org.gradle.tooling.internal.protocol.events.InternalOperationStartedProgressEvent
import org.gradle.tooling.internal.provider.runner.ClientForwardingBuildOperationListener.Companion.toOperationResult
import java.util.concurrent.ConcurrentHashMap

/**
 * Transform listener that forwards all receiving events to the client via the provided `ProgressEventConsumer` instance.
 *
 * @since 5.1
 */
internal class TransformOperationMapper(private val operationDependenciesResolver: OperationDependenciesResolver) :
    BuildOperationMapper<ExecutePlannedTransformStepBuildOperationDetails?, DefaultTransformDescriptor?>, OperationDependencyLookup {
    private val descriptors: MutableMap<TransformStepNode?, DefaultTransformDescriptor?> = ConcurrentHashMap<TransformStepNode?, DefaultTransformDescriptor?>()

    override fun isEnabled(subscriptions: BuildEventSubscriptions): Boolean {
        return subscriptions.isRequested(OperationType.TRANSFORM)
    }

    val detailsType: Class<ExecutePlannedTransformStepBuildOperationDetails?>?
        get() = ExecutePlannedTransformStepBuildOperationDetails::class.java

    override fun lookupExistingOperationDescriptor(node: Node?): InternalOperationDescriptor? {
        if (node is TransformStepNode) {
            return descriptors.get(node)
        }
        return null
    }

    override fun createDescriptor(details: ExecutePlannedTransformStepBuildOperationDetails, buildOperation: BuildOperationDescriptor, parent: OperationIdentifier?): DefaultTransformDescriptor {
        val id = buildOperation.getId()
        val displayName = buildOperation.getDisplayName()
        val transformerName = details.transformerName
        val subjectName = details.subjectName
        val dependencies = operationDependenciesResolver.resolveDependencies(details.transformStepNode)
        val descriptor = DefaultTransformDescriptor(id, displayName, parent, transformerName, subjectName, dependencies)
        val descriptorWithoutDependencies = DefaultTransformDescriptor(id, displayName, parent, transformerName, subjectName, mutableSetOf<InternalOperationDescriptor?>())
        descriptors.put(details.transformStepNode, descriptorWithoutDependencies)
        return descriptor
    }

    override fun createStartedEvent(
        descriptor: DefaultTransformDescriptor?,
        details: ExecutePlannedTransformStepBuildOperationDetails?,
        startEvent: OperationStartEvent
    ): InternalOperationStartedProgressEvent? {
        return DefaultOperationStartedProgressEvent(startEvent.getStartTime(), descriptor)
    }

    override fun createFinishedEvent(
        descriptor: DefaultTransformDescriptor?,
        details: ExecutePlannedTransformStepBuildOperationDetails?,
        finishEvent: OperationFinishEvent
    ): InternalOperationFinishedProgressEvent? {
        return DefaultOperationFinishedProgressEvent(finishEvent.getEndTime(), descriptor, toOperationResult(finishEvent))
    }
}
