/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.internal.jvm.inspection

import org.gradle.cache.CacheBuilder
import org.gradle.cache.FileLockManager
import org.gradle.cache.IndexedCache
import org.gradle.cache.IndexedCacheParameters
import org.gradle.cache.PersistentCache
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.Serializer
import org.gradle.jvm.toolchain.internal.InstallationLocation
import org.jspecify.annotations.NullMarked
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.util.function.Function
import java.util.function.Supplier

/**
 * A [JvmMetadataDetector] that caches the results of the JVM installation metadata in a persistent cache.
 *
 * @implNote This currently only persistently caches the results for JVMs that are auto-provisioned.
 */
@NullMarked
class PersistentJvmMetadataDetector(private val delegate: JvmMetadataDetector, cacheBuilder: CacheBuilder) : JvmMetadataDetector, Closeable {
    private val cache: PersistentCache
    private val indexedCache: IndexedCache<File, JvmInstallationMetadata>

    init {
        this.cache = cacheBuilder.withInitialLockMode(FileLockManager.LockMode.None).open()
        // TODO: This cache should be cleaned up
        val parameters = IndexedCacheParameters.of<File, JvmInstallationMetadata>(
            "metadata",
            FileSerializer(),
            JvmInstallationMetadataSerializer()
        )
        this.indexedCache = cache.createIndexedCache<File, JvmInstallationMetadata>(parameters)
    }

    override fun getMetadata(javaInstallationLocation: InstallationLocation): JvmInstallationMetadata {
        // If the Java installation was auto-provisioned, we can trust that it will not change
        if (javaInstallationLocation.isAutoProvisioned()) {
            return cache.useCache<JvmInstallationMetadata>(Supplier {
                indexedCache.get(
                    javaInstallationLocation.getLocation(),
                    Function { key: File? -> delegate.getMetadata(javaInstallationLocation) })
            })
        } else {
            // Otherwise, we need to reprobe each time
            return delegate.getMetadata(javaInstallationLocation)
        }
    }

    @Throws(IOException::class)
    override fun close() {
        cache.close()
    }

    private class FileSerializer : Serializer<File?> {
        @Throws(Exception::class)
        override fun read(decoder: Decoder): File {
            return File(decoder.readString())
        }

        @Throws(Exception::class)
        override fun write(encoder: Encoder, value: File) {
            encoder.writeString(value.getAbsolutePath())
        }
    }

    private class JvmInstallationMetadataSerializer : Serializer<JvmInstallationMetadata?> {
        @Throws(Exception::class)
        override fun read(decoder: Decoder): JvmInstallationMetadata {
            return JvmInstallationMetadata.Companion.from(
                File(decoder.readString()),
                decoder.readString(),
                decoder.readString(),
                decoder.readString(),
                decoder.readString(),
                decoder.readString(),
                decoder.readString(),
                decoder.readString(),
                decoder.readString()
            )
        }

        @Throws(Exception::class)
        override fun write(encoder: Encoder, value: JvmInstallationMetadata) {
            encoder.writeString(value.getJavaHome().toString())
            encoder.writeString(value.getJavaVersion())
            encoder.writeString(value.getVendor().getRawVendor())
            encoder.writeString(value.getRuntimeName())
            encoder.writeString(value.getRuntimeVersion())
            encoder.writeString(value.getJvmName())
            encoder.writeString(value.getJvmVersion())
            encoder.writeString(value.getJvmVendor())
            encoder.writeString(value.getArchitecture())
        }
    }
}
