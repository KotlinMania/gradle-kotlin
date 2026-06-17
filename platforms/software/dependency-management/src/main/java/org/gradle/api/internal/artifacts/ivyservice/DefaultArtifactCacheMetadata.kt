/*
 * Copyright 2014 the original author or authors.
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

import com.google.common.collect.ImmutableList
import org.gradle.cache.GlobalCache
import org.gradle.cache.internal.CacheVersion
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory
import java.io.File

class DefaultArtifactCacheMetadata(cacheBuilderFactory: GlobalScopedCacheBuilderFactory) : ArtifactCacheMetadata, GlobalCache {
    private val cacheDir: File?
    private val baseDir: File

    init {
        this.baseDir = cacheBuilderFactory.getRootDir()
        this.cacheDir = cacheBuilderFactory.baseDirForCrossVersionCache(CacheLayout.MODULES.getKey())
    }

    constructor(cacheBuilderFactory: GlobalScopedCacheBuilderFactory, baseDir: File?) : this(cacheBuilderFactory.createCacheBuilderFactory(baseDir))

    override fun getCacheDir(): File? {
        return cacheDir
    }

    override fun getFileStoreDirectory(): File {
        return createCacheRelativeDir(CacheLayout.FILE_STORE)
    }

    override fun getExternalResourcesStoreDirectory(): File {
        return createCacheRelativeDir(CacheLayout.RESOURCES)
    }

    override fun getMetaDataStoreDirectory(): File {
        return File(createCacheRelativeDir(CacheLayout.META_DATA), "descriptors")
    }

    private fun createCacheRelativeDir(cacheLayout: CacheLayout): File {
        return cacheLayout.getPath(getCacheDir())
    }

    override fun getGlobalCacheRoots(): MutableList<File?> {
        return ImmutableList.of<File?>(baseDir)
    }

    companion object {
        val CACHE_LAYOUT_VERSION: CacheVersion = CacheLayout.META_DATA.getVersion()
    }
}
