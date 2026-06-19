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
package org.gradle.internal.resource.transport.http

import org.apache.http.HttpHeaders
import org.apache.http.client.utils.DateUtils
import org.gradle.internal.hash.HashCode
import org.gradle.internal.resource.metadata.DefaultExternalResourceMetaData
import org.gradle.internal.resource.metadata.ExternalResourceMetaData
import org.gradle.internal.resource.transfer.ExternalResourceReadResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.util.Date

class HttpResponseResource(private val method: String, val uRI: URI, private val response: HttpClient.Response) : ExternalResourceReadResponse {
    private val metaData: ExternalResourceMetaData
    private var wasOpened = false

    init {
        val etag: String? = getEtag(response)
        this.metaData = DefaultExternalResourceMetaData(
            uRI,
            this.lastModified,
            this.contentLength,
            this.contentType, etag, getSha1(response, etag),
            this.filename, response.isMissing
        )
    }

    override fun toString(): String {
        return "Http " + method + " Resource: " + this.uRI
    }

    override fun getMetaData(): ExternalResourceMetaData {
        return metaData
    }

    val statusCode: Int
        get() = response.statusCode

    val lastModified: Date?
        get() {
            val responseHeader = response.getHeader(HttpHeaders.LAST_MODIFIED)
            if (responseHeader == null) {
                return Date(0)
            }
            try {
                return DateUtils.parseDate(responseHeader)
            } catch (e: Exception) {
                return Date(0)
            }
        }

    private val filename: String?
        get() {
            val disposition = response.getHeader("Content-Disposition")
            if (disposition != null) {
                // extracts file name from header field
                var beginIndex = disposition.indexOf("filename=\"")
                if (beginIndex > 0) {
                    var endIndex = disposition.indexOf(';', beginIndex + 11) // find the next semicolon
                    endIndex = if (endIndex < 0) disposition.length else endIndex // if no semicolon is found, then there is nothing else in the disposition
                    endIndex -= 1 // ignore the closing quotes
                    return disposition.substring(beginIndex + 10, endIndex)
                }

                beginIndex = disposition.indexOf("filename=")
                if (beginIndex > 0) {
                    var endIndex = disposition.indexOf(';', beginIndex + 10) // find the next semicolon
                    endIndex = if (endIndex < 0) disposition.length else endIndex // if no semicolon is found, then there is nothing else in the disposition
                    return disposition.substring(beginIndex + 9, endIndex)
                }
            } else {
                // extracts file name from URL
                val uri = if (response.effectiveUri == null) this.uRI else response.effectiveUri
                val sourceInStringForm = uri.toString()
                val fileNameIndex = sourceInStringForm.lastIndexOf("/")
                if (fileNameIndex >= 0) {
                    return sourceInStringForm.substring(fileNameIndex + 1)
                }
            }
            return null
        }

    val contentLength: Long
        get() {
            val header = response.getHeader(HttpHeaders.CONTENT_LENGTH)
            if (header == null) {
                return -1
            }

            try {
                return header.toLong()
            } catch (e: NumberFormatException) {
                return -1
            }
        }

    fun getHeaderValue(name: String): String? {
        return response.getHeader(name)
    }

    val contentType: String?
        get() = response.getHeader(HttpHeaders.CONTENT_TYPE)

    val isLocal: Boolean
        get() = false

    @Throws(IOException::class)
    override fun openStream(): InputStream {
        if (wasOpened) {
            throw IOException("Unable to open Stream as it was opened before.")
        }
        LOGGER.debug("Attempting to download resource {}.", this.uRI)
        this.wasOpened = true
        return response.content
    }

    override fun close() {
        response.close()
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(HttpResponseResource::class.java)

        private fun getEtag(response: HttpClient.Response): String? {
            return response.getHeader(HttpHeaders.ETAG)
        }

        private fun getSha1(response: HttpClient.Response, etag: String?): HashCode? {
            val sha1Header = response.getHeader("X-Checksum-Sha1")
            if (sha1Header != null) {
                return HashCode.fromString(sha1Header)
            }

            // Nexus uses sha1 etags, with a constant prefix
            // e.g {SHA1{b8ad5573a5e9eba7d48ed77a48ad098e3ec2590b}}
            if (etag != null && etag.startsWith("{SHA1{")) {
                val hash = etag.substring(6, etag.length - 2)
                return HashCode.fromString(hash)
            }

            return null
        }
    }
}
