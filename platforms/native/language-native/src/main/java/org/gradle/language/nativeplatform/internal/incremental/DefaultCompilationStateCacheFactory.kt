/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.language.nativeplatform.internal.incremental

import org.gradle.cache.FileLockManager
import org.gradle.cache.IndexedCache
import org.gradle.cache.IndexedCacheParameters
import org.gradle.cache.ObjectHolder
import org.gradle.cache.PersistentCache
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory
import org.gradle.cache.scopes.BuildScopedCacheBuilderFactory
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import java.io.Closeable

@ServiceScope(Scope.Build::class)
class DefaultCompilationStateCacheFactory(cacheBuilderFactory: BuildScopedCacheBuilderFactory, inMemoryCacheDecoratorFactory: InMemoryCacheDecoratorFactory) : CompilationStateCacheFactory, Closeable {
    private val compilationStateIndexedCache: IndexedCache<String?, CompilationState>
    private val cache: PersistentCache

    init {
        cache = cacheBuilderFactory
            .createCacheBuilder("nativeCompile")
            .withDisplayName("native compile cache")
            .withInitialLockMode(FileLockManager.LockMode.OnDemand)
            .open()
        val parameters: IndexedCacheParameters<String?, CompilationState?> = IndexedCacheParameters.of<String?, CompilationState?>("nativeCompile", String::class.java, CompilationStateSerializer())
            .withCacheDecorator(inMemoryCacheDecoratorFactory.decorator(2000, false))

        compilationStateIndexedCache = cache.createIndexedCache<String?, CompilationState?>(parameters)
    }

    override fun close() {
        cache.close()
    }

    override fun create(taskPath: String): ObjectHolder<CompilationState?> {
        return SimplePersistentObjectHolder(taskPath, compilationStateIndexedCache)
    }

    private class SimplePersistentObjectHolder(private val taskPath: String, private val compilationStateIndexedCache: IndexedCache<String?, CompilationState>) : ObjectHolder<CompilationState?> {
        override fun get(): CompilationState {
            return compilationStateIndexedCache.getIfPresent(taskPath)!!
        }

        override fun set(newValue: CompilationState) {
            compilationStateIndexedCache.put(taskPath, newValue)
        }

        override fun update(updateAction: ObjectHolder.UpdateAction<CompilationState?>): CompilationState {
            throw UnsupportedOperationException()
        }

        override fun maybeUpdate(updateAction: ObjectHolder.UpdateAction<CompilationState?>): CompilationState {
            throw UnsupportedOperationException()
        }
    }
}
