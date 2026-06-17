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
package org.gradle.api.internal.tasks.compile.incremental.cache

import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassAnalysis
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassSetAnalysisData
import org.gradle.cache.Cache
import org.gradle.cache.FileLockManager
import org.gradle.cache.IndexedCacheParameters
import org.gradle.cache.PersistentCache
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory
import org.gradle.cache.internal.MinimalPersistentCache
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory
import org.gradle.internal.hash.HashCode
import org.gradle.internal.serialize.HashCodeSerializer
import org.gradle.internal.serialize.HierarchicalNameSerializer
import java.io.Closeable
import java.util.function.Supplier

class UserHomeScopedCompileCaches(cacheBuilderFactory: GlobalScopedCacheBuilderFactory, inMemoryCacheDecoratorFactory: InMemoryCacheDecoratorFactory, interner: StringInterner) : GeneralCompileCaches,
    Closeable {
    private val classpathEntrySnapshotCache: Cache<HashCode?, ClassSetAnalysisData?>
    private val cache: PersistentCache
    private val classAnalysisCache: Cache<HashCode?, ClassAnalysis?>

    init {
        cache = cacheBuilderFactory
            .createCacheBuilder("javaCompile")
            .withDisplayName("Java compile cache")
            .withInitialLockMode(FileLockManager.LockMode.OnDemand)
            .open()
        val jarCacheParameters: IndexedCacheParameters<HashCode?, ClassSetAnalysisData?> = IndexedCacheParameters.of<HashCode?, ClassSetAnalysisData?>(
            "jarAnalysis",
            HashCodeSerializer(),
            ClassSetAnalysisData.Serializer(Supplier { HierarchicalNameSerializer(interner) })
        ).withCacheDecorator(inMemoryCacheDecoratorFactory.decorator(20000, true))
        this.classpathEntrySnapshotCache = MinimalPersistentCache<HashCode?, ClassSetAnalysisData?>(cache.createIndexedCache<HashCode?, ClassSetAnalysisData?>(jarCacheParameters))

        val classCacheParameters: IndexedCacheParameters<HashCode?, ClassAnalysis?> = IndexedCacheParameters.of<HashCode?, ClassAnalysis?>(
            "classAnalysis",
            HashCodeSerializer(),
            ClassAnalysis.Serializer(interner)
        ).withCacheDecorator(inMemoryCacheDecoratorFactory.decorator(400000, true))
        this.classAnalysisCache = MinimalPersistentCache<HashCode?, ClassAnalysis?>(cache.createIndexedCache<HashCode?, ClassAnalysis?>(classCacheParameters))
    }

    override fun close() {
        cache.close()
    }

    override fun getClassSetAnalysisCache(): Cache<HashCode?, ClassSetAnalysisData?> {
        return classpathEntrySnapshotCache
    }

    override fun getClassAnalysisCache(): Cache<HashCode?, ClassAnalysis?> {
        return classAnalysisCache
    }
}
