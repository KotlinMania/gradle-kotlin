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

import com.google.common.annotations.VisibleForTesting
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheLockingAccessCoordinator
import org.gradle.api.internal.artifacts.metadata.ComponentArtifactIdentifierSerializer
import org.gradle.api.internal.artifacts.metadata.ModuleComponentFileArtifactIdentifierSerializer
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactIdentifier
import org.gradle.internal.component.external.model.ModuleComponentFileArtifactIdentifier
import org.gradle.internal.file.FileAccessTracker
import org.gradle.internal.hash.HashCode
import org.gradle.internal.resource.cached.AbstractCachedIndex
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.DefaultSerializerRegistry
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.Serializer
import org.gradle.util.internal.BuildCommencedTimeProvider
import java.io.File
import java.io.IOException
import java.nio.file.Path

open class DefaultModuleArtifactCache(
    persistentCacheFile: String,
    private val timeProvider: BuildCommencedTimeProvider,
    cacheAccessCoordinator: ArtifactCacheLockingAccessCoordinator,
    fileAccessTracker: FileAccessTracker,
    commonRootPath: Path
) : AbstractCachedIndex<ArtifactAtRepositoryKey?, CachedArtifact?>(persistentCacheFile, KEY_SERIALIZER, CachedArtifactSerializer(commonRootPath), cacheAccessCoordinator, fileAccessTracker),
    ModuleArtifactCache {
    override fun store(key: ArtifactAtRepositoryKey?, artifactFile: File, moduleDescriptorHash: HashCode?) {
        assertArtifactFileNotNull(artifactFile)
        assertKeyNotNull(key)
        storeInternal(key, createEntry(artifactFile, moduleDescriptorHash))
    }

    private fun createEntry(artifactFile: File?, moduleDescriptorHash: HashCode?): DefaultCachedArtifact {
        return DefaultCachedArtifact(artifactFile, timeProvider.getCurrentTime(), moduleDescriptorHash)
    }

    override fun storeMissing(key: ArtifactAtRepositoryKey?, attemptedLocations: MutableList<String?>?, descriptorHash: HashCode?) {
        storeInternal(key, createMissingEntry(attemptedLocations, descriptorHash))
    }

    private fun createMissingEntry(attemptedLocations: MutableList<String?>?, descriptorHash: HashCode?): CachedArtifact {
        return DefaultCachedArtifact(attemptedLocations, timeProvider.getCurrentTime(), descriptorHash)
    }

    override fun lookup(key: ArtifactAtRepositoryKey?): CachedArtifact? {
        assertKeyNotNull(key)

        return super.lookup(key)
    }

    private class ArtifactAtRepositoryKeySerializer(private val artifactIdSerializer: Serializer<ComponentArtifactIdentifier?>) : Serializer<ArtifactAtRepositoryKey?> {
        @Throws(Exception::class)
        override fun write(encoder: Encoder, value: ArtifactAtRepositoryKey) {
            encoder.writeString(value.getRepositoryId())
            artifactIdSerializer.write(encoder, value.getArtifactId())
        }

        @Throws(Exception::class)
        override fun read(decoder: Decoder): ArtifactAtRepositoryKey {
            val repositoryId = decoder.readString()
            val artifactIdentifier = artifactIdSerializer.read(decoder)
            return ArtifactAtRepositoryKey(repositoryId, artifactIdentifier)
        }
    }

    @VisibleForTesting
    internal class CachedArtifactSerializer(private val commonRootPath: Path) : Serializer<CachedArtifact?> {
        override fun equals(o: Any?): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }
            val that = o as CachedArtifactSerializer
            return commonRootPath == that.commonRootPath
        }

        override fun hashCode(): Int {
            return commonRootPath.hashCode()
        }

        @Throws(Exception::class)
        override fun write(encoder: Encoder, value: CachedArtifact) {
            encoder.writeBoolean(value.isMissing)
            encoder.writeLong(value.cachedAt)
            val hash = value.getDescriptorHash().toByteArray()
            encoder.writeBinary(hash)
            if (!value.isMissing) {
                encoder.writeString(relativizeAndNormalizeFilePath(value.cachedFile))
            } else {
                encoder.writeSmallInt(value.attemptedLocations().size)
                for (location in value.attemptedLocations()) {
                    encoder.writeString(location)
                }
            }
        }

        @Throws(Exception::class)
        override fun read(decoder: Decoder): CachedArtifact? {
            val isMissing = decoder.readBoolean()
            val createTimestamp = decoder.readLong()
            val encodedHash = decoder.readBinary()
            val hash = HashCode.fromBytes(encodedHash!!)
            if (!isMissing) {
                return DefaultCachedArtifact(denormalizeAndResolveFilePath(decoder.readString()!!), createTimestamp, hash)
            } else {
                val size = decoder.readSmallInt()
                val attempted: MutableList<String?> = ArrayList<String?>(size)
                for (i in 0..<size) {
                    attempted.add(decoder.readString())
                }
                return DefaultCachedArtifact(attempted, createTimestamp, hash)
            }
        }

        private fun relativizeAndNormalizeFilePath(cachedFile: File): String {
            val filePath = cachedFile.toPath()
            assert(filePath.startsWith(commonRootPath)) { "Attempting to cache file " + filePath + " not in " + commonRootPath }
            val systemDependentPath = commonRootPath.relativize(filePath).toString()
            if (filePath.getFileSystem().getSeparator() != "/") {
                return systemDependentPath.replace(filePath.getFileSystem().getSeparator(), "/")
            }
            return systemDependentPath
        }

        @Throws(IOException::class)
        private fun denormalizeAndResolveFilePath(relativePath: String): File {
            var relativePath = relativePath
            if (commonRootPath.getFileSystem().getSeparator() != "/") {
                relativePath = relativePath.replace("/", commonRootPath.getFileSystem().getSeparator())
            }
            return commonRootPath.resolve(relativePath).toFile()
        }
    }

    companion object {
        private val KEY_SERIALIZER: ArtifactAtRepositoryKeySerializer = keySerializer()
        protected fun keySerializer(): ArtifactAtRepositoryKeySerializer {
            val serializerRegistry = DefaultSerializerRegistry()
            serializerRegistry.register<DefaultModuleComponentArtifactIdentifier?>(DefaultModuleComponentArtifactIdentifier::class.java, ComponentArtifactIdentifierSerializer())
            serializerRegistry.register<ModuleComponentFileArtifactIdentifier?>(ModuleComponentFileArtifactIdentifier::class.java, ModuleComponentFileArtifactIdentifierSerializer())
            return DefaultModuleArtifactCache.ArtifactAtRepositoryKeySerializer(serializerRegistry.build<ComponentArtifactIdentifier?>(ComponentArtifactIdentifier::class.java)!!)
        }
    }
}
