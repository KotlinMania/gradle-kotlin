/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.BuildAdapter
import org.gradle.BuildResult
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCachesProvider
import org.gradle.api.internal.artifacts.ivyservice.CacheLayout
import org.gradle.api.internal.artifacts.ivyservice.DefaultArtifactCaches
import org.gradle.api.internal.artifacts.transform.ImmutableTransformWorkspaceServices
import org.gradle.api.internal.artifacts.transform.ToPlannedTransformStepConverter
import org.gradle.api.internal.artifacts.transform.TransformExecutionResult
import org.gradle.api.internal.cache.CacheConfigurationsInternal
import org.gradle.cache.Cache
import org.gradle.cache.CacheCleanupStrategyFactory
import org.gradle.cache.FineGrainedCacheCleanupStrategyFactory
import org.gradle.cache.UnscopedCacheBuilderFactory
import org.gradle.cache.internal.CrossBuildInMemoryCacheFactory
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory
import org.gradle.execution.plan.ToPlannedNodeConverter
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.execution.DeferredResult
import org.gradle.internal.execution.Identity
import org.gradle.internal.execution.workspace.ImmutableWorkspaceProvider
import org.gradle.internal.execution.workspace.impl.CacheBasedImmutableWorkspaceProvider
import org.gradle.internal.file.FileAccessTimeJournal
import org.gradle.internal.service.Provides
import org.gradle.internal.service.ServiceRegistrationProvider
import org.gradle.internal.versionedcache.UsedGradleVersions
import java.util.function.Predicate

class DependencyManagementGradleUserHomeScopeServices : ServiceRegistrationProvider {
    @Provides
    fun createToPlannedTransformStepConverter(): ToPlannedNodeConverter {
        return ToPlannedTransformStepConverter()
    }

    @Provides
    fun createWritableArtifactCacheLockingParameters(
        fileAccessTimeJournal: FileAccessTimeJournal,
        usedGradleVersions: UsedGradleVersions
    ): DefaultArtifactCaches.WritableArtifactCacheLockingParameters {
        return object : DefaultArtifactCaches.WritableArtifactCacheLockingParameters {
            override fun getFileAccessTimeJournal(): FileAccessTimeJournal {
                return fileAccessTimeJournal
            }

            override fun getUsedGradleVersions(): UsedGradleVersions {
                return usedGradleVersions
            }
        }
    }

    @Provides
    fun createArtifactCaches(
        cacheBuilderFactory: GlobalScopedCacheBuilderFactory,
        unscopedCacheBuilderFactory: UnscopedCacheBuilderFactory,
        parameters: DefaultArtifactCaches.WritableArtifactCacheLockingParameters,
        listenerManager: ListenerManager,
        documentationRegistry: DocumentationRegistry,
        cacheConfigurations: CacheConfigurationsInternal,
        cacheCleanupStrategyFactory: CacheCleanupStrategyFactory
    ): ArtifactCachesProvider {
        val artifactCachesProvider = DefaultArtifactCaches(cacheBuilderFactory, unscopedCacheBuilderFactory, parameters, documentationRegistry, cacheConfigurations, cacheCleanupStrategyFactory)
        listenerManager.addListener(object : BuildAdapter() {
            @Suppress("deprecation")
            override fun buildFinished(result: BuildResult) {
                artifactCachesProvider.getWritableCacheAccessCoordinator().useCache(Runnable {})
            }
        })
        return artifactCachesProvider
    }

    @Provides
    fun createTransformWorkspaceServices(
        cacheBuilderFactory: GlobalScopedCacheBuilderFactory,
        crossBuildInMemoryCacheFactory: CrossBuildInMemoryCacheFactory,
        fileAccessTimeJournal: FileAccessTimeJournal,
        cacheConfigurations: CacheConfigurationsInternal,
        cacheCleanupStrategyFactory: FineGrainedCacheCleanupStrategyFactory
    ): ImmutableTransformWorkspaceServices {
        val cacheBuilder = cacheBuilderFactory
            .createFineGrainedCacheBuilder(CacheLayout.TRANSFORMS.getName())
            .withDisplayName("Artifact transforms cache")
        val identityCache =
            crossBuildInMemoryCacheFactory.newCacheRetainingDataFromPreviousBuild<Identity?, DeferredResult<TransformExecutionResult.TransformWorkspaceResult?>?>(Predicate { result: DeferredResult<TransformExecutionResult.TransformWorkspaceResult?>? -> result!!.getResult().isSuccessful })
        val workspaceProvider = CacheBasedImmutableWorkspaceProvider.createWorkspaceProvider(cacheBuilder, fileAccessTimeJournal, cacheConfigurations, cacheCleanupStrategyFactory)
        return object : ImmutableTransformWorkspaceServices {
            override fun getWorkspaceProvider(): ImmutableWorkspaceProvider {
                return workspaceProvider
            }

            override fun getIdentityCache(): Cache<Identity?, DeferredResult<TransformExecutionResult.TransformWorkspaceResult?>?> {
                return identityCache
            }

            override fun close() {
                workspaceProvider.close()
            }
        }
    }
}
