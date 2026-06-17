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
package org.gradle.api.internal.artifacts.transform

import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectStateRegistry
import org.gradle.cache.Cache
import org.gradle.internal.Deferrable
import org.gradle.internal.Try
import org.gradle.internal.buildoption.InternalOptions
import org.gradle.internal.execution.DeferredResult
import org.gradle.internal.execution.ExecutionEngine
import org.gradle.internal.execution.Identity
import org.gradle.internal.execution.InputFingerprinter
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.operations.BuildOperationProgressEventEmitter
import org.gradle.internal.operations.BuildOperationRunner
import java.io.File
import java.util.Arrays
import java.util.function.Function

class DefaultTransformInvocationFactory(
    private val executionEngine: ExecutionEngine,
    private val internalOptions: InternalOptions,
    private val transformExecutionListener: TransformExecutionListener,
    private val immutableWorkspaceServices: ImmutableTransformWorkspaceServices,
    private val fileCollectionFactory: FileCollectionFactory,
    private val projectStateRegistry: ProjectStateRegistry,
    private val buildOperationRunner: BuildOperationRunner,
    private val progressEventEmitter: BuildOperationProgressEventEmitter
) : TransformInvocationFactory {
    override fun createInvocation(
        transform: Transform,
        inputArtifact: File,
        dependencies: TransformDependencies,
        subject: TransformStepSubject,
        inputFingerprinter: InputFingerprinter
    ): Deferrable<Try<ImmutableList<File>>?> {
        val producerProject = determineProducerProject(subject)

        val identityCache: Cache<Identity, DeferredResult<TransformExecutionResult.TransformWorkspaceResult>>?
        val execution: UnitOfWork?

        val cachingDisabledByProperty = isCachingDisabledByProperty(transform)

        if (producerProject != null && transform.requiresInputChanges()) {
            // Incremental project artifact transforms are executed in a project-bound mutable workspace
            val workspaceServices = producerProject.getServices().get<MutableTransformWorkspaceServices?>(MutableTransformWorkspaceServices::class.java)
            identityCache = workspaceServices!!.getIdentityCache()
            execution = MutableTransformExecution(
                transform,
                inputArtifact,
                dependencies,
                subject,
                producerProject,

                transformExecutionListener,
                buildOperationRunner,
                progressEventEmitter,
                fileCollectionFactory,
                inputFingerprinter,
                workspaceServices.getWorkspaceProvider(),
                workspaceServices.getExecutionHistoryStore(),

                cachingDisabledByProperty
            )
        } else {
            // Immutable transforms and transforms without a producer project are executed in a global immutable workspace
            identityCache = immutableWorkspaceServices.getIdentityCache()
            execution = ImmutableTransformExecution(
                transform,
                inputArtifact,
                dependencies,
                subject,

                transformExecutionListener,
                buildOperationRunner,
                progressEventEmitter,
                fileCollectionFactory,
                inputFingerprinter,
                immutableWorkspaceServices.getWorkspaceProvider(),

                cachingDisabledByProperty
            )
        }
        return executionEngine.createRequest(execution)
            .executeDeferred<TransformExecutionResult.TransformWorkspaceResult>(identityCache)
            .map<Try<ImmutableList<File>?>?>(Function { result: Try<TransformExecutionResult.TransformWorkspaceResult?>? ->
                result!!
                    .map<ImmutableList<File>?>(java.util.function.Function { successfulResult: org.gradle.api.internal.artifacts.transform.TransformExecutionResult.TransformWorkspaceResult? ->
                        successfulResult!!.resolveForInputArtifact(
                            inputArtifact
                        )
                    })!!
                    .mapFailure(Function { failure: Throwable? -> TransformException(String.format("Execution failed for %s.", execution.getDisplayName()), failure!!) })
            })
    }

    private fun determineProducerProject(subject: TransformStepSubject): ProjectInternal? {
        val componentIdentifier = subject.getInitialComponentIdentifier()
        if (componentIdentifier is ProjectComponentIdentifier) {
            return projectStateRegistry.stateFor(componentIdentifier).getMutableModel()
        } else {
            return null
        }
    }

    private fun isCachingDisabledByProperty(transform: Transform): Boolean {
        val experimentalProperty = internalOptions.getValueOrNull<String?>(CACHING_DISABLED_PROPERTY)
        if (experimentalProperty != null) {
            if (experimentalProperty.isEmpty() || experimentalProperty == "true") {
                return true
            }
            val disabledTransformClasses = Arrays.asList<String>(*experimentalProperty.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
            return disabledTransformClasses.contains(transform.getImplementationClass().getName())
        }

        return false
    }

    companion object {
        private val CACHING_DISABLED_PROPERTY = InternalOptions.ofStringOrNull("org.gradle.internal.transform-caching-disabled")
    }
}
