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
package org.gradle.internal.resource.local

import com.google.common.io.CountingInputStream
import com.google.common.io.CountingOutputStream
import com.google.common.io.Files
import org.apache.commons.io.IOUtils
import org.gradle.api.Action
import org.gradle.api.resources.ResourceException
import org.gradle.internal.file.FileType
import org.gradle.internal.nativeintegration.filesystem.FileSystem
import org.gradle.internal.resource.AbstractExternalResource
import org.gradle.internal.resource.ExternalResource
import org.gradle.internal.resource.ExternalResourceReadResult
import org.gradle.internal.resource.ExternalResourceWriteResult
import org.gradle.internal.resource.LocalBinaryResource
import org.gradle.internal.resource.ReadableContent
import org.gradle.internal.resource.ResourceExceptions
import org.gradle.internal.resource.metadata.DefaultExternalResourceMetaData
import org.gradle.internal.resource.metadata.ExternalResourceMetaData
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.util.Arrays

/**
 * A file backed [ExternalResource] implementation.
 */
class LocalFileStandInExternalResource @JvmOverloads constructor(
    private val localFile: File,
    private val fileSystem: FileSystem,
    private val listener: FileResourceListener = FileResourceListener.Companion.NO_OP
) : AbstractExternalResource(), LocallyAvailableExternalResource, LocalBinaryResource {
    override fun getURI(): URI {
        return localFile.toURI()
    }

    override fun getFile(): File {
        return localFile
    }

    override fun getBaseName(): String {
        return localFile.getName()
    }

    override fun getContainingFile(): File {
        return localFile
    }

    override fun getContentLength(): Long {
        return localFile.length()
    }

    override fun getDisplayName(): String {
        return localFile.getPath()
    }

    override fun exists(): Boolean {
        listener.fileObserved(localFile)
        return localFile.exists()
    }

    override fun getMetaData(): ExternalResourceMetaData? {
        val fileMetadata = fileSystem.stat(localFile)
        if (fileMetadata!!.type === FileType.Missing) {
            return null
        }
        return DefaultExternalResourceMetaData(localFile.toURI(), fileMetadata.lastModified, fileMetadata.length)
    }

    override fun writeTo(output: OutputStream): ExternalResourceReadResult<Void?> {
        if (!localFile.exists()) {
            throw ResourceExceptions.getMissing(getURI())
        }
        try {
            val input = CountingInputStream(FileInputStream(localFile))
            try {
                IOUtils.copyLarge(input, output)
            } finally {
                input.close()
            }
            return ExternalResourceReadResult.Companion.of(input.getCount())
        } catch (e: IOException) {
            throw ResourceExceptions.getFailed(getURI(), e)
        }
    }

    override fun writeToIfPresent(destination: File): ExternalResourceReadResult<Void?>? {
        if (!localFile.exists()) {
            return null
        }
        try {
            val input = CountingInputStream(FileInputStream(localFile))
            try {
                FileOutputStream(destination).use { output ->
                    IOUtils.copyLarge(input, output)
                }
            } finally {
                input.close()
            }
            return ExternalResourceReadResult.Companion.of(input.getCount())
        } catch (e: IOException) {
            throw ResourceExceptions.getFailed(getURI(), e)
        }
    }

    override fun withContent(readAction: Action<in InputStream?>): ExternalResourceReadResult<Void?> {
        if (!localFile.exists()) {
            throw ResourceExceptions.getMissing(getURI())
        }
        try {
            val input = CountingInputStream(BufferedInputStream(FileInputStream(localFile)))
            try {
                readAction.execute(input)
            } finally {
                input.close()
            }
            return ExternalResourceReadResult.Companion.of(input.getCount())
        } catch (e: IOException) {
            throw ResourceExceptions.getFailed(getURI(), e)
        }
    }

    @Throws(ResourceException::class)
    override fun <T> withContentIfPresent(readAction: ExternalResource.ContentAndMetadataAction<out T?>): ExternalResourceReadResult<T?>? {
        if (!localFile.exists()) {
            return null
        }
        try {
            CountingInputStream(BufferedInputStream(FileInputStream(localFile))).use { input ->
                val resourceReadResult: T? = readAction.execute(input, getMetaData())
                return ExternalResourceReadResult.Companion.of<T?>(input.getCount(), resourceReadResult)
            }
        } catch (e: IOException) {
            throw ResourceExceptions.getFailed(getURI(), e)
        }
    }

    @Throws(ResourceException::class)
    override fun <T> withContentIfPresent(readAction: ExternalResource.ContentAction<out T?>): ExternalResourceReadResult<T?>? {
        if (!localFile.exists()) {
            return null
        }
        try {
            CountingInputStream(BufferedInputStream(FileInputStream(localFile))).use { input ->
                val resourceReadResult: T? = readAction.execute(input)
                return ExternalResourceReadResult.Companion.of<T?>(input.getCount(), resourceReadResult)
            }
        } catch (e: IOException) {
            throw ResourceExceptions.getFailed(getURI(), e)
        }
    }

    override fun put(location: ReadableContent): ExternalResourceWriteResult {
        try {
            if (!localFile.canWrite()) {
                localFile.delete()
            }
            Files.createParentDirs(localFile)

            location.open().use { input ->
                val output = CountingOutputStream(FileOutputStream(localFile))
                try {
                    IOUtils.copyLarge(input, output)
                } finally {
                    output.close()
                }
                return ExternalResourceWriteResult(output.getCount())
            }
        } catch (e: IOException) {
            throw ResourceExceptions.putFailed(getURI(), e)
        }
    }

    @Throws(ResourceException::class)
    override fun open(): InputStream {
        if (localFile.isDirectory()) {
            throw ResourceExceptions.readFolder(localFile)
        }
        try {
            return FileInputStream(localFile)
        } catch (e: FileNotFoundException) {
            throw ResourceExceptions.readMissing(localFile, e)
        }
    }

    @Throws(ResourceException::class)
    override fun list(): MutableList<String?>? {
        listener.directoryChildrenObserved(localFile)
        if (localFile.isDirectory()) {
            val names = localFile.list()
            return Arrays.asList<String?>(*names)
        }
        return null
    }
}
