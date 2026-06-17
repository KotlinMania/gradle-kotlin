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

import org.gradle.internal.hash.HashCode
import org.gradle.util.internal.BuildCommencedTimeProvider
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class InMemoryModuleArtifactCache : ModuleArtifactCache {
    private val inMemoryCache: MutableMap<ArtifactAtRepositoryKey?, CachedArtifact?> = ConcurrentHashMap<ArtifactAtRepositoryKey?, CachedArtifact?>()
    private val timeProvider: BuildCommencedTimeProvider
    private val delegate: ModuleArtifactCache?

    constructor(timeProvider: BuildCommencedTimeProvider) {
        this.timeProvider = timeProvider
        this.delegate = null
    }

    constructor(timeProvider: BuildCommencedTimeProvider, delegate: ModuleArtifactCache?) {
        this.timeProvider = timeProvider
        this.delegate = delegate
    }

    override fun store(key: ArtifactAtRepositoryKey?, artifactFile: File?, moduleDescriptorHash: HashCode?) {
        inMemoryCache.put(key, DefaultCachedArtifact(artifactFile, timeProvider.getCurrentTime(), moduleDescriptorHash))
        if (delegate != null) {
            delegate.store(key, artifactFile, moduleDescriptorHash)
        }
    }

    override fun storeMissing(key: ArtifactAtRepositoryKey?, attemptedLocations: MutableList<String?>?, descriptorHash: HashCode?) {
        inMemoryCache.put(key, DefaultCachedArtifact(attemptedLocations, timeProvider.getCurrentTime(), descriptorHash))
        if (delegate != null) {
            delegate.storeMissing(key, attemptedLocations, descriptorHash)
        }
    }

    override fun lookup(key: ArtifactAtRepositoryKey?): CachedArtifact? {
        var cachedArtifact = inMemoryCache.get(key)
        if (cachedArtifact == null && delegate != null) {
            cachedArtifact = delegate.lookup(key)
            if (cachedArtifact != null) {
                inMemoryCache.put(key, cachedArtifact)
            }
        }
        return cachedArtifact
    }

    override fun clear(key: ArtifactAtRepositoryKey?) {
        inMemoryCache.remove(key)
        if (delegate != null) {
            delegate.clear(key)
        }
    }
}
