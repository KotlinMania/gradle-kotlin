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
package org.gradle.api.internal.artifacts.ivyservice.modulecache

import com.google.common.collect.MapMaker
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.BaseModuleComponentRepository
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepository
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact
import org.gradle.api.logging.Logging.getLogger
import org.gradle.internal.component.external.model.ExternalModuleComponentGraphResolveState
import org.gradle.internal.concurrent.Stoppable
import java.util.concurrent.ConcurrentHashMap

/**
 * Caches the dependency metadata (descriptors, artifact files) in memory.
 */
class ResolvedArtifactCaches : Stoppable {
    private val cachePerRepo: MutableMap<String?, MutableMap<ComponentArtifactIdentifier?, ResolvableArtifact?>?> =
        MapMaker().makeMap<String?, MutableMap<ComponentArtifactIdentifier?, ResolvableArtifact?>?>()
    private val cachePerRepoWithVerification: MutableMap<String?, MutableMap<ComponentArtifactIdentifier?, ResolvableArtifact?>?> =
        MapMaker().makeMap<String?, MutableMap<ComponentArtifactIdentifier?, ResolvableArtifact?>?>()

    /**
     * For a remote repository, the only thing required is a resolved artifact cache.
     * The rest of the in-memory caching is handled by the CachingModuleComponentRepository.
     */
    fun provideResolvedArtifactCache(
        input: ModuleComponentRepository<ExternalModuleComponentGraphResolveState?>,
        withVerification: Boolean
    ): ModuleComponentRepository<ExternalModuleComponentGraphResolveState?> {
        val caches = getResolvedArtifactCache(if (withVerification) cachePerRepoWithVerification else cachePerRepo, input)
        return ResolvedArtifactCacheProvidingModuleComponentRepository(caches, input)
    }

    private fun getResolvedArtifactCache(
        cache: MutableMap<String?, MutableMap<ComponentArtifactIdentifier?, ResolvableArtifact?>?>,
        input: ModuleComponentRepository<ExternalModuleComponentGraphResolveState?>
    ): MutableMap<ComponentArtifactIdentifier?, ResolvableArtifact?> {
        var resolvedArtifactCache = cache.get(input.id)
        if (resolvedArtifactCache == null) {
            LOG!!.debug("Creating new in-memory cache for repo '{}' [{}].", input.name, input.id)
            resolvedArtifactCache = ConcurrentHashMap<ComponentArtifactIdentifier?, ResolvableArtifact?>()
            cache.put(input.id, resolvedArtifactCache)
        } else {
            LOG!!.debug("Reusing in-memory cache for repo '{}' [{}].", input.name, input.id)
        }
        return resolvedArtifactCache
    }

    override fun stop() {
        cachePerRepo.clear()
        cachePerRepoWithVerification.clear()
    }

    private class ResolvedArtifactCacheProvidingModuleComponentRepository(
        private val resolvedArtifactCache: MutableMap<ComponentArtifactIdentifier?, ResolvableArtifact?>,
        delegate: ModuleComponentRepository<ExternalModuleComponentGraphResolveState?>
    ) : BaseModuleComponentRepository<ExternalModuleComponentGraphResolveState?>(delegate) {
        override fun getArtifactCache(): MutableMap<ComponentArtifactIdentifier?, ResolvableArtifact?> {
            return resolvedArtifactCache
        }
    }

    companion object {
        private val LOG = getLogger(ResolvedArtifactCaches::class.java)
    }
}
