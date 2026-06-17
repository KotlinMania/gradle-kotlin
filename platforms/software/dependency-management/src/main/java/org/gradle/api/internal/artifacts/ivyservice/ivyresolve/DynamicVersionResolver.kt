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

import org.gradle.api.Action
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.artifacts.ComponentMetadataProcessorFactory
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.ivyservice.CacheExpirationControl
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.Version
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier.Companion.newId
import org.gradle.internal.component.external.model.ExternalModuleComponentGraphResolveState
import org.gradle.internal.component.model.ComponentOverrideMetadata
import org.gradle.internal.resolve.ModuleVersionNotFoundException
import org.gradle.internal.resolve.ModuleVersionResolveException
import org.gradle.internal.resolve.RejectedByAttributesVersion
import org.gradle.internal.resolve.RejectedByRuleVersion
import org.gradle.internal.resolve.RejectedBySelectorVersion
import org.gradle.internal.resolve.RejectedVersion
import org.gradle.internal.resolve.caching.ComponentMetadataSupplierRuleExecutor
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult
import org.gradle.internal.resolve.result.BuildableModuleVersionListingResolveResult
import org.gradle.internal.resolve.result.ComponentSelectionContext
import org.gradle.internal.resolve.result.DefaultBuildableModuleComponentMetaDataResolveResult
import org.gradle.internal.resolve.result.DefaultBuildableModuleVersionListingResolveResult
import org.gradle.internal.resolve.result.ResourceAwareResolveResult
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.LinkedList

