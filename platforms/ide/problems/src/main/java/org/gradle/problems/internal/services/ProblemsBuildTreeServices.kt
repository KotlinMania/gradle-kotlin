/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.problems.internal.services

import com.google.common.collect.ImmutableList
import org.gradle.api.GradleException
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.StartParameterInternal
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.file.temp.TemporaryFileProvider
import org.gradle.api.problems.internal.DefaultProblems
import org.gradle.api.problems.internal.ExceptionProblemRegistry
import org.gradle.api.problems.internal.InternalProblems
import org.gradle.api.problems.internal.IsolatableToBytesSerializer
import org.gradle.api.problems.internal.ProblemEmitter
import org.gradle.api.problems.internal.ProblemReportCreator
import org.gradle.api.problems.internal.ProblemSummarizer
import org.gradle.api.problems.internal.ProblemTaskIdentityTracker
import org.gradle.api.problems.internal.ProblemsInternal
import org.gradle.api.problems.internal.TaskIdentity
import org.gradle.api.problems.internal.TaskIdentityProvider
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.buildoption.InternalOptions
import org.gradle.internal.code.UserCodeApplicationContext
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.exception.ExceptionAnalyser
import org.gradle.internal.execution.WorkExecutionTracker
import org.gradle.internal.instantiation.InstantiatorFactory
import org.gradle.internal.isolation.IsolatableFactory
import org.gradle.internal.operations.BuildOperationProgressEventEmitter
import org.gradle.internal.operations.CurrentBuildOperationRef
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.problems.failure.FailureFactory
import org.gradle.internal.service.Provides
import org.gradle.internal.service.ServiceRegistrationProvider
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.problems.buildtree.ProblemStream
import org.gradle.problems.internal.NoOpProblemReportCreator
import org.gradle.problems.internal.emitters.BuildOperationBasedProblemEmitter
import org.gradle.problems.internal.emitters.ConsoleProblemEmitter
import org.gradle.problems.internal.impl.DefaultProblemsReportCreator
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer
import java.util.function.Function

@ServiceScope(Scope.BuildTree::class)
class ProblemsBuildTreeServices : ServiceRegistrationProvider {
    @Provides
    fun createProblemsService(
        problemSummarizer: ProblemSummarizer,
        problemStream: ProblemStream,
        exceptionProblemRegistry: ExceptionProblemRegistry,
        exceptionAnalyser: ExceptionAnalyser,
        instantiatorFactory: InstantiatorFactory,
        payloadSerializer: PayloadSerializer,
        isolatableFactory: IsolatableFactory,
        isolatableToBytesSerializer: IsolatableToBytesSerializer,
        serviceRegistry: ServiceRegistry
    ): ProblemsInternal {
        return DefaultProblems(
            problemSummarizer,
            problemStream,
            CurrentBuildOperationRef.instance(),
            exceptionProblemRegistry,
            exceptionAnalyser,
            instantiatorFactory.decorateLenient(serviceRegistry),
            payloadSerializer,
            isolatableFactory,
            isolatableToBytesSerializer
        )
    }

    @Provides
    fun createProblemSummarizer(
        eventEmitter: BuildOperationProgressEventEmitter,
        currentBuildOperationRef: CurrentBuildOperationRef,
        problemEmitters: MutableCollection<ProblemEmitter>,
        internalOptions: InternalOptions,
        problemReportCreator: ProblemReportCreator,
        workExecutionTracker: WorkExecutionTracker,
        startParameter: StartParameterInternal
    ): ProblemSummarizer {
        return DefaultProblemSummarizer(
            eventEmitter,
            currentBuildOperationRef,
            ImmutableList.of<ProblemEmitter>(BuildOperationBasedProblemEmitter(eventEmitter), ConsoleProblemEmitter(startParameter.warningMode)),
            internalOptions,
            problemReportCreator,
            TaskIdentityProvider { id: OperationIdentifier? ->
                val taskIdentity = ProblemTaskIdentityTracker.getTaskIdentity()
                if (taskIdentity != null) {
                    return@TaskIdentityProvider taskIdentity
                } else {
                    return@TaskIdentityProvider workExecutionTracker
                        .getCurrentTask(id!!)
                        .map<TaskIdentity>(Function { task: TaskInternal? -> TaskIdentity(task!!.getTaskIdentity().getBuildTreePath().asString()) })
                        .orElse(null)
                }
            }
        )
    }

    @Provides
    fun createProblemsReportCreator(
        executorFactory: ExecutorFactory,
        temporaryFileProvider: TemporaryFileProvider,
        internalOptions: InternalOptions,
        startParameter: StartParameterInternal,
        listenerManager: ListenerManager,
        failureFactory: FailureFactory,
        buildStateRegistry: BuildStateRegistry
    ): ProblemReportCreator {
        if (startParameter.isProblemReportGenerationEnabled) {
            return DefaultProblemsReportCreator(executorFactory, temporaryFileProvider, internalOptions, startParameter, failureFactory, buildStateRegistry)
        }
        return NoOpProblemReportCreator()
    }

    /**
     * Fails with an actionable error when a plugin requests the removed `InternalProblems` internal type.
     *
     */
    @Provides
    @Deprecated("Will be removed in Gradle 10.0")
    fun createInternalProblems(userCodeContext: UserCodeApplicationContext): InternalProblems? {
        val current = userCodeContext.current()
        val culprit: String?
        if (current == null) {
            culprit = "A plugin you are using"
        } else {
            val displayName = current.getSource().getDisplayName().getDisplayName()
            culprit = displayName.get(0).uppercaseChar().toString() + displayName.substring(1)
        }
        throw GradleException(
            culprit + " relies on 'org.gradle.api.problems.internal.InternalProblems', " +
                    "a Gradle internal API that was removed in Gradle 9.6.0. " +
                    "Update the plugin to a version that no longer uses Gradle internal APIs, or use Gradle 9.5. " +
                    DocumentationRegistry().getDocumentationRecommendationFor("information", "upgrading_version_9", "agp_8x_incompatible")
        )
    }
}
