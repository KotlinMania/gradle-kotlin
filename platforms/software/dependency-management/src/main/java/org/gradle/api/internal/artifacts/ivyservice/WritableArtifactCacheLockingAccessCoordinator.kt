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
package org.gradle.api.internal.artifacts.ivyservice

import org.gradle.api.internal.cache.CacheConfigurationsInternal
import org.gradle.api.internal.filestore.DefaultArtifactIdentifierFileStore
import org.gradle.cache.CacheCleanupStrategyFactory
import org.gradle.cache.CleanupAction
import org.gradle.cache.FileLockManager
import org.gradle.cache.IndexedCache
import org.gradle.cache.IndexedCacheParameters
import org.gradle.cache.PersistentCache
import org.gradle.cache.UnscopedCacheBuilderFactory
import org.gradle.cache.internal.CompositeCleanupAction
import org.gradle.cache.internal.LeastRecentlyUsedCacheCleanup
import org.gradle.cache.internal.SingleDepthFilesFinder
import org.gradle.internal.file.FileAccessTimeJournal
import org.gradle.internal.resource.cached.DefaultExternalResourceFileStore
import org.gradle.internal.serialize.Serializer
import org.gradle.internal.time.TimestampSuppliers.daysAgo
import org.gradle.internal.versionedcache.UnusedVersionsCacheCleanup.Companion.create
import org.gradle.internal.versionedcache.UsedGradleVersions
import java.io.Closeable
import java.util.function.Function
import java.util.function.Supplier

class WritableArtifactCacheLockingAccessCoordinator(
    unscopedCacheBuilderFactory: UnscopedCacheBuilderFactory,
    cacheMetaData: ArtifactCacheMetadata,
    fileAccessTimeJournal: FileAccessTimeJournal,
    usedGradleVersions: UsedGradleVersions,
    cacheConfigurations: CacheConfigurationsInternal,
    cacheCleanupStrategyFactory: CacheCleanupStrategyFactory
) : ArtifactCacheLockingAccessCoordinator, Closeable {
    private val cache: PersistentCache

    init {
        cache = unscopedCacheBuilderFactory
            .cache(cacheMetaData.getCacheDir())
            .withDisplayName("artifact cache")
            .withInitialLockMode(FileLockManager.LockMode.OnDemand) // Don't need to lock anything until we use the caches
            .withCleanupStrategy(
                cacheCleanupStrategyFactory.create(
                    createCleanupAction(cacheMetaData, fileAccessTimeJournal, usedGradleVersions, cacheConfigurations),
                    Supplier { cacheConfigurations.getCleanupFrequency().get() })
            )
            .open()
    }

    private fun createCleanupAction(
        cacheMetaData: ArtifactCacheMetadata,
        fileAccessTimeJournal: FileAccessTimeJournal,
        usedGradleVersions: UsedGradleVersions,
        cacheConfigurations: CacheConfigurationsInternal
    ): CleanupAction {
        return CompositeCleanupAction.builder()
            .add(create(CacheLayout.MODULES.getName(), CacheLayout.MODULES.getVersionMapping(), usedGradleVersions))
            .add(
                cacheMetaData.getExternalResourcesStoreDirectory(),
                create(CacheLayout.RESOURCES.getName(), CacheLayout.RESOURCES.getVersionMapping(), usedGradleVersions),
                LeastRecentlyUsedCacheCleanup(
                    SingleDepthFilesFinder(DefaultExternalResourceFileStore.FILE_TREE_DEPTH_TO_TRACK_AND_CLEANUP),
                    fileAccessTimeJournal,
                    getMaxAgeTimestamp(cacheConfigurations)
                )
            )
            .add(
                cacheMetaData.getFileStoreDirectory(),
                create(CacheLayout.FILE_STORE.getName(), CacheLayout.FILE_STORE.getVersionMapping(), usedGradleVersions),
                LeastRecentlyUsedCacheCleanup(
                    SingleDepthFilesFinder(DefaultArtifactIdentifierFileStore.FILE_TREE_DEPTH_TO_TRACK_AND_CLEANUP),
                    fileAccessTimeJournal,
                    getMaxAgeTimestamp(cacheConfigurations)
                )
            )
            .add(
                cacheMetaData.getMetaDataStoreDirectory().getParentFile(),
                create(CacheLayout.META_DATA.getName(), CacheLayout.META_DATA.getVersionMapping(), usedGradleVersions)
            ) // Cleanup old unused 'transforms-X' directories too. Transforms are now cached in 'caches/<gradle-version>/transforms'.
            .add(create(CacheLayout.TRANSFORMS.getName(), CacheLayout.TRANSFORMS.getVersionMapping(), usedGradleVersions))
            .build()
    }

    private fun getMaxAgeTimestamp(cacheConfigurations: CacheConfigurationsInternal): Supplier<Long> {
        val maxAgeProperty = Integer.getInteger("org.gradle.internal.cleanup.external.max.age")
        if (maxAgeProperty == null) {
            return cacheConfigurations.getDownloadedResources().getEntryRetentionTimestampSupplier()
        } else {
            return daysAgo(maxAgeProperty)
        }
    }

    override fun close() {
        cache.close()
    }

    override fun <T> withFileLock(action: Supplier<out T>): T? {
        return cache.withFileLock(action)
    }

    override fun withFileLock(action: Runnable) {
        cache.withFileLock(action)
    }

    override fun <T> useCache(action: Supplier<out T>): T? {
        return cache.useCache(action)
    }

    override fun useCache(action: Runnable) {
        cache.useCache(action)
    }

    override fun <K, V> createCache(cacheName: String, keySerializer: Serializer<K?>, valueSerializer: Serializer<V?>): IndexedCache<K?, V?> {
        val cacheFileInMetaDataStore = CacheLayout.META_DATA.getKey() + "/" + cacheName
        val indexedCache = cache.createIndexedCache<K?, V?>(IndexedCacheParameters.of<K?, V?>(cacheFileInMetaDataStore, keySerializer, valueSerializer))
        return WritableArtifactCacheLockingAccessCoordinator.CacheLockingIndexedCache<K?, V?>(indexedCache)
    }

    private inner class CacheLockingIndexedCache<K, V>(private val indexedCache: IndexedCache<K?, V?>) : IndexedCache<K?, V?> {
        override fun getIfPresent(key: K?): V? {
            return cache.useCache<V?>(Supplier { indexedCache.getIfPresent(key) })
        }

        override fun get(key: K?, producer: Function<in K?, out V>): V? {
            return cache.useCache<V?>(Supplier { indexedCache.get(key, producer) })
        }

        override fun put(key: K?, value: V?) {
            cache.useCache(Runnable { indexedCache.put(key, value) })
        }

        override fun remove(key: K?) {
            cache.useCache(Runnable { indexedCache.remove(key) })
        }
    }
}
