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
import org.gradle.internal.resource.metadata.DefaultExternalResourceMetaData
import org.gradle.internal.resource.metadata.ExternalResourceMetaData
import org.gradle.internal.resource.transfer.ExternalResourceReadResponse
import java.io.IOException
import java.io.InputStream
import java.net.URI

class S3Resource(private val s3Object: S3Object, val uRI: URI?) : ExternalResourceReadResponse {
    @Throws(IOException::class)
    override fun openStream(): InputStream? {
        return s3Object.getObjectContent()
    }

    val contentLength: Long
        get() = s3Object.getObjectMetadata().getContentLength()

    val isLocal: Boolean
        get() = false

    override fun getMetaData(): ExternalResourceMetaData {
        val objectMetadata = s3Object.getObjectMetadata()
        val lastModified = objectMetadata.getLastModified()
        return DefaultExternalResourceMetaData(
            this.uRI,
            lastModified.getTime(),
            this.contentLength,
            s3Object.getObjectMetadata().getContentType(),
            s3Object.getObjectMetadata().getETag(),
            null
        ) // Passing null for sha1 - TODO - consider using the etag which is an MD5 hash of the file (when less than 5Gb)
    }

    @Throws(IOException::class)
    override fun close() {
        s3Object.close()
    }
}
