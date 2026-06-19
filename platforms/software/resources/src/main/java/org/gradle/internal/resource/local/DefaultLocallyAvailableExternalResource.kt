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
package org.gradle.internal.resource.local

import org.gradle.api.Action
import org.gradle.api.resources.ResourceException
import org.gradle.internal.nativeintegration.filesystem.FileSystem
import org.gradle.internal.resource.ExternalResource
import org.gradle.internal.resource.ExternalResourceReadResult
import org.gradle.internal.resource.ExternalResourceWriteResult
import org.gradle.internal.resource.ReadableContent
import org.gradle.internal.resource.metadata.ExternalResourceMetaData
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.URI

/**
 * A [LocallyAvailableExternalResource] implementation that represents a local file backed copy of some remote resource.
 */
class DefaultLocallyAvailableExternalResource(private val source: URI, locallyAvailableResource: File, private val metaData: ExternalResourceMetaData?, fileSystem: FileSystem) :
    LocallyAvailableExternalResource {
    private val localFile: LocallyAvailableExternalResource

    init {
        localFile = LocalFileStandInExternalResource(locallyAvailableResource, fileSystem)
    }

    override fun getDisplayName(): String {
        return source.toString()
    }

    override fun getURI(): URI {
        return source
    }

    override fun getMetaData(): ExternalResourceMetaData? {
        return metaData
    }

    override fun getFile(): File {
        return localFile.getFile()
    }

    override fun exists(): Boolean {
        return localFile.exists()
    }

    @Throws(ResourceException::class)
    override fun list(): MutableList<String?>? {
        throw UnsupportedOperationException()
    }

    @Throws(ResourceException::class)
    override fun put(source: ReadableContent): ExternalResourceWriteResult {
        throw UnsupportedOperationException()
    }

    @Throws(ResourceException::class)
    override fun writeTo(destination: File): ExternalResourceReadResult<Void?> {
        return localFile.writeTo(destination)
    }

    @Throws(ResourceException::class)
    override fun writeToIfPresent(destination: File): ExternalResourceReadResult<Void?>? {
        return localFile.writeToIfPresent(destination)
    }

    @Throws(ResourceException::class)
    override fun writeTo(destination: OutputStream): ExternalResourceReadResult<Void?> {
        return localFile.writeTo(destination)
    }

    @Throws(ResourceException::class)
    override fun withContent(readAction: Action<in InputStream>): ExternalResourceReadResult<Void?> {
        return localFile.withContent(readAction)
    }

    @Throws(ResourceException::class)
    override fun <T> withContent(readAction: ExternalResource.ContentAction<out T?>): ExternalResourceReadResult<T?> {
        return localFile.withContent<T?>(readAction)
    }

    @Throws(ResourceException::class)
    override fun <T> withContentIfPresent(readAction: ExternalResource.ContentAction<out T?>): ExternalResourceReadResult<T?>? {
        return localFile.withContentIfPresent<T?>(readAction)
    }

    @Throws(ResourceException::class)
    override fun <T> withContent(readAction: ExternalResource.ContentAndMetadataAction<out T?>): ExternalResourceReadResult<T?> {
        return localFile.withContent<T?>(readAction)
    }

    @Throws(ResourceException::class)
    override fun <T> withContentIfPresent(readAction: ExternalResource.ContentAndMetadataAction<out T?>): ExternalResourceReadResult<T?>? {
        return localFile.withContentIfPresent<T?>(readAction)
    }
}
