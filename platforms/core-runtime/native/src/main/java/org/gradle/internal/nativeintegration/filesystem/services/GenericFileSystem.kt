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
package org.gradle.internal.nativeintegration.filesystem.services

import com.google.common.io.Files
import org.apache.commons.io.FileUtils
import org.gradle.api.internal.file.temp.TemporaryFileProvider
import org.gradle.internal.SystemProperties
import org.gradle.internal.file.FileException
import org.gradle.internal.file.FileMetadata
import org.gradle.internal.file.FileMetadataAccessor
import org.gradle.internal.file.FileModeAccessor
import org.gradle.internal.file.FileModeMutator
import org.gradle.internal.file.StatStatistics
import org.gradle.internal.nativeintegration.filesystem.FileSystem
import org.gradle.internal.nativeintegration.filesystem.Symlink
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.inject.Inject

internal class GenericFileSystem(
    private val chmod: FileModeMutator,
    private val stat: FileModeAccessor,
    private val symlink: Symlink,
    private val metadata: FileMetadataAccessor,
    private val statisticsCollector: StatStatistics.Collector,
    private val temporaryFileProvider: TemporaryFileProvider
) : FileSystem {
    private var caseSensitive: Boolean? = null
    private val canCreateSymbolicLink: Boolean

    init {
        this.canCreateSymbolicLink = symlink.isSymlinkCreationSupported()
    }

    public override fun isCaseSensitive(): Boolean {
        initializeCaseSensitive()
        return caseSensitive!!
    }

    override fun canCreateSymbolicLink(): Boolean {
        return canCreateSymbolicLink
    }

    override fun createSymbolicLink(link: File, target: File) {
        try {
            symlink.symlink(link, target)
        } catch (e: Exception) {
            throw FileException(String.format("Could not create symlink from '%s' to '%s'.", link.getPath(), target.getPath()), e)
        }
    }

    override fun isSymlink(suspect: File?): Boolean {
        return symlink.isSymlink(suspect)
    }

    override fun getUnixMode(f: File): Int {
        statisticsCollector.reportUnixModeQueried()
        try {
            return stat.getUnixMode(f)
        } catch (e: Exception) {
            throw FileException(String.format("Could not get file mode for '%s'.", f), e)
        }
    }

    @Throws(FileException::class)
    override fun stat(f: File): FileMetadata? {
        statisticsCollector.reportFileStated()
        return metadata.stat(f)
    }

    override fun chmod(f: File, mode: Int) {
        try {
            chmod.chmod(f, mode)
        } catch (e: Exception) {
            throw FileException(String.format("Could not set file mode %o on '%s'.", mode, f), e)
        }
    }

    private fun initializeCaseSensitive() {
        if (caseSensitive == null) {
            val content = generateUniqueContent()
            var file: File? = null
            try {
                checkJavaIoTmpDirExists()
                file = createFile(content)
                caseSensitive = probeCaseSensitive(file, content)
            } catch (e: IOException) {
                throw RuntimeException(e)
            } finally {
                FileUtils.deleteQuietly(file)
            }
        }
    }

    private fun generateUniqueContent(): String {
        return UUID.randomUUID().toString()
    }

    @Throws(IOException::class)
    private fun createFile(content: String): File {
        val file = temporaryFileProvider.createTemporaryFile("gradle_fs_probing", null)
        Files.asCharSink(file, StandardCharsets.UTF_8).write(content)
        return file
    }

    private fun probeCaseSensitive(file: File, content: String?): Boolean {
        try {
            val upperCased = File(file.getPath().uppercase())
            return !hasContent(upperCased, content)
        } catch (e: IOException) {
            // not fully accurate but a sensible fallback
            // see http://stackoverflow.com/questions/1288102/how-do-i-detect-whether-the-file-system-is-case-sensitive
            val result = File("foo") != File("FOO")
            LOGGER.info("Failed to determine if file system is case sensitive. Best guess is '{}'.", result)
            return result
        }
    }

    @Throws(IOException::class)
    private fun hasContent(file: File, content: String?): Boolean {
        return file.exists() && Files.asCharSource(file, StandardCharsets.UTF_8).readFirstLine() == content
    }

    @Throws(IOException::class)
    private fun checkJavaIoTmpDirExists() {
        @Suppress("deprecation") val dir = File(SystemProperties.getInstance().getJavaIoTmpDir())
        if (!dir.exists()) {
            throw IOException("java.io.tmpdir is set to a directory that doesn't exist: " + dir)
        }
    }

    internal class Factory @Inject constructor(
        private val fileMetadataAccessor: FileMetadataAccessor,
        private val statisticsCollector: StatStatistics.Collector,
        private val temporaryFileProvider: TemporaryFileProvider
    ) {
        fun create(chmod: FileModeMutator, stat: FileModeAccessor, symlink: Symlink): GenericFileSystem {
            return GenericFileSystem(chmod, stat, symlink, fileMetadataAccessor, statisticsCollector, temporaryFileProvider)
        }
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(GenericFileSystem::class.java)
    }
}
