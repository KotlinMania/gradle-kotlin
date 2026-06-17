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
package org.gradle.testfixtures.internal

import org.gradle.cache.CacheCleanupStrategy
import org.gradle.cache.CacheOpenException
import org.gradle.cache.FineGrainedCacheCleanupStrategy
import org.gradle.cache.FineGrainedPersistentCache
import org.gradle.cache.IndexedCache
import org.gradle.cache.IndexedCacheParameters
import org.gradle.cache.LockOptions
import org.gradle.cache.PersistentCache
import org.gradle.cache.internal.CacheFactory
import org.gradle.cache.internal.CacheVisitor
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.Pair
import org.gradle.internal.serialize.Serializer
import org.gradle.util.internal.GFileUtils
import java.io.File
import java.time.Instant
import java.util.Collections
import java.util.function.Consumer
import java.util.function.Supplier

class TestInMemoryCacheFactory : CacheFactory {
    /*
     * In case multiple threads is accessing the cache, for example when running JUnit 5 tests in parallel,
     * the map must be protected from concurrent modification.
     */
    val caches: MutableMap<Pair<File, String>, IndexedCache<*, *>> = Collections.synchronizedMap<Pair<File?, String?>, IndexedCache<*, *>>(LinkedHashMap<Pair<File?, String?>?, IndexedCache<*, *>?>())

    @Throws(CacheOpenException::class)
    override fun open(
        cacheDir: File,
        displayName: String,
        properties: MutableMap<String, *>,
        lockOptions: LockOptions,
        initializer: Consumer<in PersistentCache>?,
        cacheCleanupStrategy: CacheCleanupStrategy?
    ): PersistentCache {
        GFileUtils.mkdirs(cacheDir)
        val cache: InMemoryCache = TestInMemoryCacheFactory.InMemoryCache(cacheDir, displayName, if (cacheCleanupStrategy != null) cacheCleanupStrategy else CacheCleanupStrategy.NO_CLEANUP)
        if (initializer != null) {
            initializer.accept(cache)
        }
        return cache
    }

    @Throws(CacheOpenException::class)
    override fun openFineGrained(cacheDir: File, displayName: String, cacheCleanupStrategy: FineGrainedCacheCleanupStrategy): FineGrainedPersistentCache {
        GFileUtils.mkdirs(cacheDir)
        return InMemoryFineGrainedCache(cacheDir, displayName, if (cacheCleanupStrategy != null) cacheCleanupStrategy.getCleanupStrategy() else CacheCleanupStrategy.NO_CLEANUP)
    }

    fun open(cacheDir: File, displayName: String): PersistentCache {
        return TestInMemoryCacheFactory.InMemoryCache(cacheDir, displayName, CacheCleanupStrategy.NO_CLEANUP)
    }

    override fun visitCaches(visitor: CacheVisitor) {
        throw UnsupportedOperationException()
    }

    private inner class InMemoryCache(private val cacheDir: File, private val displayName: String, private val cleanup: CacheCleanupStrategy) : PersistentCache {
        private var closed = false

        override fun close() {
            cleanup()
            closed = true
        }

        override fun cleanup() {
            if (cleanup != null) {
                synchronized(this) {
                    cleanup.clean(this, Instant.now())
                }
            }
        }

        override fun getBaseDir(): File {
            return cacheDir
        }

        override fun getReservedCacheFiles(): MutableCollection<File> {
            return mutableListOf<File>()
        }

        fun assertNotClosed() {
            check(!closed) { "cache is closed" }
        }

        override fun <K, V> createIndexedCache(name: String, keyType: Class<K?>, valueSerializer: Serializer<V?>): IndexedCache<K?, V?> {
            assertNotClosed()
            return createIndexedCache<K?, V?>(name, valueSerializer)
        }

        override fun <K, V> indexedCacheExists(parameters: IndexedCacheParameters<K?, V?>): Boolean {
            return true
        }

        override fun <K, V> createIndexedCache(parameters: IndexedCacheParameters<K?, V?>): IndexedCache<K?, V?> {
            assertNotClosed()
            return createIndexedCache<K?, V?>(parameters.getCacheName(), parameters.getValueSerializer())
        }

        fun <K, V> createIndexedCache(name: String, valueSerializer: Serializer<V?>): IndexedCache<K?, V?> {
            assertNotClosed()
            var indexedCache = caches.get(Pair.of(cacheDir, name))
            if (indexedCache == null) {
                indexedCache = TestInMemoryIndexedCache<K?, V?>(valueSerializer)
                caches.put(Pair.of(cacheDir, name), indexedCache)
            }
            return uncheckedCast<IndexedCache<K?, V?>?>(indexedCache)!!
        }

        override fun <T> withFileLock(action: Supplier<out T>): T? {
            return action.get()
        }

        override fun withFileLock(action: Runnable) {
            action.run()
        }

        override fun <T> useCache(action: Supplier<out T>): T? {
            assertNotClosed()
            // The contract of useCache() means we have to provide some basic synchronization.
            synchronized(this) {
                return action.get()
            }
        }

        override fun useCache(action: Runnable) {
            assertNotClosed()
            // The contract of useCache() means we have to provide some basic synchronization.
            synchronized(this) {
                action.run()
            }
        }

        override fun getDisplayName(): String {
            return "InMemoryCache '" + displayName + "' " + cacheDir
        }

        override fun toString(): String {
            return getDisplayName()
        }
    }

    private class InMemoryFineGrainedCache(private val cacheDir: File, private val displayName: String, private val cleanupStrategy: CacheCleanupStrategy) : FineGrainedPersistentCache {
        private var closed = false

        override fun open(): FineGrainedPersistentCache {
            return this
        }

        override fun <T> useCache(key: String, action: Supplier<out T>): T? {
            assertNotClosed()
            validateKey(key)
            synchronized(this) {
                return action.get()
            }
        }

        override fun useCache(key: String, action: Runnable) {
            useCache<Any>(key, Supplier {
                action.run()
                null
            })
        }

        override fun <T> withFileLock(key: String, action: Supplier<out T>): T? {
            assertNotClosed()
            validateKey(key)
            return action.get()
        }

        override fun withFileLock(key: String, action: Runnable) {
            withFileLock<Any>(key, Supplier {
                action.run()
                null
            })
        }

        fun assertNotClosed() {
            check(!closed) { "cache is closed" }
        }

        override fun close() {
            cleanup()
            closed = true
        }

        override fun getBaseDir(): File {
            return cacheDir
        }

        override fun getReservedCacheFiles(): MutableCollection<File> {
            return mutableListOf<File>()
        }

        override fun getDisplayName(): String {
            return "InMemoryFineGrainedCache '" + displayName + "' " + cacheDir
        }

        override fun cleanup() {
            synchronized(this) {
                cleanupStrategy.clean(this, Instant.now())
            }
        }

        override fun toString(): String {
            return getDisplayName()
        }

        companion object {
            private fun validateKey(key: String) {
                require(!(key.contains("/") || key.contains("\\"))) { String.format("Cache key path must not contain file separator: '%s'", key) }
                require(!key.startsWith(".")) { String.format("Cache key must not start with '.' character: '%s'", key) }
            }
        }
    }
}
