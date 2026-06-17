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

import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepository
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata
import org.gradle.util.internal.BuildCommencedTimeProvider
import org.slf4j.Logger
import org.slf4j.LoggerFactory

abstract class AbstractModuleMetadataCache internal constructor(protected val timeProvider: BuildCommencedTimeProvider) : ModuleMetadataCache {
    override fun getCachedModuleDescriptor(repository: ModuleComponentRepository<*>, id: ModuleComponentIdentifier): ModuleMetadataCache.CachedMetadata? {
        val key = createKey(repository, id)
        return get(key)
    }

    override fun cacheMissing(repository: ModuleComponentRepository<*>, id: ModuleComponentIdentifier): ModuleMetadataCache.CachedMetadata {
        LOGGER.debug("Recording absence of module descriptor in cache: {} [changing = {}]", id, false)
        val key = createKey(repository, id)
        val entry: ModuleMetadataCacheEntry = ModuleMetadataCacheEntry.Companion.forMissingModule(timeProvider.getCurrentTime())
        val cachedMetaData = DefaultCachedMetadata(entry, null, timeProvider)
        store(key, entry, cachedMetaData)
        return cachedMetaData
    }

    override fun cacheMetaData(repository: ModuleComponentRepository<*>, id: ModuleComponentIdentifier, metadata: ModuleComponentResolveMetadata): ModuleMetadataCache.CachedMetadata? {
        LOGGER.debug("Recording module descriptor in cache: {} [changing = {}]", metadata.getId(), metadata.isChanging)
        val key = createKey(repository, id)
        val entry = createEntry(metadata)
        val cachedMetaData = DefaultCachedMetadata(entry, metadata, timeProvider)
        return store(key, entry, cachedMetaData)
    }

    protected fun createKey(repository: ModuleComponentRepository<*>, id: ModuleComponentIdentifier): ModuleComponentAtRepositoryKey {
        return ModuleComponentAtRepositoryKey(repository.id, id)
    }

    private fun createEntry(metaData: ModuleComponentResolveMetadata): ModuleMetadataCacheEntry {
        return ModuleMetadataCacheEntry.Companion.forMetaData(metaData, timeProvider.getCurrentTime())
    }

    abstract fun store(key: ModuleComponentAtRepositoryKey?, entry: ModuleMetadataCacheEntry?, cachedMetaData: ModuleMetadataCache.CachedMetadata?): ModuleMetadataCache.CachedMetadata?

    abstract fun get(key: ModuleComponentAtRepositoryKey?): ModuleMetadataCache.CachedMetadata?

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(PersistentModuleMetadataCache::class.java)
    }
}
