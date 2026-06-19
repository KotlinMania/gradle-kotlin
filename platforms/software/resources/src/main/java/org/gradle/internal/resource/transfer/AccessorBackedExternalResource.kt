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
package org.gradle.internal.resource.transfer

import com.google.common.io.CountingInputStream
import org.apache.commons.io.IOUtils
import org.gradle.api.Action
import org.gradle.api.resources.ResourceException
import org.gradle.internal.resource.AbstractExternalResource
import org.gradle.internal.resource.ExternalResource
import org.gradle.internal.resource.ExternalResourceName
import org.gradle.internal.resource.ExternalResourceReadResult
import org.gradle.internal.resource.ExternalResourceWriteResult
import org.gradle.internal.resource.ReadableContent
import org.gradle.internal.resource.ResourceExceptions
import org.gradle.internal.resource.metadata.ExternalResourceMetaData
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URI

class AccessorBackedExternalResource(
    private val name: ExternalResourceName,
    private val accessor: ExternalResourceAccessor,
    private val uploader: ExternalResourceUploader,
    private val lister: ExternalResourceLister, // Should really be a parameter to the 'withContent' methods or baked into the accessor
    private val revalidate: Boolean
) : AbstractExternalResource() {
    constructor(name: ExternalResourceName, connector: ExternalResourceConnector, revalidate: Boolean) : this(name, connector, connector, connector, revalidate)

    override fun getURI(): URI {
        return name.uri
    }

    override fun getDisplayName(): String {
        return name.getDisplayName()
    }

    @Throws(ResourceException::class)
    override fun writeToIfPresent(destination: File): ExternalResourceReadResult<Void?>? {
        return accessor.withContent<ExternalResourceReadResult<Void?>?>(name, revalidate, ExternalResource.ContentAction { inputStream: InputStream ->
            CountingInputStream(inputStream).use { input ->
                FileOutputStream(destination).use { output ->
                    IOUtils.copyLarge(input, output)
                }
                ExternalResourceReadResult.Companion.of(input.getCount())
            }
        })
    }

    @Throws(ResourceException::class)
    override fun writeTo(destination: OutputStream): ExternalResourceReadResult<Void?> {
        throw UnsupportedOperationException()
    }

    @Throws(ResourceException::class)
    override fun <T> withContentIfPresent(readAction: ExternalResource.ContentAction<out T?>): ExternalResourceReadResult<T?>? {
        return accessor.withContent<ExternalResourceReadResult<T?>?>(name, revalidate, ExternalResource.ContentAction { inputStream: InputStream ->
            CountingInputStream(BufferedInputStream(inputStream)).use { input ->
                val value: T? = readAction.execute(input)
                ExternalResourceReadResult.Companion.of<T?>(input.getCount(), value)
            }
        })
    }

    @Throws(ResourceException::class)
    override fun <T> withContentIfPresent(readAction: ExternalResource.ContentAndMetadataAction<out T?>): ExternalResourceReadResult<T?>? {
        return accessor.withContent<ExternalResourceReadResult<T?>?>(name, revalidate, ExternalResource.ContentAndMetadataAction { inputStream: InputStream, metadata: ExternalResourceMetaData? ->
            CountingInputStream(
                BufferedInputStream(inputStream)
            ).use { stream ->
                val value: T? = readAction.execute(stream, metadata)
                ExternalResourceReadResult.Companion.of<T?>(stream.getCount(), value)
            }
        })
    }

    @Throws(ResourceException::class)
    override fun withContent(readAction: Action<in InputStream>): ExternalResourceReadResult<Void?> {
        val result = accessor.withContent<ExternalResourceReadResult<Void?>?>(name, revalidate, ExternalResource.ContentAction { inputStream: InputStream ->
            val input = CountingInputStream(inputStream)
            readAction.execute(input)
            ExternalResourceReadResult.Companion.of(input.getCount())
        })
        if (result == null) {
            throw ResourceExceptions.getMissing(name.uri)
        }
        return result
    }

    @Throws(ResourceException::class)
    override fun <T> withContent(readAction: ExternalResource.ContentAndMetadataAction<out T?>): ExternalResourceReadResult<T?> {
        val result = withContentIfPresent<T?>(readAction)
        if (result == null) {
            throw ResourceExceptions.getMissing(getURI())
        }
        return result
    }

    @Throws(ResourceException::class)
    override fun put(source: ReadableContent): ExternalResourceWriteResult {
        try {
            val countingResource = CountingReadableContent(source)
            uploader.upload(countingResource, name)
            return ExternalResourceWriteResult(countingResource.getCount())
        } catch (e: IOException) {
            throw ResourceExceptions.putFailed(getURI(), e)
        }
    }

    @Throws(ResourceException::class)
    override fun list(): MutableList<String?>? {
        try {
            return lister.list(name)
        } catch (e: Exception) {
            throw ResourceExceptions.getFailed(getURI(), e)
        }
    }

    override fun getMetaData(): ExternalResourceMetaData? {
        return accessor.getMetaData(name, revalidate)
    }

    private class CountingReadableContent(private val source: ReadableContent) : ReadableContent {
        private var instr: CountingInputStream? = null
        private var count: Long = 0

        @Throws(ResourceException::class)
        override fun open(): InputStream {
            if (instr != null) {
                count += instr!!.getCount()
            }
            instr = CountingInputStream(source.open())
            return instr!!
        }

        override fun getContentLength(): Long {
            return source.getContentLength()
        }

        fun getCount(): Long {
            return if (instr != null) count + instr!!.getCount() else count
        }
    }
}
