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
package org.gradle.api.internal.artifacts.ivyservice.modulecache

import com.google.common.collect.Interner
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.capability.CapabilitySelectorSerializer
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheLockingAccessCoordinator
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheMetadata
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepository
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.AttributeContainerSerializer
import org.gradle.api.internal.artifacts.repositories.metadata.IvyMutableModuleMetadataFactory
import org.gradle.api.internal.artifacts.repositories.metadata.MavenMutableModuleMetadataFactory
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata
import org.gradle.internal.hash.ChecksumService
import org.gradle.util.internal.BuildCommencedTimeProvider

class ReadOnlyModuleMetadataCache(
    timeProvider: BuildCommencedTimeProvider?,
    cacheAccessCoordinator: ArtifactCacheLockingAccessCoordinator?,
    artifactCacheMetadata: ArtifactCacheMetadata,
    moduleIdentifierFactory: ImmutableModuleIdentifierFactory?,
    attributeContainerSerializer: AttributeContainerSerializer?,
    capabilitySelectorSerializer: CapabilitySelectorSerializer?,
    mavenMetadataFactory: MavenMutableModuleMetadataFactory?,
    ivyMetadataFactory: IvyMutableModuleMetadataFactory?,
    stringInterner: Interner<String?>?,
    moduleSourcesSerializer: ModuleSourcesSerializer?,
    checksumService: ChecksumService?
) : PersistentModuleMetadataCache(
    timeProvider,
    cacheAccessCoordinator,
    artifactCacheMetadata,
    moduleIdentifierFactory,
    attributeContainerSerializer,
    capabilitySelectorSerializer,
    mavenMetadataFactory,
    ivyMetadataFactory,
    stringInterner,
    moduleSourcesSerializer,
    checksumService
) {
    override fun store(key: ModuleComponentAtRepositoryKey?, entry: ModuleMetadataCacheEntry?, cachedMetadata: ModuleMetadataCache.CachedMetadata?): ModuleMetadataCache.CachedMetadata? {
        Object > operationShouldNotHaveBeenCalled<Any?>()
        return cachedMetadata
    }

    override fun cacheMissing(repository: ModuleComponentRepository<*>?, id: ModuleComponentIdentifier?): ModuleMetadataCache.CachedMetadata {
        return
        CachedMetadata > operationShouldNotHaveBeenCalled<Any?>()
    }

    override fun cacheMetaData(repository: ModuleComponentRepository<*>?, id: ModuleComponentIdentifier?, metadata: ModuleComponentResolveMetadata?): ModuleMetadataCache.CachedMetadata {
        return
        CachedMetadata > operationShouldNotHaveBeenCalled<Any?>()
    }

    companion object {
        //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
        private fun <T> operationShouldNotHaveBeenCalled(): T? {
            throw UnsupportedOperationException("A write operation shouldn't have been called in a read-only cache")
        }
    }
}
