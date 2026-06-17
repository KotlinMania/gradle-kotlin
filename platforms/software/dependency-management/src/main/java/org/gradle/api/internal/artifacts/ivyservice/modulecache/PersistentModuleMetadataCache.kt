/*
 * Copyright 2011 the original author or authors.
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

import com.google.common.base.Objects
import com.google.common.collect.Interner
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.capability.CapabilitySelectorSerializer
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheLockingAccessCoordinator
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheMetadata
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.AttributeContainerSerializer
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentIdentifierSerializer
import org.gradle.api.internal.artifacts.repositories.metadata.IvyMutableModuleMetadataFactory
import org.gradle.api.internal.artifacts.repositories.metadata.MavenMutableModuleMetadataFactory
import org.gradle.cache.IndexedCache
import org.gradle.internal.hash.ChecksumService
import org.gradle.internal.resource.local.DefaultPathKeyFileStore
import org.gradle.internal.serialize.AbstractSerializer
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.util.internal.BuildCommencedTimeProvider
import java.util.function.Supplier

open class PersistentModuleMetadataCache(
    timeProvider: BuildCommencedTimeProvider?,
    private val artifactCacheLockingManager: ArtifactCacheLockingAccessCoordinator,
    artifactCacheMetadata: ArtifactCacheMetadata,
    moduleIdentifierFactory: ImmutableModuleIdentifierFactory?,
    attributeContainerSerializer: AttributeContainerSerializer?,
    capabilitySelectorSerializer: CapabilitySelectorSerializer?,
    mavenMetadataFactory: MavenMutableModuleMetadataFactory?,
    ivyMetadataFactory: IvyMutableModuleMetadataFactory?,
    stringInterner: Interner<String?>?,
    moduleSourcesSerializer: ModuleSourcesSerializer?,
    checksumService: ChecksumService
) : AbstractModuleMetadataCache(timeProvider) {
    private var cache: IndexedCache<ModuleComponentAtRepositoryKey?, ModuleMetadataCacheEntry?>? = null
        get() {
            if (field == null) {
                field = initCache()
            }
            return field
        }
    private val moduleMetadataStore: ModuleMetadataStore

    init {
        moduleMetadataStore = ModuleMetadataStore(
            DefaultPathKeyFileStore(checksumService, artifactCacheMetadata.metaDataStoreDirectory),
            ModuleMetadataSerializer(attributeContainerSerializer, capabilitySelectorSerializer, mavenMetadataFactory, ivyMetadataFactory, moduleSourcesSerializer),
            moduleIdentifierFactory,
            stringInterner
        )
    }

    private fun initCache(): IndexedCache<ModuleComponentAtRepositoryKey?, ModuleMetadataCacheEntry?>? {
        return artifactCacheLockingManager.createCache<ModuleComponentAtRepositoryKey?, ModuleMetadataCacheEntry?>("module-metadata", RevisionKeySerializer(), ModuleMetadataCacheEntrySerializer())
    }

    protected override fun get(key: ModuleComponentAtRepositoryKey): ModuleMetadataCache.CachedMetadata {
        val cache: IndexedCache<ModuleComponentAtRepositoryKey?, ModuleMetadataCacheEntry?> =
            this.cache!!
        return artifactCacheLockingManager.useCache<DefaultCachedMetadata>(Supplier {
            val entry = cache.getIfPresent(key)
            if (entry == null) {
                return@useCache null
            }
            if (entry.isMissing()) {
                return@useCache DefaultCachedMetadata(entry, null, timeProvider)
            }
            val metadata = moduleMetadataStore.getModuleDescriptor(key)
            if (metadata == null) {
                // Descriptor file has been deleted - ignore the entry
                cache.remove(key)
                return@useCache null
            }
            DefaultCachedMetadata(entry, entry.configure(metadata), timeProvider)
        })
    }

    protected override fun store(key: ModuleComponentAtRepositoryKey, entry: ModuleMetadataCacheEntry, cachedMetadata: ModuleMetadataCache.CachedMetadata): ModuleMetadataCache.CachedMetadata {
        if (entry.isMissing()) {
            this.cache!!.put(key, entry)
        } else {
            // Need to lock the cache in order to write to the module metadata store
            artifactCacheLockingManager.useCache(Runnable {
                val metadata = cachedMetadata.getMetadata()
                moduleMetadataStore.putModuleDescriptor(key, metadata)
                this.cache!!.put(key, entry)
            })
        }
        return cachedMetadata
    }

    private class RevisionKeySerializer : AbstractSerializer<ModuleComponentAtRepositoryKey?>() {
        private val componentIdSerializer = ComponentIdentifierSerializer()

        @Throws(Exception::class)
        override fun write(encoder: Encoder, value: ModuleComponentAtRepositoryKey) {
            encoder.writeString(value.getRepositoryId())
            componentIdSerializer.write(encoder, value.getComponentId())
        }

        @Throws(Exception::class)
        override fun read(decoder: Decoder): ModuleComponentAtRepositoryKey {
            val resolverId = decoder.readString()
            val identifier = componentIdSerializer.read(decoder) as ModuleComponentIdentifier
            return ModuleComponentAtRepositoryKey(resolverId, identifier)
        }

        public override fun equals(obj: Any?): Boolean {
            if (!super.equals(obj)) {
                return false
            }

            val rhs = obj as RevisionKeySerializer
            return Objects.equal(componentIdSerializer, rhs.componentIdSerializer)
        }

        public override fun hashCode(): Int {
            return Objects.hashCode(super.hashCode(), componentIdSerializer)
        }
    }
}
