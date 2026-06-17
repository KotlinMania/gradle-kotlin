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

import org.gradle.api.problems.internal.DefaultProblemProgressDetails
import org.gradle.api.problems.internal.DefaultProblemsSummaryProgressDetails
import org.gradle.api.problems.internal.ProblemInternal
import org.gradle.internal.build.event.BuildEventSubscriptions
import org.gradle.internal.build.event.types.AbstractOperationResult
import org.gradle.internal.build.event.types.DefaultFailure
import org.gradle.internal.build.event.types.DefaultFailureResult
import org.gradle.internal.build.event.types.DefaultOperationDescriptor
import org.gradle.internal.build.event.types.DefaultOperationFinishedProgressEvent
import org.gradle.internal.build.event.types.DefaultOperationStartedProgressEvent
import org.gradle.internal.build.event.types.DefaultRootOperationDescriptor
import org.gradle.internal.build.event.types.DefaultSuccessResult
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationListener
import org.gradle.internal.operations.OperationFinishEvent
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.operations.OperationProgressEvent
import org.gradle.internal.operations.OperationStartEvent
import org.gradle.internal.problems.failure.Failure
import org.gradle.launcher.exec.RunBuildBuildOperationType
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.internal.protocol.InternalFailure
import org.jspecify.annotations.NullMarked
import java.util.function.Function
import java.util.function.Supplier

/**
 * Build listener that forwards all receiving events to the client via the provided `ProgressEventConsumer` instance.
 *
 * @since 2.5
 */
@NullMarked
internal class ClientForwardingBuildOperationListener(
    protected val eventConsumer: ProgressEventConsumer,
    buildEventSubscriptions: BuildEventSubscriptions,
    private val operationIdentifierSupplier: Supplier<OperationIdentifier>
) : BuildOperationListener {
    private val problemsRequested: Boolean
    private val genericRequested: Boolean
    private val rootRequested: Boolean

    init {
        this.problemsRequested = buildEventSubscriptions.isRequested(OperationType.PROBLEMS)
        this.genericRequested = buildEventSubscriptions.isRequested(OperationType.GENERIC)
        this.rootRequested = buildEventSubscriptions.isRequested(OperationType.ROOT)
    }

    override fun started(buildOperation: BuildOperationDescriptor, startEvent: OperationStartEvent) {
        // RunBuildBuildOperationType.Details is the type of the details object associated with the root build operation
        if ((rootRequested && buildOperation.getDetails() is RunBuildBuildOperationType.Details) || genericRequested) {
            eventConsumer.started(DefaultOperationStartedProgressEvent(startEvent.getStartTime(), toBuildOperationDescriptor(buildOperation)))
        }
    }

    override fun progress(buildOperationId: OperationIdentifier, progressEvent: OperationProgressEvent) {
        if (problemsRequested) {
            val details = progressEvent.getDetails()
            if (details is DefaultProblemProgressDetails) {
                eventConsumer.progress(
                    ProblemsProgressEventUtils.createProblemEvent(
                        eventConsumer.findStartedParentId(buildOperationId)!!,
                        details,
                        operationIdentifierSupplier
                    )
                )
            } else if (details is DefaultProblemsSummaryProgressDetails) {
                eventConsumer.progress(
                    ProblemsProgressEventUtils.createProblemSummaryEvent(
                        eventConsumer.findStartedParentId(buildOperationId),
                        details,
                        operationIdentifierSupplier
                    )
                )
            }
        }
    }

    override fun finished(buildOperation: BuildOperationDescriptor, result: OperationFinishEvent) {
        // RunBuildBuildOperationType.Details is the type of the details object associated with the root build operation
        if (rootRequested && buildOperation.getDetails() is RunBuildBuildOperationType.Details) {
            val operationResult = result.getResult() as RunBuildBuildOperationType.Result?
            val failure: Failure? = if (operationResult == null) null else operationResult.failure
            eventConsumer.finished(DefaultOperationFinishedProgressEvent(result.getEndTime(), createRootOperationDescriptor(buildOperation), toOperationResult(result, failure)))
        } else if (genericRequested) {
            eventConsumer.finished(DefaultOperationFinishedProgressEvent(result.getEndTime(), toBuildOperationDescriptor(buildOperation), toOperationResult(result)))
        }
    }

    fun createRootOperationDescriptor(buildOperation: BuildOperationDescriptor): DefaultRootOperationDescriptor {
        val id = buildOperation.getId()
        val name = buildOperation.getName()
        val displayName = buildOperation.getDisplayName()
        return DefaultRootOperationDescriptor(id!!, name, displayName, null)
    }

    protected fun toBuildOperationDescriptor(buildOperation: BuildOperationDescriptor): DefaultOperationDescriptor {
        val id = buildOperation.getId()
        val name = buildOperation.getName()
        val displayName = buildOperation.getDisplayName()
        val parentId = eventConsumer.findStartedParentId(buildOperation)
        return DefaultOperationDescriptor(id, name, displayName, parentId)
    }

    companion object {
        @JvmStatic
        @JvmOverloads
        fun toOperationResult(result: OperationFinishEvent, buildFailure: Failure? = null): AbstractOperationResult {
            val failure = result.getFailure()
            val startTime = result.getStartTime()
            val endTime = result.getEndTime()
            if (failure != null) {
                if (buildFailure != null) {
                    val rootFailure = DefaultFailure.fromFailure(buildFailure, Function { problem: ProblemInternal? -> ProblemsProgressEventUtils.createDefaultProblemDetails(problem!!) })
                    return DefaultFailureResult(startTime, endTime, mutableListOf<InternalFailure>(rootFailure))
                } else {
                    return DefaultFailureResult(startTime, endTime, mutableListOf<InternalFailure>(DefaultFailure.fromThrowable(failure)))
                }
            }
            return DefaultSuccessResult(startTime, endTime)
        }
    }
}
