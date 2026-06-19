/*
 * Copyright 2018 the original author or authors.
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

import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.utils.HttpClientUtils
import org.jspecify.annotations.NullMarked
import java.io.IOException
import java.io.InputStream
import java.net.URI

/**
 * An HTTP response from an [ApacheCommonsHttpClient].
 */
@NullMarked
class ApacheHttpResponse internal constructor(override val method: String, override val effectiveUri: URI, private val httpResponse: CloseableHttpResponse) : HttpClient.Response {
    private var closed = false

    override fun getHeader(name: String): String? {
        val header = httpResponse.getFirstHeader(name)
        return if (header == null) null else header.getValue()
    }

    @get:Throws(IOException::class)
    override val content: InputStream
        get() {
            val entity = httpResponse.getEntity()
            if (entity == null) {
                throw IOException(String.format("Response %d: %s has no content!", statusCode, statusReason))
            }
            return entity.getContent()
        }

    override val statusCode: Int
        get() = httpResponse.getStatusLine().getStatusCode()

    override val statusReason: String
        get() = httpResponse.getStatusLine().getReasonPhrase()

    override fun close() {
        if (!closed) {
            closed = true
            HttpClientUtils.closeQuietly(httpResponse)
        }
    }

}
