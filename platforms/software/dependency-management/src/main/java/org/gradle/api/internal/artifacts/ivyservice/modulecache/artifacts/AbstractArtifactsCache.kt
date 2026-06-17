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
package org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts

import com.google.common.collect.ImmutableSet
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepository
import org.gradle.internal.component.model.ComponentArtifactMetadata
import org.gradle.internal.hash.HashCode
import org.gradle.util.internal.BuildCommencedTimeProvider

abstract class AbstractArtifactsCache(protected val timeProvider: BuildCommencedTimeProvider) : ModuleArtifactsCache {
    override fun cacheArtifacts(
        repository: ModuleComponentRepository<*>,
        componentId: ComponentIdentifier?,
        context: String?,
        descriptorHash: HashCode?,
        artifacts: MutableCollection<out ComponentArtifactMetadata?>
    ): CachedArtifacts {
        val key = ArtifactsAtRepositoryKey(repository.id, componentId, context)
        val entry = ModuleArtifactsCacheEntry(ImmutableSet.copyOf(artifacts), timeProvider.getCurrentTime(), descriptorHash)
        store(key, entry)
        return createCacheArtifacts(entry)
    }

    abstract fun store(key: ArtifactsAtRepositoryKey?, entry: ModuleArtifactsCacheEntry?)

    override fun getCachedArtifacts(repository: ModuleComponentRepository<*>, componentId: ComponentIdentifier?, context: String?): CachedArtifacts? {
        val key = ArtifactsAtRepositoryKey(repository.id, componentId, context)
        val entry = get(key)
        return if (entry == null) null else createCacheArtifacts(entry)
    }

    abstract fun get(key: ArtifactsAtRepositoryKey?): ModuleArtifactsCacheEntry?

    private fun createCacheArtifacts(entry: ModuleArtifactsCacheEntry): CachedArtifacts {
        val entryAge = timeProvider.getCurrentTime() - entry.createTimestamp
        return DefaultCachedArtifacts(entry.artifacts, entry.moduleDescriptorHash, entryAge)
    }

    protected class ModuleArtifactsCacheEntry internal constructor(artifacts: MutableSet<out ComponentArtifactMetadata?>, val createTimestamp: Long, val moduleDescriptorHash: HashCode?) {
        val artifacts: MutableSet<ComponentArtifactMetadata?>

        init {
            this.artifacts = LinkedHashSet<ComponentArtifactMetadata?>(artifacts)
        }
    }
}
