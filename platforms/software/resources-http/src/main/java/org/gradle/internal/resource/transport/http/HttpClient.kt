/*
 * Copyright 2025 the original author or authors.
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
package org.gradle.internal.resource.transport.http

import com.google.common.collect.ImmutableMap
import org.gradle.internal.resource.ReadableContent
import org.jspecify.annotations.NullMarked
import java.io.Closeable
import java.io.IOException
import java.io.OutputStream
import java.net.URI

/**
 * A client which can perform HTTP requests.
 *
 *
 * To create instances of this interface, use an [HttpClientFactory].
 *
 *
 * Implementations of this interface must be thread-safe. This interface should
 * remain independent of any specific HTTP client library.
 */
@NullMarked
interface HttpClient : Closeable {
    /**
     * Performs a HEAD request to the given URI with the given headers.
     */
    fun performHead(uri: URI, headers: ImmutableMap<String, String>): Response?

    /**
     * Performs a GET request to the given URI with the given headers.
     */
    fun performGet(uri: URI, headers: ImmutableMap<String, String>): Response?

    /**
     * Performs a GET request to the given URI with the given headers, avoiding any
     * additional processing of the response like mapping status codes to exceptions
     * or closing the response body when unsuccessful responses are received.
     */
    @Throws(IOException::class)
    fun performRawGet(uri: URI, headers: ImmutableMap<String, String>): Response?

    /**
     * Performs a PUT request to the given URI with the given headers, avoiding any
     * additional processing of the response like mapping status codes to exceptions
     * or closing the response body when unsuccessful responses are received.
     */
    @Throws(IOException::class)
    fun performRawPut(uri: URI, resource: ReadableContent): Response?

    /**
     * Performs a PUT request to the given URI with the given headers, avoiding any
     * additional processing of the response like mapping status codes to exceptions
     * or closing the response body when unsuccessful responses are received.
     */
    @Throws(IOException::class)
    fun performRawPut(uri: URI, headers: ImmutableMap<String, String>, resource: WritableContent): Response?

    /**
     * A response to an HTTP request.
     */
    interface Response : Closeable {
        /**
         * Returns the value of the given response header, or null if not present.
         */
        fun getHeader(name: String): String?

        @JvmField
        @get:Throws(IOException::class)
        val content: InputStream?

        /**
         * Returns the HTTP status code of the response.
         */
        @JvmField
        val statusCode: Int

        /**
         * Returns the HTTP status reason phrase of the response.
         */
        @JvmField
        val statusReason: String?

        /**
         * Returns the HTTP method used for the request.
         */
        val method: String?

        /**
         * Returns the effective URI of the response, after any redirects.
         */
        val effectiveUri: URI?

        val isSuccessful: Boolean
            /**
             * Returns true if the response status code indicates a successful response.
             */
            get() {
                val statusCode = this.statusCode
                return statusCode >= 200 && statusCode < 400
            }

        val isMissing: Boolean
            /**
             * Returns true if the response status code indicates a missing resource (404).
             */
            get() = this.statusCode == 404

        /**
         * {@inheritDoc}
         */
        override fun close()
    }

    /**
     * Content that may be used as the body of a PUT request.
     */
    interface WritableContent {
        /**
         * Writes the content to the given output stream.
         */
        @Throws(IOException::class)
        fun writeTo(outputStream: OutputStream)

        /**
         * Returns the size of the content in bytes.
         */
        val size: Long
    }
}
