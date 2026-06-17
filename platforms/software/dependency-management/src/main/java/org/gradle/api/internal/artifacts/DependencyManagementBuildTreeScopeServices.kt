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
package org.gradle.api.internal.artifacts

import com.google.common.collect.ImmutableMap
import org.gradle.StartParameter
import org.gradle.api.internal.artifacts.capability.CapabilitySelectorSerializer
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheLockingAccessCoordinator
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheMetadata
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCachesProvider
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ConnectionFailureRepositoryDisabler
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleDescriptorHashCodec
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleDescriptorHashModuleSource
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.StartParameterResolutionOverride
import org.gradle.api.internal.artifacts.ivyservice.modulecache.AbstractModuleMetadataCache
import org.gradle.api.internal.artifacts.ivyservice.modulecache.FileStoreAndIndexProvider
import org.gradle.api.internal.artifacts.ivyservice.modulecache.InMemoryModuleMetadataCache
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleRepositoryCacheProvider
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleRepositoryCaches
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleSourcesSerializer
import org.gradle.api.internal.artifacts.ivyservice.modulecache.PersistentModuleMetadataCache
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ReadOnlyModuleMetadataCache
import org.gradle.api.internal.artifacts.ivyservice.modulecache.TwoStageModuleMetadataCache
import org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts.AbstractArtifactsCache
import org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts.DefaultModuleArtifactCache
import org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts.DefaultModuleArtifactsCache
import org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts.InMemoryModuleArtifactCache
import org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts.InMemoryModuleArtifactsCache
import org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts.ModuleArtifactCache
import org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts.ReadOnlyModuleArtifactCache
import org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts.ReadOnlyModuleArtifactsCache
import org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts.TwoStageArtifactsCache
import org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts.TwoStageModuleArtifactCache
import org.gradle.api.internal.artifacts.ivyservice.modulecache.dynamicversions.AbstractModuleVersionsCache
import org.gradle.api.internal.artifacts.ivyservice.modulecache.dynamicversions.DefaultModuleVersionsCache
import org.gradle.api.internal.artifacts.ivyservice.modulecache.dynamicversions.InMemoryModuleVersionsCache
import org.gradle.api.internal.artifacts.ivyservice.modulecache.dynamicversions.ReadOnlyModuleVersionsCache
import org.gradle.api.internal.artifacts.ivyservice.modulecache.dynamicversions.TwoStageModuleVersionsCache
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DefaultLocalVariantGraphResolveStateBuilder
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.LocalVariantGraphResolveStateBuilder
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.DefaultProjectPublicationRegistry
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectArtifactResolver
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectPublicationRegistry
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VariantArtifactSetCache
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.AttributeContainerSerializer
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ThisBuildTreeOnlyGraphElementStore
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.store.ResolutionResultsStoreFactory
import org.gradle.api.internal.artifacts.repositories.metadata.DefaultMetadataFileSourceCodec
import org.gradle.api.internal.artifacts.repositories.metadata.IvyMutableModuleMetadataFactory
import org.gradle.api.internal.artifacts.repositories.metadata.MavenMutableModuleMetadataFactory
import org.gradle.api.internal.artifacts.repositories.metadata.MetadataFileSource
import org.gradle.api.internal.artifacts.transform.TransformStepNodeFactory
import org.gradle.api.internal.attributes.AttributeDesugaring
import org.gradle.api.internal.file.temp.TemporaryFileProvider
import org.gradle.api.internal.filestore.ArtifactIdentifierFileStore
import org.gradle.api.internal.filestore.DefaultArtifactIdentifierFileStore
import org.gradle.api.internal.filestore.TwoStageArtifactIdentifierFileStore
import org.gradle.api.internal.project.HoldsProjectState
import org.gradle.internal.component.external.model.ModuleComponentGraphResolveStateFactory
import org.gradle.internal.component.local.model.LocalComponentGraphResolveStateFactory
import org.gradle.internal.component.model.ComponentIdGenerator
import org.gradle.internal.component.model.PersistentModuleSource
import org.gradle.internal.hash.ChecksumService
import org.gradle.internal.initialization.layout.BuildTreeLocations
import org.gradle.internal.resolve.resolver.ResolvedVariantCache
import org.gradle.internal.resource.cached.ByUrlCachedExternalResourceIndex
import org.gradle.internal.resource.cached.CachedExternalResourceIndex
import org.gradle.internal.resource.cached.DefaultExternalResourceFileStore
import org.gradle.internal.resource.cached.ExternalResourceFileStore
import org.gradle.internal.resource.cached.TwoStageByUrlCachedExternalResourceIndex
import org.gradle.internal.resource.cached.TwoStageExternalResourceFileStore
import org.gradle.internal.service.Provides
import org.gradle.internal.service.ServiceRegistration
import org.gradle.internal.service.ServiceRegistrationProvider
import org.gradle.util.internal.BuildCommencedTimeProvider
import org.gradle.util.internal.SimpleMapInterner
import java.io.File
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import java.util.function.BiFunction
import java.util.function.Function

