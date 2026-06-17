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

import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging.getLogger
import org.gradle.cache.FileLockManager
import org.gradle.cache.IndexedCache
import org.gradle.cache.IndexedCacheParameters
import org.gradle.cache.PersistentCache
import org.gradle.cache.UnscopedCacheBuilderFactory
import org.gradle.internal.Factory
import org.gradle.internal.serialize.Serializer
import java.io.Closeable
import java.util.function.Function
import java.util.function.Supplier

/**
 * An implementation of an artifact cache manager which performs operations in a read-only
 * cache first. If the operation is not successful in the readonly cache OR if it's a write
 * operation, the 2nd level writable cache is used.
 *
 * Operations use in-process locking for the read-only cache (even when requesting file locking) and
 * write operations use the regular locking mechanism (file or in-process).
 */
class ReadOnlyArtifactCacheLockingAccessCoordinator(
    unscopedCacheBuilderFactory: UnscopedCacheBuilderFactory,
    cacheMetaData: ArtifactCacheMetadata
) : ArtifactCacheLockingAccessCoordinator, Closeable {
    private val cache: PersistentCache

    init {
        cache = unscopedCacheBuilderFactory
            .cache(cacheMetaData.getCacheDir())
            .withDisplayName("read only artifact cache")
            .withInitialLockMode(FileLockManager.LockMode.None) // Don't need to lock anything, it's read-only
            .open()
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
        val parameters: IndexedCacheParameters<K?, V?> = IndexedCacheParameters.of<K?, V?>(cacheFileInMetaDataStore, keySerializer, valueSerializer)
        if (cache.indexedCacheExists<K?, V?>(parameters)) {
            return ReadOnlyArtifactCacheLockingAccessCoordinator.TransparentCacheLockingIndexedCache<K?, V?>(FailSafeIndexedCache<K?, V?>(cache.createIndexedCache<K?, V?>(parameters)))
        }
        return EmptyIndexedCache<K?, V?>()
    }

    private class EmptyIndexedCache<K, V> : IndexedCache<K?, V?> {
        override fun getIfPresent(key: K?): V? {
            return null
        }

        override fun get(key: K?, producer: Function<in K?, out V>): V? {
            return producer.apply(key)
        }

        override fun put(key: K?, value: V?) {
            throw UnsupportedOperationException()
        }

        override fun remove(key: K?) {
            throw UnsupportedOperationException()
        }
    }

    private class FailSafeIndexedCache<K, V>(private val delegate: IndexedCache<K?, V?>) : IndexedCache<K?, V?> {
        private var failed = false

        override fun getIfPresent(key: K?): V? {
            return failSafe<V?>(org.gradle.internal.Factory { delegate.getIfPresent(key) })
        }

        override fun get(key: K?, producer: Function<in K?, out V>): V? {
            return failSafe<V?>(org.gradle.internal.Factory { delegate.get(key, producer) })
        }

        override fun put(key: K?, value: V?) {
        }

        override fun remove(key: K?) {
        }

        fun <T> failSafe(operation: Factory<T?>): T? {
            if (failed) {
                return null
            }
            try {
                return operation.create()
            } catch (ex: Exception) {
                failed = true
                LOGGER.debug("Error accessing read-only cache", ex)
            }
            return null
        }
    }

    private inner class TransparentCacheLockingIndexedCache<K, V>(private val indexedCache: IndexedCache<K?, V?>) : IndexedCache<K?, V?> {
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

    companion object {
        private val LOGGER: Logger = getLogger(ReadOnlyArtifactCacheLockingAccessCoordinator::class.java)!!
    }
}
