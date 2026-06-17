/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.language.cpp.internal

import org.gradle.cache.FileLockManager
import org.gradle.cache.PersistentCache
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory
import org.gradle.internal.concurrent.Stoppable
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.nativeplatform.internal.modulemap.GenerateModuleMapFile
import org.gradle.nativeplatform.internal.modulemap.ModuleMap
import java.io.File
import java.util.function.Supplier

/**
 * This is intended to be temporary, until more metadata can be published and the dependency resolution engine can deal with it. As such, it's not particularly performant or robust.
 */
@ServiceScope(Scope.Build::class)
class NativeDependencyCache(cacheBuilderFactory: GlobalScopedCacheBuilderFactory) : Stoppable {
    private val cache: PersistentCache

    init {
        cache = cacheBuilderFactory.createCacheBuilder("native-dep")
            .withInitialLockMode(FileLockManager.LockMode.OnDemand)
            .open()
    }

    fun getModuleMapFile(moduleMap: ModuleMap): File {
        val hash = moduleMap.getHashCode().toCompactString()
        return cache.useCache<File>(Supplier {
            val dir = File(cache.getBaseDir(), "maps/" + hash + "/" + moduleMap.getModuleName())
            val moduleMapFile = File(dir, "module.modulemap")
            if (!moduleMapFile.isFile()) {
                GenerateModuleMapFile.generateFile(moduleMapFile, moduleMap.getModuleName(), moduleMap.getPublicHeaderPaths())
            }
            moduleMapFile
        })
    }

    override fun stop() {
        cache.close()
    }
}
