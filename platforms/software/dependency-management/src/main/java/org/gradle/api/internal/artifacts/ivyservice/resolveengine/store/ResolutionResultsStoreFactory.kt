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

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.GraphStructure
import org.gradle.api.internal.file.temp.TemporaryFileProvider
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging.getLogger
import org.gradle.cache.internal.Store
import org.gradle.internal.concurrent.CompositeStoppable
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.internal.time.Time.startTimer
import java.io.Closeable
import java.util.concurrent.atomic.AtomicInteger

@ServiceScope(Scope.BuildTree::class)
class ResolutionResultsStoreFactory
/**
 * @param temp - Provider of temporary files.
 * @param maxSize - indicates the approx. maximum size of the binary store that will trigger rolling of the file
 */ internal constructor(private val temp: TemporaryFileProvider, private val maxSize: Int) : Closeable {
    private var graphStructureCache: CachedStoreFactory<GraphStructure>? = null

    private val storeSetBaseId = AtomicInteger()

    constructor(temp: TemporaryFileProvider) : this(temp, DEFAULT_MAX_SIZE)

    private val stores: MutableMap<String, DefaultBinaryStore> = HashMap<String, DefaultBinaryStore>()
    private val cleanUpLater = CompositeStoppable()

    @Synchronized
    private fun createBinaryStore(storeKey: String): DefaultBinaryStore {
        var store = stores.get(storeKey)
        if (store == null || isFull(store) || store.isInUse()) {
            val storeFile = temp.createTemporaryFile("gradle", ".bin")
            storeFile.deleteOnExit()
            store = DefaultBinaryStore(storeFile)
            stores.put(storeKey, store)
            cleanUpLater.add(store)
        }
        return store
    }

    @Synchronized
    private fun getGraphStructureCache(): CachedStoreFactory<GraphStructure> {
        if (graphStructureCache == null) {
            graphStructureCache = CachedStoreFactory<GraphStructure>("Resolution result")
            cleanUpLater.add(graphStructureCache!!)
        }
        return graphStructureCache!!
    }

    fun createStoreSet(): StoreSet {
        return object : StoreSet {
            val storeSetId: Int = storeSetBaseId.getAndIncrement()
            var binaryStoreId: Int = 0
            override fun nextBinaryStore(): DefaultBinaryStore {
                //one binary store per id+threadId
                val storeKey = Thread.currentThread().getId().toString() + "-" + binaryStoreId++
                return createBinaryStore(storeKey)
            }

            override fun graphStructureCache(): Store<GraphStructure> {
                return getGraphStructureCache().createCachedStore(storeSetId)
            }
        }
    }

    //offset based implementation is only safe up to certain figure
    //because of the int max value
    //for large streams/files (huge builds), we need to roll the file
    //otherwise the stream.size() returns max integer and the offset is no longer correct
    private fun isFull(store: DefaultBinaryStore): Boolean {
        return store.getSize() > maxSize
    }

    override fun close() {
        try {
            val clock = startTimer()
            cleanUpLater.stop()
            LOG.debug("Deleted {} resolution results binary files in {}", stores.size, clock.elapsed)
        } finally {
            graphStructureCache = null
            stores.clear()
        }
    }

    companion object {
        private val LOG: Logger = getLogger(ResolutionResultsStoreFactory::class.java)!!
        private const val DEFAULT_MAX_SIZE = 2000000000 //2 gigs
    }
}