/**
 * The set of dependency management services that are created per build tree.
 */
internal class DependencyManagementBuildTreeScopeServices : ServiceRegistrationProvider {
    fun configure(registration: ServiceRegistration) {
        registration.add(ProjectArtifactResolver::class.java)
        registration.add(DefaultExternalResourceFileStore.Factory::class.java)
        registration.add(DefaultArtifactIdentifierFileStore.Factory::class.java)
        registration.add(TransformStepNodeFactory::class.java)
        registration.add(AttributeDesugaring::class.java)
        registration.add(ComponentIdGenerator::class.java)
        registration.add(LocalComponentGraphResolveStateFactory::class.java)
        registration.add(ModuleComponentGraphResolveStateFactory::class.java)
        registration.add(CapabilitySelectorSerializer::class.java)
        registration.add(ThisBuildTreeOnlyGraphElementStore::class.java)
        registration.add(ConnectionFailureRepositoryDisabler::class.java)
        registration.add<DefaultProjectPublicationRegistry?>(ProjectPublicationRegistry::class.java, HoldsProjectState::class.java, DefaultProjectPublicationRegistry::class.java)
        registration.add<DefaultLocalVariantGraphResolveStateBuilder?>(LocalVariantGraphResolveStateBuilder::class.java, DefaultLocalVariantGraphResolveStateBuilder::class.java)
        registration.add(ResolvedVariantCache::class.java)
        registration.add(VariantArtifactSetCache::class.java)
    }

    @Provides
    fun createStringInterner(): SimpleMapInterner {
        return SimpleMapInterner.threadSafe()
    }

    @Provides
    fun createBuildTimeProvider(startParameter: StartParameter): BuildCommencedTimeProvider {
        return BuildCommencedTimeProvider(startParameter)
    }

    @Provides
    fun createResolutionResultsStoreFactory(temporaryFileProvider: TemporaryFileProvider): ResolutionResultsStoreFactory {
        return ResolutionResultsStoreFactory(temporaryFileProvider)
    }

    private fun prepareArtifactUrlCachedResolutionIndex(
        timeProvider: BuildCommencedTimeProvider?,
        cacheAccessCoordinator: ArtifactCacheLockingAccessCoordinator?,
        externalResourceFileStore: ExternalResourceFileStore,
        artifactCacheMetadata: ArtifactCacheMetadata
    ): ByUrlCachedExternalResourceIndex {
        return ByUrlCachedExternalResourceIndex(
            "resource-at-url",
            timeProvider,
            cacheAccessCoordinator,
            externalResourceFileStore.fileAccessTracker,
            artifactCacheMetadata.getCacheDir().toPath()
        )
    }

