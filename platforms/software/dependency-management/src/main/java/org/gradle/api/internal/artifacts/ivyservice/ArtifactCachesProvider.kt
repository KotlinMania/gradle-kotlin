/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.cache.GlobalCache
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import java.io.Closeable
import java.util.Optional
import java.util.function.BiFunction
import java.util.function.Function

@ServiceScope(Scope.UserHome::class)
interface ArtifactCachesProvider : Closeable, GlobalCache {
    val writableCacheMetadata: ArtifactCacheMetadata?
    val readOnlyCacheMetadata: Optional<ArtifactCacheMetadata?>?

    val writableCacheAccessCoordinator: ArtifactCacheLockingAccessCoordinator?
    val readOnlyCacheAccessCoordinator: Optional<ArtifactCacheLockingAccessCoordinator?>?

    fun <T> withWritableCache(function: BiFunction<in ArtifactCacheMetadata?, in ArtifactCacheLockingAccessCoordinator?, T?>): T? {
        return function.apply(this.writableCacheMetadata, this.writableCacheAccessCoordinator)
    }

    fun <T> withReadOnlyCache(function: BiFunction<in ArtifactCacheMetadata?, in ArtifactCacheLockingAccessCoordinator?, T?>): Optional<T?> {
        return this.readOnlyCacheMetadata.map<T?>(Function { artifactCacheMetadata: ArtifactCacheMetadata? -> function.apply(artifactCacheMetadata, this.readOnlyCacheAccessCoordinator.get()) })
    }

    companion object {
        const val READONLY_CACHE_ENV_VAR: String = "GRADLE_RO_DEP_CACHE"
    }
}
