/*
 * Copyright 2015 the original author or authors.
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
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.project.taskfactory.TaskIdentity
import org.gradle.api.internal.tasks.execution.ExecuteTaskBuildOperationDetails
import org.gradle.api.internal.tasks.execution.ExecuteTaskBuildOperationType
import org.gradle.execution.plan.Node
import org.gradle.execution.plan.TaskNode
import org.gradle.internal.build.event.BuildEventSubscriptions
import org.gradle.internal.build.event.OperationResultPostProcessor
import org.gradle.internal.build.event.types.AbstractTaskResult
import org.gradle.internal.build.event.types.DefaultBinaryPluginIdentifier
import org.gradle.internal.build.event.types.DefaultFailure
import org.gradle.internal.build.event.types.DefaultScriptPluginIdentifier
import org.gradle.internal.build.event.types.DefaultTaskDescriptor
import org.gradle.internal.build.event.types.DefaultTaskFailureResult
import org.gradle.internal.build.event.types.DefaultTaskFinishedProgressEvent
import org.gradle.internal.build.event.types.DefaultTaskSkippedResult
import org.gradle.internal.build.event.types.DefaultTaskStartedProgressEvent
import org.gradle.internal.build.event.types.DefaultTaskSuccessResult
import org.gradle.internal.code.UserCodeSource
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.OperationFinishEvent
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.operations.OperationStartEvent
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.internal.protocol.InternalFailure
import org.gradle.tooling.internal.protocol.events.InternalBinaryPluginIdentifier
import org.gradle.tooling.internal.protocol.events.InternalOperationDescriptor
import org.gradle.tooling.internal.protocol.events.InternalOperationFinishedProgressEvent
import org.gradle.tooling.internal.protocol.events.InternalOperationStartedProgressEvent
import org.gradle.tooling.internal.protocol.events.InternalPluginIdentifier
import org.gradle.tooling.internal.protocol.events.InternalScriptPluginIdentifier
import java.util.concurrent.ConcurrentHashMap

internal class TaskOperationMapper(operationResultPostProcessors: MutableList<OperationResultPostProcessor>, private val operationDependenciesResolver: OperationDependenciesResolver) :
    BuildOperationMapper<ExecuteTaskBuildOperationDetails?, DefaultTaskDescriptor?>, OperationDependencyLookup {
    private val descriptors: MutableMap<TaskIdentity<*>?, DefaultTaskDescriptor?> = ConcurrentHashMap<TaskIdentity<*>?, DefaultTaskDescriptor?>()
    private val operationResultPostProcessor: PostProcessors

    init {
        this.operationResultPostProcessor = PostProcessors(operationResultPostProcessors)
    }

    override fun isEnabled(subscriptions: BuildEventSubscriptions): Boolean {
        return subscriptions.isRequested(OperationType.TASK)
    }

    val detailsType: Class<ExecuteTaskBuildOperationDetails?>?
        get() = ExecuteTaskBuildOperationDetails::class.java

    val trackers: MutableList<out BuildOperationTracker?>?
        get() = ImmutableList.of<PostProcessors?>(operationResultPostProcessor)

    override fun lookupExistingOperationDescriptor(node: Node?): InternalOperationDescriptor? {
        if (node is TaskNode) {
            val taskNode = node
            return descriptors.get(taskNode.getTask().getTaskIdentity())
        }
        return null
    }

    override fun createDescriptor(details: ExecuteTaskBuildOperationDetails, buildOperation: BuildOperationDescriptor, parent: OperationIdentifier?): DefaultTaskDescriptor {
        val id = buildOperation.getId()
        val taskIdentityPath = buildOperation.getName()
        val displayName = buildOperation.getDisplayName()
        val taskPath = details.getTask().getIdentityPath().asString()
        val dependencies = operationDependenciesResolver.resolveDependencies(details.getTaskNode())
        val originPlugin: InternalPluginIdentifier? = toInternalPluginId(details.getTask().getTaskIdentity().getUserCodeSource())
        val descriptor = DefaultTaskDescriptor(id, taskIdentityPath, taskPath, displayName, parent, dependencies, originPlugin)
        val descriptorWithoutDependencies = DefaultTaskDescriptor(id, taskIdentityPath, taskPath, displayName, parent, mutableSetOf<InternalOperationDescriptor?>(), originPlugin)
        descriptors.put(details.getTask().getTaskIdentity(), descriptorWithoutDependencies)
        return descriptor
    }

    override fun createStartedEvent(descriptor: DefaultTaskDescriptor?, details: ExecuteTaskBuildOperationDetails?, startEvent: OperationStartEvent): InternalOperationStartedProgressEvent? {
        return DefaultTaskStartedProgressEvent(startEvent.getStartTime(), descriptor)
    }

    override fun createFinishedEvent(descriptor: DefaultTaskDescriptor?, details: ExecuteTaskBuildOperationDetails, finishEvent: OperationFinishEvent): InternalOperationFinishedProgressEvent? {
        val task = details.getTask()
        val taskResult = operationResultPostProcessor.process(toTaskResult(task, finishEvent), task)
        return DefaultTaskFinishedProgressEvent(finishEvent.getEndTime(), descriptor, taskResult)
    }

    private class PostProcessors(private val processors: MutableList<OperationResultPostProcessor>) : BuildOperationTracker {
        override fun started(buildOperation: BuildOperationDescriptor?, startEvent: OperationStartEvent?) {
            for (processor in processors) {
                processor.started(buildOperation, startEvent)
            }
        }

        override fun finished(buildOperation: BuildOperationDescriptor?, finishEvent: OperationFinishEvent?) {
            for (processor in processors) {
                processor.finished(buildOperation, finishEvent)
            }
        }

        fun process(taskResult: AbstractTaskResult?, taskInternal: TaskInternal?): AbstractTaskResult? {
            var taskResult = taskResult
            for (factory in processors) {
                taskResult = factory.process(taskResult, taskInternal)
            }
            return taskResult
        }
    }

    companion object {
        private fun toInternalPluginId(source: UserCodeSource?): InternalPluginIdentifier? {
            if (source is UserCodeSource.Binary) {
                return toBinaryPluginIdentifier(source)
            } else if (source is UserCodeSource.Script) {
                if (source.getUri() != null) {
                    return toScriptPluginIdentifier(source)
                }
            }

            return null
        }

        private fun toBinaryPluginIdentifier(source: UserCodeSource.Binary): InternalBinaryPluginIdentifier {
            return DefaultBinaryPluginIdentifier(
                source.getDisplayName().getDisplayName(),
                source.getClassName(),
                source.getPluginId()
            )
        }

        private fun toScriptPluginIdentifier(source: UserCodeSource.Script): InternalScriptPluginIdentifier {
            return DefaultScriptPluginIdentifier(
                source.getDisplayName().getDisplayName(),
                source.getUri()
            )
        }

        private fun toTaskResult(task: TaskInternal, finishEvent: OperationFinishEvent): AbstractTaskResult {
            val state = task.getState()
            val startTime = finishEvent.getStartTime()
            val endTime = finishEvent.getEndTime()
            val result = finishEvent.getResult() as ExecuteTaskBuildOperationType.Result?
            val incremental = result != null && result.isIncremental

            if (state.getUpToDate()) {
                return DefaultTaskSuccessResult(startTime, endTime, true, state.isFromCache(), state.getSkipMessage(), incremental, mutableListOf<String?>())
            } else if (state.getSkipped()) {
                return DefaultTaskSkippedResult(startTime, endTime, state.getSkipMessage(), incremental)
            } else {
                val executionReasons = if (result != null) result.upToDateMessages else null
                val failure = finishEvent.getFailure()
                if (failure == null) {
                    return DefaultTaskSuccessResult(startTime, endTime, false, state.isFromCache(), "SUCCESS", incremental, executionReasons)
                } else {
                    return DefaultTaskFailureResult(startTime, endTime, mutableListOf<InternalFailure?>(DefaultFailure.fromThrowable(failure)), incremental, executionReasons)
                }
            }
        }
    }
}
