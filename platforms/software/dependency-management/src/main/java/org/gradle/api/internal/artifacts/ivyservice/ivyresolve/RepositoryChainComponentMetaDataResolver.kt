/*
 * Copyright 2011 the original author or authors.
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

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.internal.DisplayName
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.component.external.model.ExternalModuleComponentGraphResolveState
import org.gradle.internal.component.model.ComponentOverrideMetadata
import org.gradle.internal.model.CalculatedValue
import org.gradle.internal.model.CalculatedValueFactory
import org.gradle.internal.resolve.ModuleVersionResolveException
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver
import org.gradle.internal.resolve.result.BuildableComponentResolveResult
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult
import org.gradle.internal.resolve.result.DefaultBuildableComponentResolveResult
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.LinkedList
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.function.Supplier

class RepositoryChainComponentMetaDataResolver(private val versionedComponentChooser: VersionedComponentChooser, private val calculatedValueFactory: CalculatedValueFactory) :
    ComponentMetaDataResolver {
    private val repositories: MutableList<ModuleComponentRepository<ExternalModuleComponentGraphResolveState>> = ArrayList<ModuleComponentRepository<ExternalModuleComponentGraphResolveState>>()
    private val repositoryNames: MutableList<String> = ArrayList<String>()
    private val metadataValueContainerCache: Cache<ModuleComponentIdentifier, CalculatedValue<BuildableComponentResolveResult>>

    init {
        this.metadataValueContainerCache = CacheBuilder.newBuilder().weakValues().build<ModuleComponentIdentifier, CalculatedValue<BuildableComponentResolveResult>>()
    }

    fun add(repository: ModuleComponentRepository<ExternalModuleComponentGraphResolveState>) {
        repositories.add(repository)
        repositoryNames.add(repository.name)
    }

    override fun resolve(identifier: ComponentIdentifier, componentOverrideMetadata: ComponentOverrideMetadata, result: BuildableComponentResolveResult) {
        if (identifier !is ModuleComponentIdentifier) {
            throw UnsupportedOperationException("Can resolve meta-data for module components only.")
        }

        try {
            val metadataValueContainer =
                metadataValueContainerCache.get(identifier, Callable { createValueContainerFor(identifier, componentOverrideMetadata) })
            metadataValueContainer.finalizeIfNotAlready()
            metadataValueContainer.get().applyTo(result)
        } catch (e: ExecutionException) {
            throw throwAsUncheckedException(e)
        }
    }

    private fun createValueContainerFor(identifier: ComponentIdentifier, componentOverrideMetadata: ComponentOverrideMetadata): CalculatedValue<BuildableComponentResolveResult> {
        return calculatedValueFactory.create<BuildableComponentResolveResult>(toDisplayName(identifier), Supplier { resolveModule(identifier as ModuleComponentIdentifier, componentOverrideMetadata) })
    }

    override fun isFetchingMetadataCheap(identifier: ComponentIdentifier): Boolean {
        if (identifier is ModuleComponentIdentifier) {
            for (repository in repositories) {
                val localAccess = repository.localAccess
                val fetchingCost = localAccess.estimateMetadataFetchingCost(identifier)
                if (fetchingCost.isFast) {
                    return true
                } else if (fetchingCost.isExpensive) {
                    return false
                }
            }
        }
        return true
    }

    private fun resolveModule(identifier: ModuleComponentIdentifier, componentOverrideMetadata: ComponentOverrideMetadata): BuildableComponentResolveResult {
        LOGGER.debug("Attempting to resolve component for {} using repositories {}", identifier, repositoryNames)

        val errors = RepositoryFailureCollector()
        val result: BuildableComponentResolveResult = DefaultBuildableComponentResolveResult()

        val resolveStates: MutableList<ComponentMetaDataResolveState> = ArrayList<ComponentMetaDataResolveState>()
        for (repository in repositories) {
            resolveStates.add(ComponentMetaDataResolveState(identifier, componentOverrideMetadata, repository, versionedComponentChooser))
        }

        val latestResolved = findBestMatch(resolveStates, errors)
        if (latestResolved != null) {
            LOGGER.debug("Using {} from {}", latestResolved.component.getId(), latestResolved.repository)
            for (error in errors.getFailures()) {
                LOGGER.debug("Discarding resolve failure.", error)
            }

            val repositoryName = latestResolved.repository.name
            result.resolved(latestResolved.component, ModuleComponentGraphSpecificResolveState(repositoryName))
            return result
        }
        if (!errors.getFailures().isEmpty()) {
            result.failed(ModuleVersionResolveException(identifier, errors.getFailures()))
        } else {
            for (resolveState in resolveStates) {
                resolveState.applyTo(result)
            }
            result.notFound(identifier)
        }

        return result
    }

    private fun findBestMatch(resolveStates: MutableList<ComponentMetaDataResolveState>, failures: RepositoryFailureCollector): RepositoryChainModuleResolution? {
        val queue = LinkedList<ComponentMetaDataResolveState>(resolveStates)

        val missing = LinkedList<ComponentMetaDataResolveState>()

        // A first pass to do local resolves only
        val best = findBestMatch(queue, failures, missing)
        if (failures.hasFatalError()) {
            return null
        }
        if (best != null) {
            return best
        }

        // Nothing found locally - try a remote search for all resolve states that were not yet searched remotely
        queue.addAll(missing)
        missing.clear()
        return findBestMatch(queue, failures, missing)
    }

    //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
    private fun findBestMatch(
        queue: LinkedList<ComponentMetaDataResolveState>,
        failures: RepositoryFailureCollector,
        missing: MutableCollection<ComponentMetaDataResolveState>
    ): RepositoryChainModuleResolution? {
        var best: RepositoryChainModuleResolution? = null
        while (!queue.isEmpty()) {
            val request = queue.removeFirst()
            val metaDataResolveResult: BuildableModuleComponentMetaDataResolveResult<ExternalModuleComponentGraphResolveState?>?
            metaDataResolveResult = request.resolve()
            when (metaDataResolveResult.state) {
                Failed -> {
                    val failure = metaDataResolveResult.getFailure()
                    checkNotNull(failure) // Failure cannot be null in Failed state
                    failures.addFailure(failure)
                    if (request.isRepositoryDisabled() && !request.isContinueOnConnectionFailure()) {
                        // Clear the queue only if repo is now disabled, and we can't continue with it disabled
                        queue.clear()
                        failures.markFatalError()
                    }
                }

                Missing ->                     // Queue this up for checking again later
                    if (request.canMakeFurtherAttempts()) {
                        missing.add(request)
                    }

                Resolved -> {
                    val moduleResolution = RepositoryChainModuleResolution(request.repository, metaDataResolveResult.metaData!!)
                    if (!metaDataResolveResult.metaData!!.getMetadata().isMissing()) {
                        return moduleResolution
                    }
                    best = if (best != null) best else moduleResolution
                }

                else -> throw IllegalStateException("Unexpected state for resolution: " + metaDataResolveResult.state)
            }
        }

        return best
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(RepositoryChainComponentMetaDataResolver::class.java)

        private fun toDisplayName(identifier: ComponentIdentifier): DisplayName {
            if (DisplayName::class.java.isAssignableFrom(identifier.javaClass)) {
                return identifier as DisplayName
            } else {
                return object : DisplayName {
                    override fun getDisplayName(): String {
                        return identifier.getDisplayName()
                    }

                    override fun getCapitalizedDisplayName(): String {
                        return getDisplayName()
                    }
                }
            }
        }
    }
}
