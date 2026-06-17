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
package org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts

import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheLockingAccessCoordinator
import org.gradle.internal.file.FileAccessTracker
import org.gradle.internal.hash.HashCode
import org.gradle.util.internal.BuildCommencedTimeProvider
import java.io.File
import java.nio.file.Path

class ReadOnlyModuleArtifactCache(
    persistentCacheFile: String?,
    timeProvider: BuildCommencedTimeProvider?,
    cacheAccessCoordinator: ArtifactCacheLockingAccessCoordinator?,
    fileAccessTracker: FileAccessTracker?,
    commonRootPath: Path?
) : DefaultModuleArtifactCache(persistentCacheFile, timeProvider, cacheAccessCoordinator, fileAccessTracker, commonRootPath) {
    override fun store(key: ArtifactAtRepositoryKey?, artifactFile: File?, moduleDescriptorHash: HashCode?) {
        operationShouldNotHaveBeenCalled()
    }

    override fun storeMissing(key: ArtifactAtRepositoryKey?, attemptedLocations: MutableList<String?>?, descriptorHash: HashCode?) {
        operationShouldNotHaveBeenCalled()
    }

    override fun storeInternal(key: ArtifactAtRepositoryKey?, entry: CachedArtifact?) {
        operationShouldNotHaveBeenCalled()
    }

    override fun clear(key: ArtifactAtRepositoryKey?) {
        // clear is actually called from org.gradle.internal.resource.cached.AbstractCachedIndex.lookup which
        // is a read operation, in case of missing entry, so we can't fail here, but should be a no-op only
    }

    companion object {
        private fun operationShouldNotHaveBeenCalled() {
            throw UnsupportedOperationException("A write operation shouldn't have been called in a read-only cache")
        }
    }
}
