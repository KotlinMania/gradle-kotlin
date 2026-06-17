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

import org.gradle.util.internal.BuildCommencedTimeProvider
import java.util.concurrent.ConcurrentHashMap

class InMemoryModuleArtifactsCache : AbstractArtifactsCache {
    private val inMemoryCache: MutableMap<ArtifactsAtRepositoryKey?, ModuleArtifactsCacheEntry?> = ConcurrentHashMap<ArtifactsAtRepositoryKey?, ModuleArtifactsCacheEntry?>()
    private val delegate: AbstractArtifactsCache?

    constructor(timeProvider: BuildCommencedTimeProvider?) : super(timeProvider) {
        this.delegate = null
    }

    constructor(timeProvider: BuildCommencedTimeProvider?, delegate: AbstractArtifactsCache?) : super(timeProvider) {
        this.delegate = delegate
    }

    protected override fun store(key: ArtifactsAtRepositoryKey?, entry: ModuleArtifactsCacheEntry?) {
        inMemoryCache.put(key, entry)
        if (delegate != null) {
            delegate.store(key, entry)
        }
    }

    protected override fun get(key: ArtifactsAtRepositoryKey?): ModuleArtifactsCacheEntry? {
        var entry = inMemoryCache.get(key)
        if (entry == null && delegate != null) {
            entry = delegate.get(key)
            if (entry != null) {
                inMemoryCache.put(key, entry)
            }
        }
        return entry
    }
}
