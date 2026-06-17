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
package org.gradle.api.internal.artifacts.ivyservice.modulecache.dynamicversions

import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepository
import org.gradle.util.internal.BuildCommencedTimeProvider
import org.slf4j.Logger
import org.slf4j.LoggerFactory

abstract class AbstractModuleVersionsCache(protected val timeProvider: BuildCommencedTimeProvider) : ModuleVersionsCache {
    override fun cacheModuleVersionList(repository: ModuleComponentRepository<*>, moduleId: ModuleIdentifier?, listedVersions: MutableSet<String?>?) {
        LOGGER.debug("Caching version list in module versions cache: Using '{}' for '{}'", listedVersions, moduleId)
        val key = createKey(repository, moduleId)
        val entry = createEntry(listedVersions)
        store(key, entry)
    }

    override fun getCachedModuleResolution(repository: ModuleComponentRepository<*>, moduleId: ModuleIdentifier?): ModuleVersionsCache.CachedModuleVersionList? {
        val key = createKey(repository, moduleId)
        val entry = get(key)
        return if (entry == null) null else versionList(entry)
    }

    private fun versionList(entry: ModuleVersionsCacheEntry): ModuleVersionsCache.CachedModuleVersionList {
        return DefaultCachedModuleVersionList(
            entry.moduleVersionListing,
            timeProvider.getCurrentTime() - entry.createTimestamp
        )
    }

    private fun createKey(repository: ModuleComponentRepository<*>, moduleId: ModuleIdentifier?): ModuleAtRepositoryKey {
        return ModuleAtRepositoryKey(repository.id, moduleId)
    }

    private fun createEntry(listedVersions: MutableSet<String?>?): ModuleVersionsCacheEntry {
        return ModuleVersionsCacheEntry(listedVersions, timeProvider.getCurrentTime())
    }

    abstract fun store(key: ModuleAtRepositoryKey?, entry: ModuleVersionsCacheEntry?)

    abstract fun get(key: ModuleAtRepositoryKey?): ModuleVersionsCacheEntry?

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(DefaultModuleVersionsCache::class.java)
    }
}
