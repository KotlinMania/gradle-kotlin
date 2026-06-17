/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve

import org.gradle.api.Transformer
import org.gradle.api.artifacts.ComponentMetadataSupplierDetails
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact
import org.gradle.api.internal.artifacts.repositories.resolver.MetadataFetchingCost
import org.gradle.api.internal.artifacts.repositories.transport.NetworkingIssueVerifier.isLikelyPermanentNetworkIssue
import org.gradle.api.internal.artifacts.repositories.transport.NetworkingIssueVerifier.isLikelyTransientNetworkingIssue
import org.gradle.api.internal.component.ArtifactType
import org.gradle.api.logging.Logging.getLogger
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.action.InstantiatingAction
import org.gradle.internal.component.external.model.ExternalModuleComponentGraphResolveState
import org.gradle.internal.component.model.ComponentArtifactMetadata
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata
import org.gradle.internal.component.model.ComponentOverrideMetadata
import org.gradle.internal.component.model.ModuleSources
import org.gradle.internal.resolve.result.BuildableArtifactFileResolveResult
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult
import org.gradle.internal.resolve.result.BuildableModuleVersionListingResolveResult
import org.gradle.internal.resolve.result.ErroringResolveResult
import java.util.concurrent.Callable

/**
 * A ModuleComponentRepository that catches any exception and applies it to the result object.
 * This allows other repository implementations to throw exceptions on failure.
 *
 * This implementation will also disable any repository that throws a critical failure, failing-fast with that
 * repository for any subsequent requests.
 */
