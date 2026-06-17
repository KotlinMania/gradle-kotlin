/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.store

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging.getLogger
import org.gradle.cache.internal.Store
import org.gradle.internal.time.Time.startTimer
import org.gradle.internal.time.TimeFormatting.formatDurationVerbose
import java.io.Closeable
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Supplier

class CachedStoreFactory<T>(private val displayName: String) : Closeable {
    private val cache: Cache<Any, T?>
    private val stats: Stats

    init {
        cache = CacheBuilder.newBuilder().maximumSize(CACHE_SIZE.toLong()).expireAfterAccess(CACHE_EXPIRY.toLong(), TimeUnit.MILLISECONDS).build<Any, T?>()
        stats = Stats()
    }

    fun createCachedStore(id: Any): Store<T?> {
        return SimpleStore<T?>(cache, id, stats)
    }

    override fun close() {
        LOG.debug(
            (displayName + " cache closed. Cache reads: "
                    + stats.readsFromCache + ", disk reads: "
                    + stats.readsFromDisk + " (avg: " + formatDurationVerbose(stats.diskReadsAvgMs) + ", total: " + formatDurationVerbose(stats.diskReadsTotalMs.get()) + ")")
        )
    }

    private class Stats {
        private val diskReadsTotalMs = AtomicLong()
        private val readsFromCache = AtomicLong()
        private val readsFromDisk = AtomicLong()

        fun readFromDisk(duration: Long) {
            readsFromDisk.incrementAndGet()
            diskReadsTotalMs.addAndGet(duration)
        }

        fun readFromCache() {
            readsFromCache.incrementAndGet()
        }

        val diskReadsAvgMs: Long
            get() {
                if (readsFromDisk.get() == 0L) {
                    return 0
                }
                return diskReadsTotalMs.get() / readsFromDisk.get()
            }
    }

    private class SimpleStore<T>(private val cache: Cache<Any, T?>, private val id: Any, private val stats: Stats) : Store<T?> {
        override fun load(createIfNotPresent: Supplier<T?>): T? {
            val out = cache.getIfPresent(id)
            if (out != null) {
                stats.readFromCache()
                return out
            }
            val timer = startTimer()
            val value = createIfNotPresent.get()
            stats.readFromDisk(timer.elapsedMillis)
            cache.put(id, value)
            return value
        }
    }

    companion object {
        private val LOG: Logger = getLogger(CachedStoreFactory::class.java)!!
        private val CACHE_SIZE: Int = Integer.getInteger("org.gradle.api.internal.artifacts.ivyservice.resolveengine.store.cacheSize", 100)
        private val CACHE_EXPIRY: Int = Integer.getInteger("org.gradle.api.internal.artifacts.ivyservice.resolveengine.store.cacheExpiryMs", 10000)
    }
}
