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
package org.gradle.internal.resource.cached

import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheLockingAccessCoordinator
import org.gradle.cache.IndexedCache
import org.gradle.internal.file.FileAccessTracker
import org.gradle.internal.serialize.Serializer
import java.io.File
import java.util.function.Supplier

abstract class AbstractCachedIndex<K, V : CachedItem?>(
    private val persistentCacheName: String,
    private val keySerializer: Serializer<K?>,
    private val valueSerializer: Serializer<V?>,
    private val cacheAccessCoordinator: ArtifactCacheLockingAccessCoordinator,
    private val fileAccessTracker: FileAccessTracker
) {
    private var indexedCache: IndexedCache<K?, V?>? = null
        get() {
            if (field == null) {
                field = initPersistentCache()
            }
            return field
        }

    private fun initPersistentCache(): IndexedCache<K?, V?> {
        return cacheAccessCoordinator.createCache<K?, V?>(persistentCacheName, keySerializer, valueSerializer)
    }

    open fun lookup(key: K?): V? {
        assertKeyNotNull(key)

        val result: V? = cacheAccessCoordinator.useCache<V?>(Supplier {
            val found = this.indexedCache!!.getIfPresent(key)
            if (found == null) {
                return@useCache null
            } else if (found.isMissing() || found.getCachedFile()!!.exists()) {
                return@useCache found
            } else {
                clear(key)
                return@useCache null
            }
        })

        if (result != null && result.getCachedFile() != null) {
            fileAccessTracker.markAccessed(result.getCachedFile()!!)
        }

        return result
    }

    protected open fun storeInternal(key: K?, entry: V?) {
        cacheAccessCoordinator.useCache(Runnable { this.indexedCache!!.put(key, entry) })
    }

    protected fun assertKeyNotNull(key: K?) {
        requireNotNull(key) { "key cannot be null" }
    }

    protected fun assertArtifactFileNotNull(artifactFile: File) {
        requireNotNull(artifactFile) { "artifactFile cannot be null" }
    }

    open fun clear(key: K?) {
        assertKeyNotNull(key)
        cacheAccessCoordinator.useCache(Runnable { this.indexedCache!!.remove(key) })
    }
}
