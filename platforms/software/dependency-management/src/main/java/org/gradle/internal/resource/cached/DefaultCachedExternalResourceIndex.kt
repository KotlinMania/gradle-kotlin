/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.internal.resource.cached

import com.google.common.annotations.VisibleForTesting
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheLockingAccessCoordinator
import org.gradle.internal.file.FileAccessTracker
import org.gradle.internal.hash.HashCode
import org.gradle.internal.resource.metadata.DefaultExternalResourceMetaData
import org.gradle.internal.resource.metadata.ExternalResourceMetaData
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.Serializer
import org.gradle.util.internal.BuildCommencedTimeProvider
import java.io.File
import java.io.IOException
import java.io.Serializable
import java.net.URI
import java.nio.file.Path

open class DefaultCachedExternalResourceIndex<K : Serializable?>(
    persistentCacheFile: String?,
    keySerializer: Serializer<K?>?,
    private val timeProvider: BuildCommencedTimeProvider,
    cacheAccessCoordinator: ArtifactCacheLockingAccessCoordinator?,
    fileAccessTracker: FileAccessTracker?,
    commonRootPath: Path
) : AbstractCachedIndex<K?, CachedExternalResource?>(persistentCacheFile, keySerializer, CachedExternalResourceSerializer(commonRootPath), cacheAccessCoordinator, fileAccessTracker),
    CachedExternalResourceIndex<K?> {
    private fun createEntry(artifactFile: File?, externalResourceMetaData: ExternalResourceMetaData?): DefaultCachedExternalResource {
        return DefaultCachedExternalResource(artifactFile, timeProvider.getCurrentTime(), externalResourceMetaData)
    }

    override fun store(key: K?, artifactFile: File?, externalResourceMetaData: ExternalResourceMetaData?) {
        assertArtifactFileNotNull(artifactFile)
        assertKeyNotNull(key)

        storeInternal(key, createEntry(artifactFile, externalResourceMetaData))
    }

    override fun storeMissing(key: K?) {
        storeInternal(key, DefaultCachedExternalResource(timeProvider.getCurrentTime()))
    }

    @VisibleForTesting
    internal class CachedExternalResourceSerializer(private val commonRootPath: Path) : Serializer<CachedExternalResource?> {
        override fun equals(o: Any?): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }
            val that = o as CachedExternalResourceSerializer
            return commonRootPath == that.commonRootPath
        }

        override fun hashCode(): Int {
            return commonRootPath.hashCode()
        }

        @Throws(Exception::class)
        override fun read(decoder: Decoder): CachedExternalResource? {
            var cachedFile: File? = null
            if (decoder.readBoolean()) {
                cachedFile = denormalizeAndResolveFilePath(decoder.readString()!!)
            }
            val cachedAt = decoder.readLong()
            var metaData: ExternalResourceMetaData? = null
            if (decoder.readBoolean()) {
                val uri = URI(decoder.readString())
                var lastModified: Long = 0
                if (decoder.readBoolean()) {
                    lastModified = decoder.readLong()
                }
                val contentType = decoder.readNullableString()
                val contentLength = decoder.readSmallLong()
                val etag = decoder.readNullableString()
                var sha1: HashCode? = null
                if (decoder.readBoolean()) {
                    sha1 = HashCode.fromString(decoder.readString()!!)
                }
                metaData = DefaultExternalResourceMetaData(uri, lastModified, contentLength, contentType, etag, sha1)
            }
            return DefaultCachedExternalResource(cachedFile, cachedAt, metaData)
        }

        @Throws(Exception::class)
        override fun write(encoder: Encoder, value: CachedExternalResource) {
            encoder.writeBoolean(value.getCachedFile() != null)
            if (value.getCachedFile() != null) {
                encoder.writeString(relativizeAndNormalizeFilePath(value.getCachedFile()!!))
            }
            encoder.writeLong(value.getCachedAt())
            val metaData = value.getExternalResourceMetaData()
            encoder.writeBoolean(metaData != null)
            if (metaData != null) {
                encoder.writeString(metaData.getLocation().toASCIIString())
                encoder.writeBoolean(metaData.getLastModified() != null)
                if (metaData.getLastModified() != null) {
                    encoder.writeLong(metaData.getLastModified()!!.getTime())
                }
                encoder.writeNullableString(metaData.getContentType())
                encoder.writeSmallLong(metaData.getContentLength())
                encoder.writeNullableString(metaData.getEtag())
                encoder.writeBoolean(metaData.getSha1() != null)
                if (metaData.getSha1() != null) {
                    encoder.writeString(metaData.getSha1().toString())
                }
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
}
