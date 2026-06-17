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

import org.gradle.api.resources.ResourceException
import org.gradle.internal.resource.ExternalResource
import org.gradle.internal.resource.ExternalResourceName
import org.gradle.internal.resource.metadata.ExternalResourceMetaData
import org.gradle.internal.resource.transfer.ExternalResourceLister
import java.io.InputStream

class HttpResourceLister(private val accessor: HttpResourceAccessor) : ExternalResourceLister {
    override fun list(directory: ExternalResourceName): MutableList<String?>? {
        return accessor.withContent<MutableList<String?>?>(directory, true, ExternalResource.ContentAndMetadataAction { inputStream: InputStream?, metaData: ExternalResourceMetaData? ->
            if (metaData!!.wasMissing()) {
                return@withContent null
            }
            val contentType = metaData.getContentType()
            val directoryListingParser = ApacheDirectoryListingParser()
            try {
                return@withContent directoryListingParser.parse(directory.getUri(), inputStream, contentType)
            } catch (e: Exception) {
                throw ResourceException(directory.getUri(), String.format("Unable to parse HTTP directory listing for '%s'.", directory.getUri()), e)
            }
        })
    }
}
