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

import com.google.common.base.Objects
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheLockingAccessCoordinator
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentIdentifierSerializer
import org.gradle.api.internal.artifacts.metadata.ComponentArtifactMetadataSerializer
import org.gradle.cache.IndexedCache
import org.gradle.internal.component.model.ComponentArtifactMetadata
import org.gradle.internal.hash.HashCode
import org.gradle.internal.serialize.AbstractSerializer
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.Serializer
import org.gradle.internal.serialize.SetSerializer
import org.gradle.util.internal.BuildCommencedTimeProvider

open class DefaultModuleArtifactsCache(timeProvider: BuildCommencedTimeProvider?, private val cacheAccessCoordinator: ArtifactCacheLockingAccessCoordinator) : AbstractArtifactsCache(timeProvider) {
    private var cache: IndexedCache<ArtifactsAtRepositoryKey?, ModuleArtifactsCacheEntry?>? = null
        get() {
            if (field == null) {
                field = initCache()
            }
            return field
        }

    private fun initCache(): IndexedCache<ArtifactsAtRepositoryKey?, ModuleArtifactsCacheEntry?>? {
        return cacheAccessCoordinator.createCache<ArtifactsAtRepositoryKey?, ModuleArtifactsCacheEntry?>("module-artifacts", ModuleArtifactsKeySerializer(), ModuleArtifactsCacheEntrySerializer())
    }

    protected override fun store(key: ArtifactsAtRepositoryKey, entry: ModuleArtifactsCacheEntry) {
        this.cache!!.put(key, entry)
    }

    protected override fun get(key: ArtifactsAtRepositoryKey): ModuleArtifactsCacheEntry? {
        return this.cache!!.getIfPresent(key)
    }

    private class ModuleArtifactsKeySerializer : AbstractSerializer<ArtifactsAtRepositoryKey?>() {
        private val identifierSerializer = ComponentIdentifierSerializer()

        @Throws(Exception::class)
        override fun write(encoder: Encoder, value: ArtifactsAtRepositoryKey) {
            encoder.writeString(value.repositoryId)
            identifierSerializer.write(encoder, value.componentId)
            encoder.writeString(value.context)
        }

        @Throws(Exception::class)
        override fun read(decoder: Decoder): ArtifactsAtRepositoryKey {
            val resolverId = decoder.readString()
            val componentId = identifierSerializer.read(decoder)
            val context = decoder.readString()
            return ArtifactsAtRepositoryKey(resolverId, componentId, context)
        }

        public override fun equals(obj: Any?): Boolean {
            if (!super.equals(obj)) {
                return false
            }

            val rhs = obj as ModuleArtifactsKeySerializer
            return Objects.equal(identifierSerializer, rhs.identifierSerializer)
        }

        public override fun hashCode(): Int {
            return Objects.hashCode(super.hashCode(), identifierSerializer)
        }
    }

    private class ModuleArtifactsCacheEntrySerializer : AbstractSerializer<ModuleArtifactsCacheEntry?>() {
        private val artifactsSerializer: Serializer<MutableSet<ComponentArtifactMetadata?>?> = SetSerializer<ComponentArtifactMetadata?>(ComponentArtifactMetadataSerializer())

        @Throws(Exception::class)
        override fun write(encoder: Encoder, value: ModuleArtifactsCacheEntry) {
            encoder.writeLong(value.createTimestamp)
            val hash = value.moduleDescriptorHash.toByteArray()
            encoder.writeBinary(hash)
            artifactsSerializer.write(encoder, value.artifacts)
        }

        @Throws(Exception::class)
        override fun read(decoder: Decoder): ModuleArtifactsCacheEntry {
            val createTimestamp = decoder.readLong()
            val encodedHash = decoder.readBinary()
            val hash = HashCode.fromBytes(encodedHash!!)
            val artifacts = artifactsSerializer.read(decoder)
            return ModuleArtifactsCacheEntry(artifacts, createTimestamp, hash)
        }

        public override fun equals(obj: Any?): Boolean {
            if (!super.equals(obj)) {
                return false
            }

            val rhs = obj as ModuleArtifactsCacheEntrySerializer
            return Objects.equal(artifactsSerializer, rhs.artifactsSerializer)
        }

        public override fun hashCode(): Int {
            return Objects.hashCode(super.hashCode(), artifactsSerializer)
        }
    }
}
