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
package org.gradle.internal.resource

import org.gradle.api.Action
import org.gradle.api.resources.ResourceException
import org.gradle.internal.resource.metadata.ExternalResourceMetaData
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URI

/**
 * Represents a binary resource and provides access to the content and meta-data of the resource. The resource may or may not exist, and may change over time.
 */
interface ExternalResource : Resource {
    /**
     * Get the URI of the resource.
     */
    fun getURI(): URI

    /**
     * Copies the contents of this resource to the given file.
     *
     * @throws ResourceException on failure to copy the content.
     * @throws org.gradle.api.resources.MissingResourceException when the resource does not exist
     */
    @Throws(ResourceException::class)
    fun writeTo(destination: File): ExternalResourceReadResult<Void?>

    /**
     * Copies the contents of this resource to the given file, if the resource exists.
     *
     * @return null if this resource does not exist.
     * @throws ResourceException on failure to copy the content.
     */
    @Throws(ResourceException::class)
    fun writeToIfPresent(destination: File): ExternalResourceReadResult<Void?>?

    /**
     * Copies the binary contents of this resource to the given stream. Does not close the provided stream.
     *
     * @throws ResourceException on failure to copy the content.
     * @throws org.gradle.api.resources.MissingResourceException when the resource does not exist
     */
    @Throws(ResourceException::class)
    fun writeTo(destination: OutputStream): ExternalResourceReadResult<Void?>

    /**
     * Executes the given action against the binary contents of this resource.
     *
     * @throws ResourceException on failure to read the content.
     * @throws org.gradle.api.resources.MissingResourceException when the resource does not exist
     */
    @Throws(ResourceException::class)
    fun withContent(readAction: Action<in InputStream>): ExternalResourceReadResult<Void?>

    /**
     * Executes the given action against the binary contents of this resource.
     *
     * @throws ResourceException on failure to read the content.
     * @throws org.gradle.api.resources.MissingResourceException when the resource does not exist
     */
    @Throws(ResourceException::class)
    fun <T> withContent(readAction: ContentAction<out T?>): ExternalResourceReadResult<T?>

    /**
     * Executes the given action against the binary contents of this resource, if the resource exists.
     *
     * @return null if the resource does not exist.
     * @throws ResourceException on failure to read the content.
     */
    @Throws(ResourceException::class)
    fun <T> withContentIfPresent(readAction: ContentAction<out T?>): ExternalResourceReadResult<T?>?

    /**
     * Executes the given action against the binary contents and meta-data of this resource.
     * Generally, this method will be less efficient than one of the other `withContent` methods that do
     * not provide the meta-data, as additional requests may need to be made to obtain the meta-data.
     *
     * @throws ResourceException on failure to read the content.
     * @throws org.gradle.api.resources.MissingResourceException when the resource does not exist
     */
    @Throws(ResourceException::class)
    fun <T> withContent(readAction: ContentAndMetadataAction<out T?>): ExternalResourceReadResult<T?>

    /**
     * Executes the given action against the binary contents and meta-data of this resource.
     * Generally, this method will be less efficient than one of the other `withContent` methods that do
     * not provide the meta-data, as additional requests may need to be made to obtain the meta-data.
     *
     * @return null if the resource does not exist.
     * @throws ResourceException on failure to read the content.
     */
    @Throws(ResourceException::class)
    fun <T> withContentIfPresent(readAction: ContentAndMetadataAction<out T?>): ExternalResourceReadResult<T?>?

    /**
     * Copies the given content to this resource.
     *
     * @param source The local resource to be transferred.
     * @throws ResourceException On failure to write the content.
     */
    @Throws(ResourceException::class)
    fun put(source: ReadableContent): ExternalResourceWriteResult

    /**
     * Return a listing of child resources names.
     *
     * @return A listing of the direct children of the given parent. Returns null when the parent resource does not exist.
     * @throws ResourceException On listing failure.
     */
    @Throws(ResourceException::class)
    fun list(): MutableList<String?>?

    /**
     * Returns the meta-data for this resource, if the resource exists.
     *
     * @return null when the resource does not exist.
     */
    fun getMetaData(): ExternalResourceMetaData?

    fun interface ContentAndMetadataAction<T> {
        @Throws(IOException::class)
        fun execute(inputStream: InputStream, metaData: ExternalResourceMetaData?): T?
    }

    fun interface ContentAction<T> {
        @Throws(IOException::class)
        fun execute(inputStream: InputStream): T?
    }
}