class DynamicVersionResolver(
    private val versionedComponentChooser: VersionedComponentChooser,
    private val versionParser: VersionParser,
    private val attributesFactory: AttributesFactory,
    private val componentMetadataProcessor: ComponentMetadataProcessorFactory?,
    private val componentMetadataSupplierRuleExecutor: ComponentMetadataSupplierRuleExecutor?,
    private val cacheExpirationControl: CacheExpirationControl?
) {
    private val repositories: MutableList<ModuleComponentRepository<ExternalModuleComponentGraphResolveState?>> = ArrayList<ModuleComponentRepository<ExternalModuleComponentGraphResolveState?>>()
    private val repositoryNames: MutableList<String?> = ArrayList<String?>()

    fun add(repository: ModuleComponentRepository<ExternalModuleComponentGraphResolveState?>) {
        repositories.add(repository)
        repositoryNames.add(repository.getName())
    }

    fun resolve(
        requested: ModuleComponentSelector,
        overrideMetadata: ComponentOverrideMetadata?,
        versionSelector: VersionSelector,
        rejectedVersionSelector: VersionSelector?,
        consumerAttributes: ImmutableAttributes?,
        result: BuildableComponentIdResolveResult
    ) {
        LOGGER.debug("Attempting to resolve version for {} using repositories {}", requested, repositoryNames)
        val errors = RepositoryFailureCollector()

        val resolveStates: MutableList<RepositoryResolveState> = ArrayList<RepositoryResolveState>(repositories.size)
        for (repository in repositories) {
            resolveStates.add(
                DynamicVersionResolver.RepositoryResolveState(
                    versionedComponentChooser,
                    requested,
                    overrideMetadata,
                    repository,
                    versionSelector,
                    rejectedVersionSelector!!,
                    versionParser,
                    consumerAttributes,
                    attributesFactory,
                    componentMetadataProcessor,
                    componentMetadataSupplierRuleExecutor,
                    cacheExpirationControl
                )
            )
        }

        val latestResolved = findLatestModule(resolveStates, errors)
        if (latestResolved != null) {
            LOGGER.debug("Using {} from {}", latestResolved.component.getId(), latestResolved.repository)
            for (error in errors.failures) {
                LOGGER.debug("Discarding resolve failure.", error)
            }

            found(result, resolveStates, latestResolved)
            return
        }
        if (!errors.failures.isEmpty()) {
            result.failed(ModuleVersionResolveException(requested, errors.failures))
        } else {
            notFound(result, requested, resolveStates)
        }
    }

    private fun found(result: BuildableComponentIdResolveResult, resolveStates: MutableList<RepositoryResolveState>, latestResolved: RepositoryChainModuleResolution) {
        for (resolveState in resolveStates) {
            resolveState.registerAttempts(result)
        }
        result.resolved(latestResolved.component, ModuleComponentGraphSpecificResolveState(latestResolved.repository.getName()))
    }

    private fun notFound(result: BuildableComponentIdResolveResult, requested: ModuleComponentSelector, resolveStates: MutableList<RepositoryResolveState>) {
        for (resolveState in resolveStates) {
            resolveState.applyTo(result)
        }
        if (result.isRejected) {
            // We have a matching component id that was rejected. These are handled later in the resolution process
            // (after conflict resolution), so it is not a failure at this stage.
            return
        }
        result.failed(ModuleVersionNotFoundException(requested, result.attempted, result.unmatchedVersions, result.rejectedVersions))
    }

    private fun findLatestModule(resolveStates: MutableList<RepositoryResolveState>, failures: RepositoryFailureCollector): RepositoryChainModuleResolution? {
        val queue = LinkedList<RepositoryResolveState>(resolveStates)

        val missing = LinkedList<RepositoryResolveState?>()

        // A first pass to do local resolves only
        val best = findLatestModule(queue, failures, missing)
        if (failures.hasFatalError()) {
            return null
        }
        if (best != null) {
            return best
        }

        // Nothing found - do a second pass
        queue.addAll(missing)
        missing.clear()
        return findLatestModule(queue, failures, missing)
    }

    //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
    private fun findLatestModule(
        queue: LinkedList<RepositoryResolveState>,
        failures: RepositoryFailureCollector,
        missing: MutableCollection<RepositoryResolveState?>
    ): RepositoryChainModuleResolution? {
        var best: RepositoryChainModuleResolution? = null
        while (!queue.isEmpty()) {
            val request = queue.removeFirst()
            try {
                request.resolve()
            } catch (t: Exception) {
                handleFailure(queue, request, failures, t)
                continue
            }
            when (request.resolvedVersionMetadata.state) {
                BuildableModuleComponentMetaDataResolveResult.State.Failed -> {
                    val failure = request.resolvedVersionMetadata.getFailure()
                    checkNotNull(failure) // Failure cannot be null in Failed state
                    handleFailure(queue, request, failures, failure)
                }

                Missing, Unknown ->                     // Queue this up for checking again later
                    // This is done because we're checking what we have locally in cache, and there may be nothing
                    // so we're queuing it back so that the next time we check in remote access.
                    if (request.canMakeFurtherAttempts()) {
                        missing.add(request)
                    }

                BuildableModuleComponentMetaDataResolveResult.State.Resolved -> {
                    val moduleResolution = RepositoryChainModuleResolution(request.repository, request.resolvedVersionMetadata.metaData!!)
                    best = chooseBest(best, moduleResolution)
                }

                else -> throw IllegalStateException("Unexpected state for resolution: " + request.resolvedVersionMetadata.state)
            }
        }

        return best
    }

    private fun chooseBest(one: RepositoryChainModuleResolution?, two: RepositoryChainModuleResolution?): RepositoryChainModuleResolution? {
        if (one == null || two == null) {
            return if (two == null) one else two
        }
        return if (versionedComponentChooser.selectNewestComponent(one.component.getMetadata()!!, two.component.getMetadata()!!) === one.component.getMetadata()) one else two
    }

    private class AttemptCollector : Action<ResourceAwareResolveResult?> {
        private val attempts: MutableList<String?> = ArrayList<String?>()

        override fun execute(resourceAwareResolveResult: ResourceAwareResolveResult) {
            attempts.addAll(resourceAwareResolveResult.attempted!!)
        }

        fun applyTo(result: ResourceAwareResolveResult) {
            for (url in attempts) {
                result.attempted(url)
            }
        }
    }

    /**
     * This class contains state used to resolve a component from a specific repository. It can be used in multiple passes,
     * (local access, remote access), and will be used for 2 different steps:
     *
     * 1. selecting a version, thanks to the versioned component chooser, for a specific version selector
     * 2. once the selection is done, fetch metadata for this component
     */
    private class RepositoryResolveState(
        private val versionedComponentChooser: VersionedComponentChooser,
        private val selector: ModuleComponentSelector,
        private val overrideMetadata: ComponentOverrideMetadata?,
        private val repository: ModuleComponentRepository<ExternalModuleComponentGraphResolveState?>,
        private val versionSelector: VersionSelector,
        private val rejectedVersionSelector: VersionSelector,
        private val versionParser: VersionParser,
        consumerAttributes: ImmutableAttributes?,
        attributesFactory: AttributesFactory,
        private val componentMetadataProcessorFactory: ComponentMetadataProcessorFactory?,
        metadataSupplierRuleExecutor: ComponentMetadataSupplierRuleExecutor?,
        cacheExpirationControl: CacheExpirationControl?
    ) : ComponentSelectionContext {
        private val resolvedVersionMetadata: BuildableModuleComponentMetaDataResolveResult<ExternalModuleComponentGraphResolveState?> =
            DefaultBuildableModuleComponentMetaDataResolveResult<ExternalModuleComponentGraphResolveState?>()
        private val candidateComponents: MutableMap<String?, CandidateResult> = LinkedHashMap<String?, CandidateResult>()
        private val unmatchedVersions: MutableSet<String?> = LinkedHashSet<String?>()
        private val rejectedVersions: MutableSet<RejectedVersion?> = LinkedHashSet<RejectedVersion?>()
        private val versionListingResult: VersionListResult
        private val attemptCollector: AttemptCollector
        private val consumerAttributes: ImmutableAttributes
        private val attributesFactory: AttributesFactory?
        private val metadataSupplierRuleExecutor: ComponentMetadataSupplierRuleExecutor?
        private val cacheExpirationControl: CacheExpirationControl?
        private var firstRejected: ModuleComponentIdentifier? = null

        init {
            this.attributesFactory = attributesFactory
            this.metadataSupplierRuleExecutor = metadataSupplierRuleExecutor
            this.cacheExpirationControl = cacheExpirationControl
            this.attemptCollector = AttemptCollector()
            this.consumerAttributes = buildAttributes(consumerAttributes, attributesFactory)
            this.versionListingResult = VersionListResult(selector, overrideMetadata, repository)
        }

        fun buildAttributes(consumerAttributes: ImmutableAttributes?, attributesFactory: AttributesFactory): ImmutableAttributes {
            val dependencyAttributes = (selector.getAttributes() as AttributeContainerInternal).asImmutable()
            return attributesFactory.concat(consumerAttributes, dependencyAttributes)
        }

        fun canMakeFurtherAttempts(): Boolean {
            return versionListingResult.canMakeFurtherAttempts()
        }

        fun resolve() {
            versionListingResult.resolve()
            when (versionListingResult.result.getState()) {
                BuildableModuleVersionListingResolveResult.State.Failed -> resolvedVersionMetadata.failed(versionListingResult.result.getFailure())
                BuildableModuleVersionListingResolveResult.State.Listed -> selectMatchingVersionAndResolve()
                BuildableModuleVersionListingResolveResult.State.Unknown -> {}
                else -> throw IllegalStateException("Unexpected state for version list result.")
            }
        }

        fun selectMatchingVersionAndResolve() {
            // TODO - reuse metaData if it was already fetched to select the component from the version list
            versionedComponentChooser.selectNewestMatchingComponent(candidates(), this, versionSelector, rejectedVersionSelector, consumerAttributes)
        }

        override fun matches(moduleComponentIdentifier: ModuleComponentIdentifier) {
            val version = moduleComponentIdentifier.getVersion()
            val candidateResult: CandidateResult = candidateComponents.get(version)!!
            candidateResult.tryResolveMetadata(resolvedVersionMetadata)
        }

        override fun failed(failure: ModuleVersionResolveException?) {
            resolvedVersionMetadata.failed(failure)
        }

        override fun noMatchFound() {
            resolvedVersionMetadata.missing()
        }

        override fun notMatched(id: ModuleComponentIdentifier, requestedVersionMatcher: VersionSelector?) {
            unmatchedVersions.add(id.getVersion())
        }

        override fun rejectedByRule(id: RejectedByRuleVersion?) {
            rejectedVersions.add(id)
        }

        override fun doesNotMatchConsumerAttributes(rejectedVersion: RejectedByAttributesVersion?) {
            rejectedVersions.add(rejectedVersion)
        }

        val contentFilter: Action<in ArtifactResolutionDetails?>?
            get() {
                if (repository is FilteredModuleComponentRepository) {
                    return repository.getFilterAction()
                }
                return null
            }

        override fun rejectedBySelector(id: ModuleComponentIdentifier?, versionSelector: VersionSelector?) {
            if (firstRejected == null) {
                firstRejected = id
            }
            rejectedVersions.add(RejectedBySelectorVersion(id, versionSelector))
        }

        fun candidates(): MutableList<CandidateResult?> {
            val candidates: MutableList<CandidateResult?> = ArrayList<CandidateResult?>()
            for (version in versionListingResult.result.getVersions()!!) {
                var candidateResult = candidateComponents.get(version)
                if (candidateResult == null) {
                    candidateResult = DynamicVersionResolver.CandidateResult(
                        selector,
                        overrideMetadata,
                        version!!,
                        repository,
                        attemptCollector,
                        versionParser,
                        componentMetadataProcessorFactory,
                        attributesFactory,
                        metadataSupplierRuleExecutor,
                        cacheExpirationControl
                    )
                    candidateComponents.put(version, candidateResult)
                }
                candidates.add(candidateResult)
            }
            return candidates
        }

        fun applyTo(target: BuildableComponentIdResolveResult) {
            registerAttempts(target)

            if (firstRejected != null) {
                target.rejected(firstRejected, DefaultModuleVersionIdentifier.newId(firstRejected!!))
            }
        }

        fun registerAttempts(target: BuildableComponentIdResolveResult) {
            versionListingResult.applyTo(target)
            attemptCollector.applyTo(target)
            target.unmatched(unmatchedVersions)
            target.rejections(rejectedVersions)
        }

        val isContinueOnConnectionFailure: Boolean
            get() = repository.isContinueOnConnectionFailure()

        val isRepositoryDisabled: Boolean
            get() = repository.isRepositoryDisabled()
    }

    private class CandidateResult(
        selector: ModuleComponentSelector,
        private val overrideMetadata: ComponentOverrideMetadata?,
        version: String,
        private val repository: ModuleComponentRepository<ExternalModuleComponentGraphResolveState?>,
        private val attemptCollector: AttemptCollector,
        versionParser: VersionParser,
        val componentMetadataProcessorFactory: ComponentMetadataProcessorFactory?,
        val attributesFactory: AttributesFactory?,
        val componentMetadataSupplierExecutor: ComponentMetadataSupplierRuleExecutor?,
        val cacheExpirationControl: CacheExpirationControl?
    ) : ModuleComponentResolveState {
        val id: ModuleComponentIdentifier
        val version: Version?
        private var searchedLocally = false
        private var searchedRemotely = false
        private val result = DefaultBuildableModuleComponentMetaDataResolveResult<ExternalModuleComponentGraphResolveState?>()

        init {
            this.version = versionParser.transform(version)
            this.id = newId(selector.getModuleIdentifier(), version)
        }

        override fun resolve(): BuildableModuleComponentMetaDataResolveResult<ExternalModuleComponentGraphResolveState?> {
            if (!searchedLocally) {
                searchedLocally = true
                process(repository.getLocalAccess())
                if (result.hasResult() && result.isAuthoritative()) {
                    // Authoritative result means don't do remote search
                    searchedRemotely = true
                }
            }
            if (result.getState() == BuildableModuleComponentMetaDataResolveResult.State.Resolved || result.getState() == BuildableModuleComponentMetaDataResolveResult.State.Failed) {
                return result
            }
            if (!searchedRemotely) {
                searchedRemotely = true
                process(repository.getRemoteAccess())
            }
            return result
        }

        val componentMetadataSupplier: InstantiatingAction<ComponentMetadataSupplierDetails?>?
            get() = repository.getComponentMetadataSupplier()

        fun process(access: ModuleComponentRepositoryAccess<ExternalModuleComponentGraphResolveState?>) {
            access.resolveComponentMetaData(this.id, overrideMetadata, result)
            attemptCollector.execute(result)
        }

        /**
         * Once a version has been selected, this tries to resolve metadata for this specific version. If it can it
         * will copy the result to the target builder
         *
         * @param target where to put metadata
         */
        fun tryResolveMetadata(target: BuildableModuleComponentMetaDataResolveResult<ExternalModuleComponentGraphResolveState?>) {
            val result: BuildableModuleComponentMetaDataResolveResult<ExternalModuleComponentGraphResolveState?> = resolve()
            when (result.state) {
                BuildableModuleComponentMetaDataResolveResult.State.Resolved -> {
                    target.resolved(result.metaData)
                    return
                }

                Missing -> {
                    result.applyTo(target)
                    target.missing()
                    return
                }

                BuildableModuleComponentMetaDataResolveResult.State.Failed -> {
                    target.failed(result.getFailure())
                    return
                }

                Unknown -> return
                else -> throw IllegalStateException()
            }
        }
    }

    private class VersionListResult(private val selector: ModuleComponentSelector?, private val overrideMetadata: ComponentOverrideMetadata?, private val repository: ModuleComponentRepository<*>) {
        private val result = DefaultBuildableModuleVersionListingResolveResult()

        private var searchedLocally = false
        private var searchedRemotely = false

        fun resolve() {
            if (!searchedLocally) {
                searchedLocally = true
                process(selector, overrideMetadata, repository.getLocalAccess())
                if (result.hasResult()) {
                    if (result.isAuthoritative()) {
                        // Authoritative result - don't need to try remote
                        searchedRemotely = true
                    }
                    return
                }
                // Otherwise, try remotely
            }
            if (!searchedRemotely) {
                searchedRemotely = true
                process(selector, overrideMetadata, repository.getRemoteAccess())
            }

            // Otherwise, just reuse previous result
        }

        fun canMakeFurtherAttempts(): Boolean {
            return !searchedRemotely
        }

        fun applyTo(target: ResourceAwareResolveResult) {
            result.applyTo(target)
        }

        fun process(selector: ModuleComponentSelector?, overrideMetadata: ComponentOverrideMetadata?, moduleAccess: ModuleComponentRepositoryAccess<*>) {
            moduleAccess.listModuleVersions(selector, overrideMetadata, result)
        }
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(DynamicVersionResolver::class.java)

        private fun handleFailure(queue: MutableList<RepositoryResolveState>, request: RepositoryResolveState, failures: RepositoryFailureCollector, t: Exception) {
            failures.addFailure(t)
            if (request.isRepositoryDisabled && !request.isContinueOnConnectionFailure) {
                // Clear the queue only if repo is now disabled, and we can't continue with it disabled
                queue.clear()
                failures.markFatalError()
            }
        }
    }
}