class ErrorHandlingModuleComponentRepository(private val delegate: ModuleComponentRepository<ExternalModuleComponentGraphResolveState?>, private val remoteRepositoryDisabler: RepositoryDisabler) :
    ModuleComponentRepository<ExternalModuleComponentGraphResolveState?> {
    private val local: ErrorHandlingModuleComponentRepositoryAccess
    private val remote: ErrorHandlingModuleComponentRepositoryAccess

    init {
        this.local = ErrorHandlingModuleComponentRepository.ErrorHandlingModuleComponentRepositoryAccess(delegate.getLocalAccess(), getId(), RepositoryDisabler.NoOpDisabler.INSTANCE, getName(), false)
        this.remote = ErrorHandlingModuleComponentRepository.ErrorHandlingModuleComponentRepositoryAccess(
            delegate.getRemoteAccess(), getId(),
            remoteRepositoryDisabler, getName(), isContinueOnConnectionFailure()
        )
    }

    override fun toString(): String {
        return delegate.toString()
    }

    override fun getId(): String? {
        return delegate.getId()
    }

    override fun getName(): String? {
        return delegate.getName()
    }

    override fun getLocalAccess(): ModuleComponentRepositoryAccess<ExternalModuleComponentGraphResolveState?> {
        return local
    }

    override fun getRemoteAccess(): ModuleComponentRepositoryAccess<ExternalModuleComponentGraphResolveState?> {
        return remote
    }

    override fun getArtifactCache(): MutableMap<ComponentArtifactIdentifier?, ResolvableArtifact?>? {
        return delegate.getArtifactCache()
    }

    override fun getComponentMetadataSupplier(): InstantiatingAction<ComponentMetadataSupplierDetails?>? {
        return delegate.getComponentMetadataSupplier()
    }

    override fun isContinueOnConnectionFailure(): Boolean {
        return delegate.isContinueOnConnectionFailure()
    }

    override fun isRepositoryDisabled(): Boolean {
        return remoteRepositoryDisabler.isDisabled(getId()!!) || delegate.isRepositoryDisabled()
    }

    private class ErrorHandlingModuleComponentRepositoryAccess(
        delegate: ModuleComponentRepositoryAccess<ExternalModuleComponentGraphResolveState?>,
        repositoryId: String,
        repositoryDisabler: RepositoryDisabler,
        maxTentativesCount: Int,
        initialBackOff: Int,
        private val repositoryName: String?,
        continueOnConnectionFailure: Boolean
    ) : ModuleComponentRepositoryAccess<ExternalModuleComponentGraphResolveState?> {
        private val delegate: ModuleComponentRepositoryAccess<ExternalModuleComponentGraphResolveState?>
        private val repositoryId: String
        private val repositoryDisabler: RepositoryDisabler
        private val maxTentativesCount: Int
        private val initialBackOff: Int
        private val continueOnConnectionFailure: Boolean

        private constructor(
            delegate: ModuleComponentRepositoryAccess<ExternalModuleComponentGraphResolveState?>,
            repositoryId: String,
            repositoryDisabler: RepositoryDisabler,
            repositoryName: String?,
            continueOnConnectionFailure: Boolean
        ) : this(
            delegate, repositoryId, repositoryDisabler, Integer.getInteger(
                MAX_TENTATIVES_BEFORE_DISABLING, 3
            ), Integer.getInteger(INITIAL_BACKOFF_MS, 1000), repositoryName, continueOnConnectionFailure
        )

        init {
            assert(maxTentativesCount > 0) { "Max tentatives must be > 0" }
            assert(initialBackOff >= 0) { "Initial backoff must be >= 0" }
            this.delegate = delegate
            this.repositoryId = repositoryId
            this.repositoryDisabler = repositoryDisabler
            this.maxTentativesCount = maxTentativesCount
            this.initialBackOff = initialBackOff
            this.continueOnConnectionFailure = continueOnConnectionFailure
        }

        override fun toString(): String {
            return "error handling > " + delegate.toString()
        }

        override fun listModuleVersions(selector: ModuleComponentSelector, overrideMetadata: ComponentOverrideMetadata?, result: BuildableModuleVersionListingResolveResult?) {
            TODO(
                """
                |Cannot convert element
                |With text:
                |@org.jetbrains.annotations.Nullable ModuleVersionResolveException, BuildableModuleVersionListingResolveResult><@org.jetbrains.annotations.Nullable ModuleVersionResolveException, BuildableModuleVersionListingResolveResult>performOperationWithRetries(result,
                |                () -> delegate.listModuleVersions(selector, overrideMetadata, result),
                |                cause -> new ModuleVersionResolveException(selector, () -> buildDisabledRepositoryErrorMessage(repositoryName)),
                |                cause -> new ModuleVersionResolveException(selector, () -> "Failed to list versions for " + selector.getGroup() + ":" + selector.getModule() + ".", cause)
                |            );
                """.trimMargin()
            )
        }

        override fun resolveComponentMetaData(
            moduleComponentIdentifier: ModuleComponentIdentifier,
            requestMetaData: ComponentOverrideMetadata?,
            result: BuildableModuleComponentMetaDataResolveResult<ExternalModuleComponentGraphResolveState?>?
        ) {
            TODO(
                """
                |Cannot convert element
                |With text:
                |@org.jetbrains.annotations.Nullable ModuleVersionResolveException, BuildableModuleComponentMetaDataResolveResult<ExternalModuleComponentGraphResolveState>> <@org.jetbrains.annotations.Nullable ModuleVersionResolveException, BuildableModuleComponentMetaDataResolveResult<ExternalModuleComponentGraphResolveState>>performOperationWithRetries(result,
                |                () -> delegate.resolveComponentMetaData(moduleComponentIdentifier, requestMetaData, result),
                |                cause -> new ModuleVersionResolveException(moduleComponentIdentifier, () -> buildDisabledRepositoryErrorMessage(repositoryName), cause),
                |                cause -> new ModuleVersionResolveException(moduleComponentIdentifier, cause)
                |            );
                """.trimMargin()
            )
        }

        override fun resolveArtifactsWithType(component: ComponentArtifactResolveMetadata, artifactType: ArtifactType?, result: BuildableArtifactSetResolveResult?) {
            TODO(
                """
                |Cannot convert element
                |With text:
                |@org.jetbrains.annotations.Nullable ArtifactResolveException, BuildableArtifactSetResolveResult><@org.jetbrains.annotations.Nullable ArtifactResolveException, BuildableArtifactSetResolveResult>performOperationWithRetries(result,
                |                () -> delegate.resolveArtifactsWithType(component, artifactType, result),
                |                cause -> new ArtifactResolveException(component.getId(), buildDisabledRepositoryErrorMessage(repositoryName), cause),
                |                cause -> new ArtifactResolveException(component.getId(), cause)
                |            );
                """.trimMargin()
            )
        }

        override fun resolveArtifact(artifact: ComponentArtifactMetadata, moduleSources: ModuleSources?, result: BuildableArtifactFileResolveResult) {
            TODO(
                """
                |Cannot convert element
                |With text:
                |@org.jetbrains.annotations.Nullable ArtifactResolveException, BuildableArtifactFileResolveResult><@org.jetbrains.annotations.Nullable ArtifactResolveException, BuildableArtifactFileResolveResult>performOperationWithRetries(result,
                |                () -> {
                |                    delegate.resolveArtifact(artifact, moduleSources, result);
                |                    if (result.hasResult()) {
                |                        ArtifactResolveException failure = result.getFailure();
                |                        if (!(failure instanceof ArtifactNotFoundException)) {
                |                            return failure;
                |                        }
                |                    }
                |                    return null;
                |                },
                |                cause -> new ArtifactResolveException(artifact.getId(), buildDisabledRepositoryErrorMessage(repositoryName), cause),
                |                cause -> new ArtifactResolveException(artifact.getId(), cause));
                """.trimMargin()
            )
        }

        fun <E : Throwable?, R : ErroringResolveResult<E?>?> performOperationWithRetries(
            result: R?,
            operation: Callable<E?>,
            onDisabled: Transformer<E?, Throwable?>,
            onError: Transformer<E?, Throwable?>
        ) {
            if ();
            TODO(
                """
                |Cannot convert element
                |With text:
                |E, R><E, R>checkToHandleDisabledRepository(result, onDisabled)
                """.trimMargin()
            )
            run {
                return
            }
            TODO(
                """
                |Cannot convert element
                |With text:
                |E, R><E, R>tryResolveAndMaybeDisable(result, operation, onError);
                """.trimMargin()
            )
        }

        fun <E : Throwable?, R : ErroringResolveResult<E?>?> performOperationWithRetries(
            result: R?,
            operation: Runnable,
            onDisabled: Transformer<E?, Throwable?>,
            onError: Transformer<E?, Throwable?>
        ) {
            if ();
            TODO(
                """
                |Cannot convert element
                |With text:
                |E, R><E, R>checkToHandleDisabledRepository(result, onDisabled)
                """.trimMargin()
            )
            run {
                return
            }
            TODO(
                """
                |Cannot convert element
                |With text:
                |E, R><E, R>tryResolveAndMaybeDisable(result, operation, onError);
                """.trimMargin()
            )
        }

        fun <E : Throwable?, R : ErroringResolveResult<E?>?> checkToHandleDisabledRepository(result: R?, onDisabled: Transformer<E?, Throwable?>): Boolean {
            // Artifact can only be resolved from the same repository as the metadata
            // So continue does not make sense here
            val disabledIsFatal = !continueOnConnectionFailure || result is BuildableArtifactFileResolveResult

            if (disabledIsFatal && repositoryDisabler.isDisabled(repositoryId)) {
                val reason = repositoryDisabler.getDisabledReason(repositoryId)!!.get()
                val failure = onDisabled.transform(reason)
                result!!.failed(failure)
                return true
            }
            return false
        }

        fun <E : Throwable?, R : ErroringResolveResult<E?>?> tryResolveAndMaybeDisable(
            result: R?,
            operation: Runnable,
            onError: Transformer<E?, Throwable?>
        ) {
            TODO(
                """
                |Cannot convert element
                |With text:
                |E, R><E, R>tryResolveAndMaybeDisable(result, () -> {
                |                operation.run();
                |                return null;
                |            }, onError);
                """.trimMargin()
            )
        }

        fun <E : Throwable?, R : ErroringResolveResult<E?>?> tryResolveAndMaybeDisable(
            result: R?,
            operation: Callable<E?>,
            onError: Transformer<E?, Throwable?>
        ) {
            var retries = 0
            var backoff = initialBackOff

            while (retries < maxTentativesCount) {
                retries++
                var failure: E?
                var unexpectedFailure: Throwable? = null
                try {
                    failure = operation.call()
                    if (failure == null) {
                        if (retries > 1) {
                            LOGGER!!.debug("Successfully fetched external resource after {} retries", retries - 1)
                        }
                        return
                    }
                } catch (throwable: Exception) {
                    unexpectedFailure = throwable
                    failure = onError.transform(throwable)
                }
                val transientNetworkingIssue = isLikelyTransientNetworkingIssue<E?>(failure)
                val doNotRetry = isLikelyPermanentNetworkIssue<E?>(failure) || !transientNetworkingIssue
                if (doNotRetry || retries == maxTentativesCount) {
                    if (unexpectedFailure != null) {
                        // TODO Think about disabling on failed retries but also have an option to allow to continue despite disabled repositories
                        // Use case: I have my internal Central mirror in project setup, but outside of the company network it is not reachable. And thus I want to continue build with just Maven Central.
                        repositoryDisabler.tryDisableRepository(repositoryId, failure!!, transientNetworkingIssue && retries == maxTentativesCount)
                    }
                    result!!.failed(failure)
                    break
                } else {
                    LOGGER!!.debug("Error while accessing remote repository {}. Waiting {}ms before next retry. {} retries left", repositoryName, backoff, maxTentativesCount - retries, failure)
                    try {
                        Thread.sleep(backoff.toLong())
                        backoff *= 2
                    } catch (e: InterruptedException) {
                        throw throwAsUncheckedException(e)
                    }
                }
            }
        }

        override fun estimateMetadataFetchingCost(moduleComponentIdentifier: ModuleComponentIdentifier?): MetadataFetchingCost? {
            return delegate.estimateMetadataFetchingCost(moduleComponentIdentifier)
        }

        companion object {
            private val LOGGER = getLogger(ErrorHandlingModuleComponentRepositoryAccess::class.java)
            private const val MAX_TENTATIVES_BEFORE_DISABLING = "org.gradle.internal.repository.max.tentatives"
            private const val INITIAL_BACKOFF_MS = "org.gradle.internal.repository.initial.backoff"

            private fun buildDisabledRepositoryErrorMessage(repositoryName: String?): String {
                return String.format("Repository %s is disabled due to earlier error below:", repositoryName)
            }
        }
    }
}