    @Provides
    fun createFileStoreAndIndexProvider(
        timeProvider: BuildCommencedTimeProvider?,
        artifactCaches: ArtifactCachesProvider,
        defaultExternalResourceFileStoreFactory: DefaultExternalResourceFileStore.Factory,
        defaultArtifactIdentifierFileStoreFactory: DefaultArtifactIdentifierFileStore.Factory
    ): FileStoreAndIndexProvider {
        val writableFileStore: ExternalResourceFileStore = defaultExternalResourceFileStoreFactory.create(artifactCaches.getWritableCacheMetadata())
        val externalResourceFileStore = artifactCaches.withReadOnlyCache<ExternalResourceFileStore>(BiFunction { md: ArtifactCacheMetadata?, manager: ArtifactCacheLockingAccessCoordinator? ->
            TwoStageExternalResourceFileStore(
                defaultExternalResourceFileStoreFactory.create(md!!),
                writableFileStore
            ) as ExternalResourceFileStore
        }).orElse(writableFileStore)
        val writableByUrlCachedExternalResourceIndex: CachedExternalResourceIndex<String?> =
            prepareArtifactUrlCachedResolutionIndex(timeProvider, artifactCaches.getWritableCacheAccessCoordinator(), externalResourceFileStore, artifactCaches.getWritableCacheMetadata())
        val writableArtifactIdentifierFileStore: ArtifactIdentifierFileStore =
            artifactCaches.withWritableCache<DefaultArtifactIdentifierFileStore>(BiFunction { md: ArtifactCacheMetadata?, manager: ArtifactCacheLockingAccessCoordinator? ->
                defaultArtifactIdentifierFileStoreFactory.create(md!!)
            })
        val artifactIdentifierFileStore = artifactCaches.withReadOnlyCache<ArtifactIdentifierFileStore>(BiFunction { md: ArtifactCacheMetadata?, manager: ArtifactCacheLockingAccessCoordinator? ->
            TwoStageArtifactIdentifierFileStore(
                defaultArtifactIdentifierFileStoreFactory.create(md!!),
                writableArtifactIdentifierFileStore
            ) as ArtifactIdentifierFileStore
        }).orElse(writableArtifactIdentifierFileStore)
        return FileStoreAndIndexProvider(
            artifactCaches.withReadOnlyCache<CachedExternalResourceIndex<String?>?>(BiFunction { md: ArtifactCacheMetadata?, manager: ArtifactCacheLockingAccessCoordinator? ->
                TwoStageByUrlCachedExternalResourceIndex(
                    md!!.getCacheDir().toPath(),
                    prepareArtifactUrlCachedResolutionIndex(timeProvider, manager, externalResourceFileStore, md),
                    writableByUrlCachedExternalResourceIndex
                ) as CachedExternalResourceIndex<String?>
            }).orElse(writableByUrlCachedExternalResourceIndex),
            externalResourceFileStore, artifactIdentifierFileStore
        )
    }

    @Provides
    fun createModuleSourcesSerializer(moduleIdentifierFactory: ImmutableModuleIdentifierFactory, fileStoreAndIndexProvider: FileStoreAndIndexProvider): ModuleSourcesSerializer {
        val codecs: MutableMap<Int?, PersistentModuleSource.Codec<out PersistentModuleSource?>?> = ImmutableMap.of<Int?, PersistentModuleSource.Codec<out PersistentModuleSource?>?>(
            MetadataFileSource.codecId, DefaultMetadataFileSourceCodec(moduleIdentifierFactory, fileStoreAndIndexProvider.getArtifactIdentifierFileStore()),
            ModuleDescriptorHashModuleSource.codecId, ModuleDescriptorHashCodec()
        )
        return ModuleSourcesSerializer(codecs)
    }

    @Provides
    fun createStartParameterResolutionOverride(startParameter: StartParameter, buildTreeLocations: BuildTreeLocations): StartParameterResolutionOverride {
        val gradleDir = File(buildTreeLocations.getBuildTreeRootDirectory(), "gradle")
        return StartParameterResolutionOverride(startParameter, gradleDir)
    }

