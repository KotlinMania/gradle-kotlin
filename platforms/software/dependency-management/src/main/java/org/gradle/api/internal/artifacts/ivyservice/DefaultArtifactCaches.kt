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

import com.google.common.collect.ImmutableList
import org.apache.commons.lang3.StringUtils
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.cache.CacheConfigurationsInternal
import org.gradle.api.logging.Logging.getLogger
import org.gradle.cache.CacheCleanupStrategyFactory
import org.gradle.cache.IndexedCache
import org.gradle.cache.UnscopedCacheBuilderFactory
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory
import org.gradle.internal.Factory
import org.gradle.internal.serialize.Serializer
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.util.internal.IncubationLogger.incubatingFeatureUsed
import java.io.Closeable
import java.io.File
import java.util.Optional
import java.util.function.Supplier
import kotlin.concurrent.Volatile

class DefaultArtifactCaches(
    cacheBuilderFactory: GlobalScopedCacheBuilderFactory,
    unscopedCacheBuilderFactory: UnscopedCacheBuilderFactory,
    params: WritableArtifactCacheLockingParameters,
    documentationRegistry: DocumentationRegistry,
    cacheConfigurations: CacheConfigurationsInternal,
    cacheCleanupStrategyFactory: CacheCleanupStrategyFactory
) : ArtifactCachesProvider {
    private val writableCacheMetadata: DefaultArtifactCacheMetadata
    private val readOnlyCacheMetadata: DefaultArtifactCacheMetadata?
    private val writableCacheAccessCoordinator: LateInitWritableArtifactCacheLockingAccessCoordinator
    private val readOnlyCacheAccessCoordinator: ReadOnlyArtifactCacheLockingAccessCoordinator?

    init {
        writableCacheMetadata = DefaultArtifactCacheMetadata(cacheBuilderFactory)
        writableCacheAccessCoordinator = LateInitWritableArtifactCacheLockingAccessCoordinator(org.gradle.internal.Factory {
            WritableArtifactCacheLockingAccessCoordinator(
                unscopedCacheBuilderFactory, writableCacheMetadata,
                params.fileAccessTimeJournal,
                params.usedGradleVersions, cacheConfigurations, cacheCleanupStrategyFactory
            )
        })
        val roCache = System.getenv(ArtifactCachesProvider.Companion.READONLY_CACHE_ENV_VAR)
        if (StringUtils.isNotEmpty(roCache)) {
            incubatingFeatureUsed("Shared read-only dependency cache")
            val baseDir: File? = validateReadOnlyCache(documentationRegistry, File(roCache).getAbsoluteFile())
            if (baseDir != null) {
                readOnlyCacheMetadata = DefaultArtifactCacheMetadata(cacheBuilderFactory, baseDir)
                readOnlyCacheAccessCoordinator = ReadOnlyArtifactCacheLockingAccessCoordinator(unscopedCacheBuilderFactory, readOnlyCacheMetadata)
                LOGGER!!.info("The read-only dependency cache is enabled \nThe {} environment variable was set to {}", ArtifactCachesProvider.Companion.READONLY_CACHE_ENV_VAR, baseDir)
            } else {
                readOnlyCacheMetadata = null
                readOnlyCacheAccessCoordinator = null
            }
        } else {
            readOnlyCacheMetadata = null
            readOnlyCacheAccessCoordinator = null
        }
    }

    override fun getWritableCacheMetadata(): ArtifactCacheMetadata {
        return writableCacheMetadata
    }

    override fun getReadOnlyCacheMetadata(): Optional<ArtifactCacheMetadata?> {
        return Optional.ofNullable<ArtifactCacheMetadata?>(readOnlyCacheMetadata)
    }

    override fun getWritableCacheAccessCoordinator(): ArtifactCacheLockingAccessCoordinator {
        return writableCacheAccessCoordinator
    }

    override fun getReadOnlyCacheAccessCoordinator(): Optional<ArtifactCacheLockingAccessCoordinator?> {
        return Optional.ofNullable<ArtifactCacheLockingAccessCoordinator?>(readOnlyCacheAccessCoordinator)
    }

    override fun getGlobalCacheRoots(): MutableList<File?> {
        return if (readOnlyCacheMetadata == null)
            ImmutableList.of<File?>()
        else
            readOnlyCacheMetadata.getGlobalCacheRoots()
    }

    override fun close() {
        writableCacheAccessCoordinator.close()
        if (readOnlyCacheAccessCoordinator != null) {
            readOnlyCacheAccessCoordinator.close()
        }
    }

    @ServiceScope(Scope.UserHome::class)
    interface WritableArtifactCacheLockingParameters {
        val fileAccessTimeJournal: FileAccessTimeJournal?

        val usedGradleVersions: UsedGradleVersions?
    }

    private class LateInitWritableArtifactCacheLockingAccessCoordinator(private val factory: Factory<WritableArtifactCacheLockingAccessCoordinator?>) : ArtifactCacheLockingAccessCoordinator,
        Closeable {
        @Volatile
        private var delegate: WritableArtifactCacheLockingAccessCoordinator? = null

        fun getDelegate(): WritableArtifactCacheLockingAccessCoordinator? {
            if (delegate == null) {
                synchronized(factory) {
                    if (delegate == null) {
                        delegate = factory.create()
                    }
                }
            }
            return delegate
        }

        override fun close() {
            if (delegate != null) {
                delegate!!.close()
            }
        }

        override fun <T> withFileLock(action: Supplier<out T?>): T? {
            return getDelegate()!!.withFileLock(action)
        }

        override fun withFileLock(action: Runnable) {
            getDelegate()!!.withFileLock(action)
        }

        override fun <T> useCache(action: Supplier<out T?>): T? {
            return getDelegate()!!.useCache(action)
        }

        override fun useCache(action: Runnable) {
            getDelegate()!!.useCache(action)
        }

        override fun <K, V> createCache(cacheName: String, keySerializer: Serializer<K?>, valueSerializer: Serializer<V?>): IndexedCache<K?, V?> {
            return getDelegate()!!.createCache<K?, V?>(cacheName, keySerializer, valueSerializer)
        }
    }

    companion object {
        private val LOGGER = getLogger(DefaultArtifactCaches::class.java)

        private fun validateReadOnlyCache(documentationRegistry: DocumentationRegistry, cacheDir: File): File? {
            if (!cacheDir.exists()) {
                LOGGER!!.warn("The read-only dependency cache is disabled because of a configuration problem:")
                LOGGER.warn("The " + ArtifactCachesProvider.Companion.READONLY_CACHE_ENV_VAR + " environment variable was set to " + cacheDir + " which doesn't exist!")
                return null
            }
            val root = CacheLayout.MODULES.getPath(cacheDir)
            if (!root.exists()) {
                val docLink = documentationRegistry.getDocumentationRecommendationFor("instructions on how to do this", "dependency_resolution", "sub:shared-readonly-cache")
                LOGGER!!.warn("The read-only dependency cache is disabled because of a configuration problem:")
                LOGGER.warn(
                    "Read-only cache is configured but the directory layout isn't expected. You must have a pre-populated " +
                            CacheLayout.MODULES.getKey() + " directory at " + root + " . " + docLink
                )
                return null
            }
            return cacheDir
        }
    }
}
