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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve

import org.gradle.api.artifacts.ComponentMetadataSupplierDetails
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.artifacts.ComponentMetadataProcessor
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.ivyservice.CacheExpirationControl
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleMetadataCache
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleRepositoryCaches
import org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts.ArtifactAtRepositoryKey
import org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts.ModuleArtifactCache
import org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts.ModuleArtifactsCache
import org.gradle.api.internal.artifacts.ivyservice.modulecache.dynamicversions.ModuleVersionsCache
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact
import org.gradle.api.internal.artifacts.repositories.resolver.MetadataFetchingCost
import org.gradle.api.internal.component.ArtifactType
import org.gradle.internal.action.InstantiatingAction
import org.gradle.internal.component.external.model.ExternalModuleComponentGraphResolveState
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata
import org.gradle.internal.component.external.model.ModuleComponentGraphResolveStateFactory
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata.withSources
import org.gradle.internal.component.model.ComponentArtifactMetadata
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata
import org.gradle.internal.component.model.ComponentOverrideMetadata
import org.gradle.internal.component.model.ImmutableModuleSources.Companion.of
import org.gradle.internal.component.model.ModuleSources
import org.gradle.internal.component.model.ModuleSources.withSources
import org.gradle.internal.component.model.MutableModuleSources
import org.gradle.internal.resolve.ArtifactNotFoundException
import org.gradle.internal.resolve.result.BuildableArtifactFileResolveResult
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult
import org.gradle.internal.resolve.result.BuildableModuleVersionListingResolveResult
import org.gradle.internal.resolve.result.DefaultBuildableModuleComponentMetaDataResolveResult
import org.gradle.util.internal.BuildCommencedTimeProvider
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Duration
import java.util.function.Function
import java.util.stream.Collectors

/**
 * A ModuleComponentRepository that loads and saves resolution results in the dependency resolution cache.
 *
 * The `LocateInCacheRepositoryAccess` provided by [.getLocalAccess] will attempt to handle any resolution request
 * directly from the cache, checking for cache expiry based on the `ResolutionStrategy` in operation.
 *
 * The `ResolveAndCacheRepositoryAccess` provided by [.getRemoteAccess] will first delegate any resolution request,
 * and then store the result in the dependency resolution cache.
 */
