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
package org.gradle.internal.resource.transport.http

import com.google.common.collect.ImmutableMap
import org.apache.http.HttpHeaders
import org.gradle.internal.IoActions
import org.gradle.internal.resource.ExternalResourceName
import org.gradle.internal.resource.metadata.ExternalResourceMetaData
import org.gradle.internal.resource.transfer.AbstractExternalResourceAccessor
import org.gradle.internal.resource.transfer.ExternalResourceAccessor
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI

open class HttpResourceAccessor(private val client: HttpClient) : AbstractExternalResourceAccessor(), ExternalResourceAccessor {
    public override fun openResource(location: ExternalResourceName, revalidate: Boolean): HttpResponseResource? {
        LOGGER.debug("Constructing external resource: {}", location)

        val uri = location.uri
        val response = client.performGet(uri, getHeaders(revalidate))
        return wrapResponse(uri, response)
    }

    override fun getMetaData(location: ExternalResourceName, revalidate: Boolean): ExternalResourceMetaData? {
        LOGGER.debug("Constructing external resource metadata: {}", location)

        val uri = location.uri
        val response = client.performHead(uri, getHeaders(revalidate))

        if (response.isMissing) {
            return null
        }

        val resource = HttpResponseResource("HEAD", uri, response)
        try {
            return resource.getMetaData()
        } finally {
            IoActions.closeQuietly(resource)
        }
    }

    private fun wrapResponse(uri: URI, response: HttpClient.Response): HttpResponseResource {
        return HttpResponseResource("GET", uri, response)
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(HttpResourceAccessor::class.java)
        private val REVALIDATE_HEADERS = ImmutableMap.of<String, String>(HttpHeaders.CACHE_CONTROL, "max-age=0")

        private fun getHeaders(revalidate: Boolean): ImmutableMap<String, String> {
            return if (revalidate) REVALIDATE_HEADERS else ImmutableMap.of<String, String>()
        }
    }
}
