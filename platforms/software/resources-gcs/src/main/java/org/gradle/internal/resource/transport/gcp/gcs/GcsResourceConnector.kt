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
package org.gradle.internal.resource.transport.gcp.gcs

import org.gradle.api.resources.ResourceException
import org.gradle.internal.resource.ExternalResourceName
import org.gradle.internal.resource.ReadableContent
import org.gradle.internal.resource.metadata.ExternalResourceMetaData
import org.gradle.internal.resource.transfer.AbstractExternalResourceAccessor
import org.gradle.internal.resource.transfer.ExternalResourceConnector
import org.gradle.internal.resource.transfer.ExternalResourceReadResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException

class GcsResourceConnector(private val gcsClient: GcsClient) : AbstractExternalResourceAccessor(), ExternalResourceConnector {
    @Throws(ResourceException::class)
    override fun list(parent: ExternalResourceName): MutableList<String?>? {
        LOGGER.debug("Listing parent resources: {}", parent)
        return gcsClient.list(parent.uri)
    }

    @Throws(ResourceException::class)
    public override fun openResource(location: ExternalResourceName, revalidate: Boolean): ExternalResourceReadResponse? {
        LOGGER.debug("Attempting to get resource: {}", location)
        val gcsObject = gcsClient.getResource(location.uri)
        if (gcsObject == null) {
            return null
        }
        return GcsResource(gcsClient, gcsObject, location.uri)
    }

    @Throws(ResourceException::class)
    override fun getMetaData(location: ExternalResourceName, revalidate: Boolean): ExternalResourceMetaData? {
        LOGGER.debug("Attempting to get resource metadata: {}", location)
        val gcsObject = gcsClient.getResource(location.uri)
        if (gcsObject == null) {
            return null
        }
        return ResourceMapper.toExternalResourceMetaData(location.uri, gcsObject)
    }

    @Throws(IOException::class)
    override fun upload(resource: ReadableContent, destination: ExternalResourceName) {
        LOGGER.debug("Attempting to upload stream to: {}", destination)
        val inputStream = resource.open()
        try {
            gcsClient.put(inputStream, resource.getContentLength(), destination.uri)
        } finally {
            inputStream.close()
        }
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(GcsResourceConnector::class.java)
    }
}
