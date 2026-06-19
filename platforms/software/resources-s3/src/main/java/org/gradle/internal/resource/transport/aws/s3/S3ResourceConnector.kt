/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.internal.resource.transport.aws.s3

import com.amazonaws.services.s3.model.S3Object
import com.google.common.io.ByteStreams
import org.gradle.internal.IoActions
import org.gradle.internal.resource.ExternalResourceName
import org.gradle.internal.resource.ReadableContent
import org.gradle.internal.resource.metadata.DefaultExternalResourceMetaData
import org.gradle.internal.resource.metadata.ExternalResourceMetaData
import org.gradle.internal.resource.transfer.AbstractExternalResourceAccessor
import org.gradle.internal.resource.transfer.ExternalResourceConnector
import org.gradle.internal.resource.transfer.ExternalResourceReadResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException

class S3ResourceConnector(private val s3Client: S3Client) : AbstractExternalResourceAccessor(), ExternalResourceConnector {
    override fun list(parent: ExternalResourceName): MutableList<String?>? {
        LOGGER.debug("Listing parent resources: {}", parent)
        return s3Client.listDirectChildren(parent.uri)
    }

    public override fun openResource(location: ExternalResourceName, revalidate: Boolean): ExternalResourceReadResponse? {
        LOGGER.debug("Attempting to get resource: {}", location)
        val s3Object = s3Client.getResource(location.uri)
        if (s3Object == null) {
            return null
        }
        return S3Resource(s3Object, location.uri)
    }

    override fun getMetaData(location: ExternalResourceName, revalidate: Boolean): ExternalResourceMetaData? {
        LOGGER.debug("Attempting to get resource metadata: {}", location)
        val s3Object = s3Client.getMetaData(location.uri)
        if (s3Object == null) {
            return null
        }
        try {
            val objectMetadata = s3Object.getObjectMetadata()
            return DefaultExternalResourceMetaData(
                location.uri,
                objectMetadata.getLastModified().getTime(),
                objectMetadata.getContentLength(),
                objectMetadata.getContentType(),
                objectMetadata.getETag(),
                null
            ) // Passing null for sha1 - TODO - consider using the etag which is an MD5 hash of the file (when less than 5Gb)
        } finally {
            discardEmptyContentAndClose(s3Object)
        }
    }

    @Throws(IOException::class)
    override fun upload(resource: ReadableContent, destination: ExternalResourceName) {
        LOGGER.debug("Attempting to upload stream to : {}", destination)
        val inputStream = resource.open()
        try {
            s3Client.put(inputStream, resource.getContentLength(), destination.uri)
        } finally {
            inputStream.close()
        }
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(S3ResourceConnector::class.java)
        private fun discardEmptyContentAndClose(s3Object: S3Object) {
            // Consume the content stream to avoid warning from S3 SDK. The response should have only 1 byte there because Range header was specified.
            try {
                val objectContent = s3Object.getObjectContent()
                if (objectContent == null) {
                    return
                }
                val downloadedContentLength = ByteStreams.exhaust(objectContent)
                if (downloadedContentLength > 1L) {
                    // This may happen if the endpoint ignores Range HTTP header for whatever reason.
                    LOGGER.debug("Downloaded {} bytes of the object content for metadata request which is too much.", downloadedContentLength)
                }
            } catch (e: IOException) {
                // Don't complain loudly to the user about the error there because we were discarding the response anyway.
                LOGGER.debug("Exception while consuming empty object content from metadata request", e)
            } finally {
                // This also closes objectContent, no need to close it explicitly.
                IoActions.closeQuietly(s3Object)
            }
        }
    }
}