    @Provides
    fun createModuleRepositoryCacheProvider(
        timeProvider: BuildCommencedTimeProvider?,
        moduleIdentifierFactory: ImmutableModuleIdentifierFactory?,
        artifactCaches: ArtifactCachesProvider,
        attributeContainerSerializer: AttributeContainerSerializer?,
        capabilitySelectorSerializer: CapabilitySelectorSerializer?,
        mavenMetadataFactory: MavenMutableModuleMetadataFactory?,
        ivyMetadataFactory: IvyMutableModuleMetadataFactory?,
        stringInterner: SimpleMapInterner?,
        fileStoreAndIndexProvider: FileStoreAndIndexProvider,
        moduleSourcesSerializer: ModuleSourcesSerializer?,
        checksumService: ChecksumService?
    ): ModuleRepositoryCacheProvider {
        val artifactIdentifierFileStore = fileStoreAndIndexProvider.getArtifactIdentifierFileStore()
        val writableCaches = artifactCaches.withWritableCache<ModuleRepositoryCaches>(BiFunction { md: ArtifactCacheMetadata?, manager: ArtifactCacheLockingAccessCoordinator? ->
            Companion.prepareModuleRepositoryCaches(
                md!!,
                manager,
                timeProvider,
                moduleIdentifierFactory,
                attributeContainerSerializer,
                capabilitySelectorSerializer,
                mavenMetadataFactory,
                ivyMetadataFactory,
                stringInterner,
                artifactIdentifierFileStore,
                moduleSourcesSerializer,
                checksumService
            )
        })
        val roCachePath = AtomicReference<Path?>()
        val readOnlyCaches = artifactCaches.withReadOnlyCache<ModuleRepositoryCaches?>(BiFunction { ro: ArtifactCacheMetadata?, manager: ArtifactCacheLockingAccessCoordinator? ->
            roCachePath.set(ro!!.getCacheDir().toPath())
            Companion.prepareReadOnlyModuleRepositoryCaches(
                ro,
                manager,
                timeProvider,
                moduleIdentifierFactory,
                attributeContainerSerializer,
                capabilitySelectorSerializer,
                mavenMetadataFactory,
                ivyMetadataFactory,
                stringInterner,
                artifactIdentifierFileStore,
                moduleSourcesSerializer,
                checksumService
            )
        })
        val moduleVersionsCache = readOnlyCaches.map<AbstractModuleVersionsCache?>(Function { mrc: ModuleRepositoryCaches? ->
            TwoStageModuleVersionsCache(
                timeProvider,
                mrc!!.moduleVersionsCache,
                writableCaches.moduleVersionsCache
            ) as AbstractModuleVersionsCache
        }).orElse(writableCaches.moduleVersionsCache)
        val persistentModuleMetadataCache = readOnlyCaches.map<AbstractModuleMetadataCache?>(Function { mrc: ModuleRepositoryCaches? ->
            TwoStageModuleMetadataCache(
                timeProvider,
                mrc!!.moduleMetadataCache,
                writableCaches.moduleMetadataCache
            ) as AbstractModuleMetadataCache
        }).orElse(writableCaches.moduleMetadataCache)
        val moduleArtifactsCache = readOnlyCaches.map<AbstractArtifactsCache?>(Function { mrc: ModuleRepositoryCaches? ->
            TwoStageArtifactsCache(
                timeProvider,
                mrc!!.moduleArtifactsCache,
                writableCaches.moduleArtifactsCache
            ) as AbstractArtifactsCache
        }).orElse(writableCaches.moduleArtifactsCache)
        val moduleArtifactCache = readOnlyCaches.map<ModuleArtifactCache?>(Function { mrc: ModuleRepositoryCaches? ->
            TwoStageModuleArtifactCache(
                roCachePath.get(),
                mrc!!.moduleArtifactCache,
                writableCaches.moduleArtifactCache
            ) as ModuleArtifactCache
        }).orElse(writableCaches.moduleArtifactCache)
        val persistentCaches = ModuleRepositoryCaches(
            InMemoryModuleVersionsCache(timeProvider, moduleVersionsCache),
            InMemoryModuleMetadataCache(timeProvider, persistentModuleMetadataCache),
            InMemoryModuleArtifactsCache(timeProvider, moduleArtifactsCache),
            InMemoryModuleArtifactCache(timeProvider, moduleArtifactCache)
        )
        val inMemoryOnlyCaches = ModuleRepositoryCaches(
            InMemoryModuleVersionsCache(timeProvider),
            InMemoryModuleMetadataCache(timeProvider),
            InMemoryModuleArtifactsCache(timeProvider),
            InMemoryModuleArtifactCache(timeProvider)
        )
        return ModuleRepositoryCacheProvider(persistentCaches, inMemoryOnlyCaches)
    }