class CachingModuleComponentRepository(
    private val delegate: ModuleComponentRepository<ModuleComponentResolveMetadata?>,
    caches: ModuleRepositoryCaches,
    private val resolveStateFactory: ModuleComponentGraphResolveStateFactory,
    private val cacheExpirationControl: CacheExpirationControl,
    private val timeProvider: BuildCommencedTimeProvider,
    private val metadataProcessor: ComponentMetadataProcessor,
    private val listener: ChangingValueDependencyResolutionListener
) : ModuleComponentRepository<ExternalModuleComponentGraphResolveState?> {
    private val moduleVersionsCache: ModuleVersionsCache?
    private val moduleMetadataCache: ModuleMetadataCache?
    private val moduleArtifactsCache: ModuleArtifactsCache?
    private val moduleArtifactCache: ModuleArtifactCache?

    private val locateInCacheRepositoryAccess: LocateInCacheRepositoryAccess = CachingModuleComponentRepository.LocateInCacheRepositoryAccess()
    private val resolveAndCacheRepositoryAccess: ResolveAndCacheRepositoryAccess = CachingModuleComponentRepository.ResolveAndCacheRepositoryAccess()

    init {
        this.moduleMetadataCache = caches.moduleMetadataCache
        this.moduleVersionsCache = caches.moduleVersionsCache
        this.moduleArtifactsCache = caches.moduleArtifactsCache
        this.moduleArtifactCache = caches.moduleArtifactCache
    }

    override fun getId(): String? {
        return delegate.getId()
    }

    override fun getName(): String? {
        return delegate.getName()
    }

    override fun toString(): String {
        return delegate.toString()
    }

    override fun getLocalAccess(): ModuleComponentRepositoryAccess<ExternalModuleComponentGraphResolveState?> {
        return locateInCacheRepositoryAccess
    }

    override fun getRemoteAccess(): ModuleComponentRepositoryAccess<ExternalModuleComponentGraphResolveState?> {
        return resolveAndCacheRepositoryAccess
    }

    override fun getArtifactCache(): MutableMap<ComponentArtifactIdentifier?, ResolvableArtifact?>? {
        throw UnsupportedOperationException()
    }

    override fun getComponentMetadataSupplier(): InstantiatingAction<ComponentMetadataSupplierDetails?>? {
        return delegate.getComponentMetadataSupplier()
    }

    override fun isContinueOnConnectionFailure(): Boolean {
        return delegate.isContinueOnConnectionFailure()
    }

    override fun isRepositoryDisabled(): Boolean {
        return delegate.isRepositoryDisabled()
    }

    private inner class LocateInCacheRepositoryAccess : ModuleComponentRepositoryAccess<ExternalModuleComponentGraphResolveState?> {
        override fun toString(): String {
            return "cache lookup for " + delegate
        }

        override fun listModuleVersions(selector: ModuleComponentSelector, overrideMetadata: ComponentOverrideMetadata?, result: BuildableModuleVersionListingResolveResult) {
            // First try to determine the versions in-memory: don't use the cache in this case
            delegate.getLocalAccess().listModuleVersions(selector, overrideMetadata, result)
            if (result.hasResult()) {
                return
            }

            listModuleVersionsFromCache(selector, result)
        }

        fun listModuleVersionsFromCache(selector: ModuleComponentSelector, result: BuildableModuleVersionListingResolveResult) {
            val moduleId = selector.getModuleIdentifier()
            val cachedModuleVersionList = moduleVersionsCache!!.getCachedModuleResolution(delegate, moduleId)
            if (cachedModuleVersionList != null) {
                val versionList: MutableSet<String?> = cachedModuleVersionList.moduleVersions!!
                val versions: MutableSet<ModuleVersionIdentifier?> = versionList
                    .stream()
                    .map<Any?> { original: String? -> DefaultModuleVersionIdentifier.newId(moduleId, original!!) }
                    .collect(Collectors.toSet())
                val expiry = cacheExpirationControl.versionListExpiry(moduleId, versions, cachedModuleVersionList.age)
                if (expiry!!.isMustCheck) {
                    LOGGER.debug("Version listing in dynamic revision cache is expired: will perform fresh resolve of '{}' in '{}'", selector, delegate.getName())
                } else {
                    // When age == 0, verified since the start of this build, assume listing hasn't changed
                    val authoritative = cachedModuleVersionList.age.toMillis() === 0
                    result.listed(versionList)
                    result.isAuthoritative = authoritative
                    listener.onDynamicVersionSelection(selector, expiry, versions)
                }
            }
        }

        override fun resolveComponentMetaData(
            moduleComponentIdentifier: ModuleComponentIdentifier?,
            requestMetaData: ComponentOverrideMetadata,
            result: BuildableModuleComponentMetaDataResolveResult<ExternalModuleComponentGraphResolveState?>
        ) {
            // First try to determine the metadata in-memory: don't use the cache in this case
            val localResult = DefaultBuildableModuleComponentMetaDataResolveResult<ModuleComponentResolveMetadata?>()
            delegate.getLocalAccess().resolveComponentMetaData(moduleComponentIdentifier, requestMetaData, localResult)
            if (localResult.hasResult()) {
                localResult.applyTo<ExternalModuleComponentGraphResolveState?>(result, Function { metadata: ModuleComponentResolveMetadata? -> resolveStateFactory.stateFor(metadata) })
                return
            }


            resolveComponentMetaDataFromCache(moduleComponentIdentifier, requestMetaData, result)
        }

        fun resolveComponentMetaDataFromCache(
            moduleComponentIdentifier: ModuleComponentIdentifier?,
            requestMetaData: ComponentOverrideMetadata,
            result: BuildableModuleComponentMetaDataResolveResult<ExternalModuleComponentGraphResolveState?>
        ) {
            val cachedMetadata = moduleMetadataCache!!.getCachedModuleDescriptor(delegate, moduleComponentIdentifier)
            if (cachedMetadata == null) {
                return
            }
            if (cachedMetadata.isMissing) {
                if (cacheExpirationControl.missingModuleExpiry(moduleComponentIdentifier, cachedMetadata.age)!!.isMustCheck) {
                    LOGGER.debug("Cached meta-data for missing module is expired: will perform fresh resolve of '{}' in '{}'", moduleComponentIdentifier, delegate.getName())
                    return
                }
                LOGGER.debug("Detected non-existence of module '{}' in resolver cache '{}'", moduleComponentIdentifier, delegate.getName())
                result.missing()
                // When age == 0, verified since the start of this build, assume still missing
                result.isAuthoritative = cachedMetadata.age.toMillis() === 0
                return
            }
            val state = getProcessedMetadata(metadataProcessor.rulesHash, cachedMetadata)
            if (requestMetaData.isChanging() || state.getMetadata()!!.isChanging()) {
                val expiry = cacheExpirationControl.changingModuleExpiry(moduleComponentIdentifier, cachedMetadata.moduleVersion, cachedMetadata.age)
                if (expiry!!.isMustCheck) {
                    LOGGER.debug("Cached meta-data for changing module is expired: will perform fresh resolve of '{}' in '{}'", moduleComponentIdentifier, delegate.getName())
                    return
                }
                LOGGER.debug("Found cached version of changing module '{}' in '{}'", moduleComponentIdentifier, delegate.getName())
                listener.onChangingModuleResolve(moduleComponentIdentifier, expiry)
            } else {
                if (cacheExpirationControl.moduleExpiry(moduleComponentIdentifier, cachedMetadata.moduleVersion, cachedMetadata.age)!!.isMustCheck) {
                    LOGGER.debug("Cached meta-data for module must be refreshed: will perform fresh resolve of '{}' in '{}'", moduleComponentIdentifier, delegate.getName())
                    return
                }
            }

            LOGGER.debug("Using cached module metadata for module '{}' in '{}'", moduleComponentIdentifier, delegate.getName())
            result.resolved(state)
            // When age == 0, verified since the start of this build, assume the meta-data hasn't changed
            result.isAuthoritative = cachedMetadata.age.toMillis() === 0
        }

        fun getProcessedMetadata(key: Int, cachedMetadata: ModuleMetadataCache.CachedMetadata): ExternalModuleComponentGraphResolveState {
            var state = cachedMetadata.getProcessedMetadata(key)
            if (state == null) {
                var metadata = metadataProcessor.processMetadata(cachedMetadata.metadata)
                metadata = attachRepositorySource(metadata!!)
                state = resolveStateFactory.stateFor(metadata)
                // Save the processed metadata for next time.
                cachedMetadata.putProcessedMetadata(key, state)
            }
            return state
        }

        override fun resolveArtifactsWithType(component: ComponentArtifactResolveMetadata, artifactType: ArtifactType, result: BuildableArtifactSetResolveResult) {
            // First try to determine the artifacts in-memory (e.g using the metadata): don't use the cache in this case
            delegate.getLocalAccess().resolveArtifactsWithType(component, artifactType, result)
            if (result.hasResult()) {
                return
            }

            resolveModuleArtifactsFromCache(cacheKey(artifactType), component, result)
        }

        fun resolveModuleArtifactsFromCache(contextId: String?, component: ComponentArtifactResolveMetadata, result: BuildableArtifactSetResolveResult) {
            val cachedModuleArtifacts = moduleArtifactsCache!!.getCachedArtifacts(delegate, component.getId(), contextId)
            val sources = component.getSources()
            val cachingModuleSource: ModuleDescriptorHashModuleSource = Companion.findCachingModuleSource(sources!!)
            val moduleDescriptorHash = cachingModuleSource.descriptorHash

            if (cachedModuleArtifacts != null) {
                if (!cacheExpirationControl.moduleArtifactsExpiry(
                        component.getModuleVersionId(), null, java.time.Duration.ofMillis(cachedModuleArtifacts.ageMillis),
                        cachingModuleSource.isChangingModule, moduleDescriptorHash == cachedModuleArtifacts.descriptorHash
                    )!!.isMustCheck
                ) {
                    result.resolved(cachedModuleArtifacts.artifacts)
                    return
                }

                LOGGER.debug("Artifact listing has expired: will perform fresh resolve of '{}' for '{}' in '{}'", contextId, component.getModuleVersionId(), delegate.getName())
            }
        }

        override fun resolveArtifact(artifact: ComponentArtifactMetadata, moduleSources: ModuleSources, result: BuildableArtifactFileResolveResult) {
            // First try to resolve the artifact in-memory (e.g using the metadata): don't use the cache in this case
            delegate.getLocalAccess().resolveArtifact(artifact, moduleSources, result)
            if (result.hasResult()) {
                return
            }

            resolveArtifactFromCache(artifact, moduleSources, result)
        }

        override fun estimateMetadataFetchingCost(moduleComponentIdentifier: ModuleComponentIdentifier?): MetadataFetchingCost? {
            val cachedMetadata = moduleMetadataCache!!.getCachedModuleDescriptor(delegate, moduleComponentIdentifier)
            if (cachedMetadata == null) {
                return estimateCostViaRemoteAccess(moduleComponentIdentifier)
            }
            if (cachedMetadata.isMissing) {
                if (cacheExpirationControl.missingModuleExpiry(moduleComponentIdentifier, cachedMetadata.age)!!.isMustCheck) {
                    return estimateCostViaRemoteAccess(moduleComponentIdentifier)
                }
                return MetadataFetchingCost.CHEAP
            }
            val state = getProcessedMetadata(metadataProcessor.rulesHash, cachedMetadata)
            if (state.getMetadata()!!.isChanging()) {
                if (cacheExpirationControl.changingModuleExpiry(moduleComponentIdentifier, cachedMetadata.moduleVersion, cachedMetadata.age)!!.isMustCheck) {
                    return estimateCostViaRemoteAccess(moduleComponentIdentifier)
                }
            } else {
                if (cacheExpirationControl.moduleExpiry(moduleComponentIdentifier, cachedMetadata.moduleVersion, cachedMetadata.age)!!.isMustCheck) {
                    return estimateCostViaRemoteAccess(moduleComponentIdentifier)
                }
            }
            return MetadataFetchingCost.FAST
        }

        fun estimateCostViaRemoteAccess(moduleComponentIdentifier: ModuleComponentIdentifier?): MetadataFetchingCost? {
            return delegate.getRemoteAccess().estimateMetadataFetchingCost(moduleComponentIdentifier)
        }

        fun resolveArtifactFromCache(artifact: ComponentArtifactMetadata, moduleSources: ModuleSources, result: BuildableArtifactFileResolveResult) {
            val cached = moduleArtifactCache!!.lookup(artifactCacheKey(artifact.getId()!!))
            if (cached != null) {
                val moduleSource: ModuleDescriptorHashModuleSource = findCachingModuleSource(moduleSources)
                val descriptorHash = moduleSource.descriptorHash
                val age = Duration.ofMillis(timeProvider.getCurrentTime() - cached.cachedAt)
                val isChangingModule = moduleSource.isChangingModule
                val moduleComponentArtifactMetadata = artifact as ModuleComponentArtifactMetadata
                if (cached.isMissing) {
                    val expiry = cacheExpirationControl.artifactExpiry(moduleComponentArtifactMetadata, null, age, isChangingModule, descriptorHash == cached.descriptorHash)
                    if (!expiry!!.isMustCheck) {
                        LOGGER.debug("Detected non-existence of artifact '{}' in resolver cache", artifact)
                        for (location in cached.attemptedLocations()!!) {
                            result.attempted(location)
                        }
                        result.notFound(artifact.getId())
                    }
                } else {
                    val cachedArtifactFile: File? = cached.cachedFile
                    val expiry = cacheExpirationControl.artifactExpiry(moduleComponentArtifactMetadata, cachedArtifactFile, age, isChangingModule, descriptorHash == cached.descriptorHash)
                    if (!expiry!!.isMustCheck) {
                        LOGGER.debug("Found artifact '{}' in resolver cache: {}", artifact, cachedArtifactFile)
                        result.resolved(cachedArtifactFile)
                    }
                }
            }
        }
    }

    private inner class ResolveAndCacheRepositoryAccess : ModuleComponentRepositoryAccess<ExternalModuleComponentGraphResolveState?> {
        override fun toString(): String {
            return "cache > " + delegate.getRemoteAccess()
        }

        override fun listModuleVersions(selector: ModuleComponentSelector, overrideMetadata: ComponentOverrideMetadata?, result: BuildableModuleVersionListingResolveResult) {
            delegate.getRemoteAccess().listModuleVersions(selector, overrideMetadata, result)
            when (result.state) {
                Listed -> {
                    val moduleId = selector.getModuleIdentifier()
                    val versionList: MutableSet<String?> = result.versions!!
                    val versions: MutableSet<ModuleVersionIdentifier?> = versionList
                        .stream()
                        .map<Any?> { original: String? -> DefaultModuleVersionIdentifier.newId(moduleId, original!!) }
                        .collect(Collectors.toSet())
                    moduleVersionsCache!!.cacheModuleVersionList(delegate, moduleId, versionList)
                    listener.onDynamicVersionSelection(
                        selector,
                        cacheExpirationControl.versionListExpiry(moduleId, versions, Duration.ZERO),
                        versions
                    )
                }

                Failed -> {}
                else -> throw IllegalStateException("Unexpected state on listModuleVersions: " + result.state)
            }
        }

        override fun resolveComponentMetaData(
            moduleComponentIdentifier: ModuleComponentIdentifier?,
            requestMetaData: ComponentOverrideMetadata,
            result: BuildableModuleComponentMetaDataResolveResult<ExternalModuleComponentGraphResolveState?>
        ) {
            resolveComponentMetaDataAndCache(moduleComponentIdentifier, requestMetaData, result)
        }

        fun resolveComponentMetaDataAndCache(
            moduleComponentIdentifier: ModuleComponentIdentifier?,
            requestMetaData: ComponentOverrideMetadata,
            result: BuildableModuleComponentMetaDataResolveResult<ExternalModuleComponentGraphResolveState?>
        ) {
            val forced = requestMetaData.withChanging()
            val localResult = DefaultBuildableModuleComponentMetaDataResolveResult<ModuleComponentResolveMetadata?>()
            delegate.getRemoteAccess().resolveComponentMetaData(moduleComponentIdentifier, forced, localResult)
            when (localResult.getState()) {
                BuildableModuleComponentMetaDataResolveResult.State.Missing -> {
                    moduleMetadataCache!!.cacheMissing(delegate, moduleComponentIdentifier)
                    localResult.applyTo<ExternalModuleComponentGraphResolveState?>(result, Function { metadata: ModuleComponentResolveMetadata? ->
                        // Should not be called
                        throw IllegalStateException()
                    })
                }

                BuildableModuleComponentMetaDataResolveResult.State.Resolved -> {
                    val resolvedMetadata = localResult.getMetaData()
                    val cachedMetadata = moduleMetadataCache!!.cacheMetaData(delegate, moduleComponentIdentifier, resolvedMetadata)
                    // Starting here we're going to process the component metadata rules
                    // Therefore metadata can be mutated, and will _not_ be stored in the module metadata cache
                    // but will be in the _in memory_ cache
                    val processedMetadata = metadataProcessor.processMetadata(resolvedMetadata)
                    processedMetadata = attachRepositorySource(processedMetadata!!)
                    if (processedMetadata.isChanging || requestMetaData.isChanging()) {
                        processedMetadata = makeChanging(processedMetadata)
                        val expiry = cacheExpirationControl.changingModuleExpiry(moduleComponentIdentifier, cachedMetadata!!.moduleVersion, Duration.ZERO)
                        listener.onChangingModuleResolve(moduleComponentIdentifier, expiry)
                    }
                    val state = resolveStateFactory.stateFor(processedMetadata)
                    cachedMetadata!!.putProcessedMetadata(metadataProcessor.rulesHash, state)
                    localResult.applyTo(result)
                    result.resolved(state)
                }

                BuildableModuleComponentMetaDataResolveResult.State.Failed -> localResult.applyTo<ExternalModuleComponentGraphResolveState?>(
                    result,
                    Function { metadata: ModuleComponentResolveMetadata? ->
                        // should not be called
                        throw IllegalStateException()
                    })

                else -> throw IllegalStateException("Unexpected resolve state: " + result.state)
            }
        }

        fun makeChanging(processedMetadata: ModuleComponentResolveMetadata): ModuleComponentResolveMetadata? {
            val sources = MutableModuleSources()
            processedMetadata.sources.withSources({ src ->
                if (src is ModuleDescriptorHashModuleSource) {
                    val changingSource = ModuleDescriptorHashModuleSource(
                        src.descriptorHash,
                        true
                    )
                    sources.add(changingSource)
                } else {
                    sources.add(src)
                }
            })
            return processedMetadata.withSources(sources)
        }

        override fun resolveArtifactsWithType(component: ComponentArtifactResolveMetadata, artifactType: ArtifactType, result: BuildableArtifactSetResolveResult) {
            delegate.getRemoteAccess().resolveArtifactsWithType(component, artifactType, result)

            if (result.getFailure() == null) {
                val moduleSource: ModuleDescriptorHashModuleSource = Companion.findCachingModuleSource(component.getSources()!!)
                moduleArtifactsCache!!.cacheArtifacts(delegate, component.getId(), cacheKey(artifactType), moduleSource.descriptorHash, result.result)
            }
        }

        override fun resolveArtifact(artifact: ComponentArtifactMetadata, moduleSources: ModuleSources, result: BuildableArtifactFileResolveResult) {
            delegate.getRemoteAccess().resolveArtifact(artifact, moduleSources, result)
            LOGGER.debug("Downloaded artifact '{}' from resolver: {}", artifact, delegate.getName())

            val failure = result.getFailure()
            val cachingModuleSource: ModuleDescriptorHashModuleSource = findCachingModuleSource(moduleSources)
            if (failure == null) {
                moduleArtifactCache!!.store(artifactCacheKey(artifact.getId()!!), result.getResult(), cachingModuleSource.descriptorHash)
            } else if (failure is ArtifactNotFoundException) {
                moduleArtifactCache!!.storeMissing(artifactCacheKey(artifact.getId()!!), result.attempted, cachingModuleSource.descriptorHash)
            }
        }

        override fun estimateMetadataFetchingCost(moduleComponentIdentifier: ModuleComponentIdentifier?): MetadataFetchingCost? {
            return delegate.getLocalAccess().estimateMetadataFetchingCost(moduleComponentIdentifier)
        }
    }

    private fun attachRepositorySource(processedMetadata: ModuleComponentResolveMetadata): ModuleComponentResolveMetadata {
        var processedMetadata = processedMetadata
        val moduleSource = RepositoryChainModuleSource(delegate)
        val originSources: ModuleSources = processedMetadata.sources
        val mergedSources = of(originSources, moduleSource)
        processedMetadata = processedMetadata.withSources(mergedSources)!!
        return processedMetadata
    }

    private fun cacheKey(artifactType: ArtifactType): String {
        return "artifacts:" + artifactType.name
    }

    private fun artifactCacheKey(id: ComponentArtifactIdentifier): ArtifactAtRepositoryKey {
        return ArtifactAtRepositoryKey(delegate.getId(), id)
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(CachingModuleComponentRepository::class.java)

        private fun findCachingModuleSource(sources: ModuleSources): ModuleDescriptorHashModuleSource {
            return sources.getSource<ModuleDescriptorHashModuleSource?>(ModuleDescriptorHashModuleSource::class.java)
                .orElseThrow<java.lang.RuntimeException?>(java.util.function.Supplier { java.lang.RuntimeException("Cannot find expected module source " + org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleDescriptorHashModuleSource::class.java.getSimpleName() + " in " + sources) })!!
        }
    }
}
