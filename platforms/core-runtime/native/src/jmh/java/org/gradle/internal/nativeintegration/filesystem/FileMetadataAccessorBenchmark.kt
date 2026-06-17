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
package org.gradle.internal.nativeintegration.filesystem

import com.google.common.collect.ImmutableMap
import net.rubygrapefruit.platform.Native
import org.gradle.internal.UncheckedException
import org.gradle.internal.file.FileMetadata
import org.gradle.internal.file.FileMetadataAccessor
import org.gradle.internal.file.impl.DefaultFileMetadata.Companion.directory
import org.gradle.internal.file.impl.DefaultFileMetadata.Companion.file
import org.gradle.internal.file.impl.DefaultFileMetadata.Companion.missing
import org.gradle.internal.file.nio.NioFileMetadataAccessor
import org.gradle.internal.nativeintegration.filesystem.services.FallbackFileMetadataAccessor
import org.gradle.internal.nativeintegration.filesystem.services.NativePlatformBackedFileMetadataAccessor
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.TearDown
import org.openjdk.jmh.annotations.Threads
import org.openjdk.jmh.annotations.Warmup
import org.openjdk.jmh.infra.Blackhole
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.UUID

@Threads(2)
@Warmup(iterations = 5)
@Measurement(iterations = 5)
@State(Scope.Benchmark)
class FileMetadataAccessorBenchmark {
    @Param(
        "FallbackFileMetadataAccessor", "NativePlatformBackedFileMetadataAccessor", "Jdk7FileMetadataAccessor", "NioFileMetadataAccessor"
    )
    var accessorClassName: String? = null

    var accessor: FileMetadataAccessor? = null
    var missing: File? = null
    var missingPath: Path? = null
    var directory: File? = null
    var directoryPath: Path? = null
    var realFile: File? = null
    var realFilePath: Path? = null

    @Setup
    @Throws(IOException::class)
    fun prepare() {
        accessor = getAccessor(accessorClassName)
        missing = File(UUID.randomUUID().toString())
        missingPath = missing!!.toPath()
        directory = File.createTempFile("jmh", "dir")
        directoryPath = directory!!.toPath()
        directory!!.mkdirs()
        realFile = File.createTempFile("jmh", "tmp")
        realFilePath = realFile!!.toPath()

        val fos = FileOutputStream(realFile)
        fos.write(ByteArray(1024))
        fos.close()
    }

    @TearDown
    fun tearDown() {
        directory!!.delete()
        realFile!!.delete()
    }

    private fun getAccessor(name: String?): FileMetadataAccessor? {
        return ACCESSORS.get(name)
    }

    @Benchmark
    fun stat_missing_file(bh: Blackhole) {
        bh.consume(getAccessor(accessorClassName)!!.stat(missing!!))
    }

    @Benchmark
    fun stat_directory(bh: Blackhole) {
        bh.consume(getAccessor(accessorClassName)!!.stat(directory!!))
    }

    @Benchmark
    fun stat_existing(bh: Blackhole) {
        bh.consume(getAccessor(accessorClassName)!!.stat(realFile!!))
    }

    private class Jdk7FileMetadataAccessor : FileMetadataAccessor {
        override fun stat(f: File): FileMetadata? {
            if (!f.exists()) {
                // This is really not cool, but we cannot rely on `readAttributes` because it will
                // THROW AN EXCEPTION if the file is missing, which is really incredibly slow just
                // to determine if a file exists or not.
                return missing(FileMetadata.AccessType.DIRECT)
            }
            try {
                val bfa = Files.readAttributes<BasicFileAttributes>(f.toPath(), BasicFileAttributes::class.java)
                if (bfa.isDirectory()) {
                    return directory(FileMetadata.AccessType.DIRECT)
                }
                return file(bfa.lastModifiedTime().toMillis(), bfa.size(), FileMetadata.AccessType.DIRECT)
            } catch (e: IOException) {
                throw UncheckedException.throwAsUncheckedException(e)
            }
        }
    }

    companion object {
        private val NATIVE_INTEGRATION: Native = Native.init(File("build/tmp/jmh-benchmark"))
        private val ACCESSORS: MutableMap<String?, FileMetadataAccessor?> = ImmutableMap.builder<String?, FileMetadataAccessor?>()
            .put(FallbackFileMetadataAccessor::class.java.getSimpleName(), FallbackFileMetadataAccessor())
            .put(
                NativePlatformBackedFileMetadataAccessor::class.java.getSimpleName(),
                NativePlatformBackedFileMetadataAccessor(NATIVE_INTEGRATION.get<net.rubygrapefruit.platform.file.Files?>(net.rubygrapefruit.platform.file.Files::class.java))
            )
            .put(Jdk7FileMetadataAccessor::class.java.getSimpleName(), Jdk7FileMetadataAccessor())
            .put(NioFileMetadataAccessor::class.java.getSimpleName(), NioFileMetadataAccessor())
            .build()
    }
}