    companion object {
        private fun prepareModuleRepositoryCaches(
            artifactCacheMetadata: ArtifactCacheMetadata,
            cacheAccessCoordinator: ArtifactCacheLockingAccessCoordinator?,
            timeProvider: BuildCommencedTimeProvider?,
            moduleIdentifierFactory: ImmutableModuleIdentifierFactory?,
            attributeContainerSerializer: AttributeContainerSerializer?,
            capabilitySelectorSerializer: CapabilitySelectorSerializer?,
            mavenMetadataFactory: MavenMutableModuleMetadataFactory?,
            ivyMetadataFactory: IvyMutableModuleMetadataFactory?,
            stringInterner: SimpleMapInterner?,
            artifactIdentifierFileStore: ArtifactIdentifierFileStore,
            moduleSourcesSerializer: ModuleSourcesSerializer?,
            checksumService: ChecksumService?
        ): ModuleRepositoryCaches {
            val moduleVersionsCache = DefaultModuleVersionsCache(
                timeProvider,
                cacheAccessCoordinator,
                moduleIdentifierFactory
            )
            val moduleMetadataCache = PersistentModuleMetadataCache(
                timeProvider,
                cacheAccessCoordinator,
                artifactCacheMetadata,
                moduleIdentifierFactory,
                attributeContainerSerializer,
                capabilitySelectorSerializer,
                mavenMetadataFactory,
                ivyMetadataFactory,
                stringInterner,
                moduleSourcesSerializer,
                checksumService
            )
            val moduleArtifactsCache = DefaultModuleArtifactsCache(
                timeProvider,
                cacheAccessCoordinator
            )
            val moduleArtifactCache = DefaultModuleArtifactCache(
                "module-artifact",
                timeProvider,
                cacheAccessCoordinator,
                artifactIdentifierFileStore.fileAccessTracker,
                artifactCacheMetadata.getCacheDir().toPath()
            )
            return ModuleRepositoryCaches(
                moduleVersionsCache,
                moduleMetadataCache,
                moduleArtifactsCache,
                moduleArtifactCache
            )
        }

        private fun prepareReadOnlyModuleRepositoryCaches(
            artifactCacheMetadata: ArtifactCacheMetadata,
            cacheAccessCoordinator: ArtifactCacheLockingAccessCoordinator?,
            timeProvider: BuildCommencedTimeProvider?,
            moduleIdentifierFactory: ImmutableModuleIdentifierFactory?,
            attributeContainerSerializer: AttributeContainerSerializer?,
            capabilitySelectorSerializer: CapabilitySelectorSerializer?,
            mavenMetadataFactory: MavenMutableModuleMetadataFactory?,
            ivyMetadataFactory: IvyMutableModuleMetadataFactory?,
            stringInterner: SimpleMapInterner?,
            artifactIdentifierFileStore: ArtifactIdentifierFileStore,
            moduleSourcesSerializer: ModuleSourcesSerializer?,
            checksumService: ChecksumService?
        ): ModuleRepositoryCaches {
            val moduleVersionsCache = ReadOnlyModuleVersionsCache(
                timeProvider,
                cacheAccessCoordinator,
                moduleIdentifierFactory
            )
            val moduleMetadataCache = ReadOnlyModuleMetadataCache(
                timeProvider,
                cacheAccessCoordinator,
                artifactCacheMetadata,
                moduleIdentifierFactory,
                attributeContainerSerializer,
                capabilitySelectorSerializer,
                mavenMetadataFactory,
                ivyMetadataFactory,
                stringInterner,
                moduleSourcesSerializer,
                checksumService
            )
            val moduleArtifactsCache = ReadOnlyModuleArtifactsCache(
                timeProvider,
                cacheAccessCoordinator
            )
            val moduleArtifactCache = ReadOnlyModuleArtifactCache(
                "module-artifact",
                timeProvider,
                cacheAccessCoordinator,
                artifactIdentifierFileStore.fileAccessTracker,
                artifactCacheMetadata.getCacheDir().toPath()
            )
            return ModuleRepositoryCaches(
                moduleVersionsCache,
                moduleMetadataCache,
                moduleArtifactsCache,
                moduleArtifactCache
            )
        }
    }
}
