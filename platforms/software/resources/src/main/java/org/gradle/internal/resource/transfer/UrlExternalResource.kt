/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.api.resources.ResourceException
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.resource.ExternalResource
import org.gradle.internal.resource.ExternalResourceName
import org.gradle.internal.resource.ReadableContent
import org.gradle.internal.resource.ResourceExceptions
import org.gradle.internal.resource.metadata.DefaultExternalResourceMetaData
import org.gradle.internal.resource.metadata.ExternalResourceMetaData
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.URISyntaxException
import java.net.URL

class UrlExternalResource : AbstractExternalResourceAccessor(), ExternalResourceConnector {
    @Throws(ResourceException::class)
    override fun getMetaData(location: ExternalResourceName, revalidate: Boolean): ExternalResourceMetaData? {
        try {
            val url = location.uri.toURL()
            val connection = url.openConnection()
            try {
                return DefaultExternalResourceMetaData(location.uri, connection.getLastModified(), connection.getContentLength().toLong(), connection.getContentType(), null, null)
            } finally {
                connection.getInputStream().close()
            }
        } catch (e: FileNotFoundException) {
            return null
        } catch (e: Exception) {
            throw ResourceExceptions.getFailed(location.uri, e)
        }
    }

    @Throws(ResourceException::class)
    public override fun openResource(location: ExternalResourceName, revalidate: Boolean): ExternalResourceReadResponse? {
        try {
            val url = location.uri.toURL()
            val connection = url.openConnection()
            val inputStream = connection.getInputStream()
            return object : ExternalResourceReadResponse {
                override fun openStream(): InputStream {
                    return inputStream
                }

                override fun getMetaData(): ExternalResourceMetaData {
                    return DefaultExternalResourceMetaData(location.uri, connection.getLastModified(), connection.getContentLength().toLong(), connection.getContentType(), null, null)
                }

                @Throws(IOException::class)
                override fun close() {
                    inputStream.close()
                }
            }
        } catch (e: FileNotFoundException) {
            return null
        } catch (e: Exception) {
            throw ResourceExceptions.getFailed(location.uri, e)
        }
    }

    @Throws(ResourceException::class)
    override fun list(parent: ExternalResourceName): MutableList<String?>? {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun upload(resource: ReadableContent, destination: ExternalResourceName) {
        throw UnsupportedOperationException()
    }

    companion object {
        @Throws(IOException::class)
        fun open(url: URL): ExternalResource {
            val uri: URI?
            try {
                uri = url.toURI()
            } catch (e: URISyntaxException) {
                throw throwAsUncheckedException(e)
            }
            val connector = UrlExternalResource()
            return AccessorBackedExternalResource(ExternalResourceName(uri), connector, connector, connector, false)
        }
    }
}
