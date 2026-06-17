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
package org.gradle.internal.resource.metadata

import org.gradle.internal.hash.HashCode
import java.net.URI
import java.util.Date

class DefaultExternalResourceMetaData(
    private val location: URI?,
    private val lastModified: Date?,
    private val contentLength: Long,
    private val contentType: String?,
    private val etag: String?,
    private val sha1: HashCode?,
    private val fileName: String?,
    private val wasMissing: Boolean
) : ExternalResourceMetaData {
    constructor(location: URI?, lastModified: Long, contentLength: Long) : this(location, if (lastModified > 0) Date(lastModified) else null, contentLength, null, null, null, null, false)

    constructor(location: URI?, lastModified: Long, contentLength: Long, contentType: String?, etag: String?, sha1: HashCode?) : this(
        location,
        if (lastModified > 0) Date(lastModified) else null,
        contentLength,
        contentType,
        etag,
        sha1,
        null,
        false
    )

    override fun getLocation(): URI? {
        return location
    }

    override fun getLastModified(): Date? {
        return lastModified
    }

    override fun getContentLength(): Long {
        return contentLength
    }

    override fun getContentType(): String? {
        return contentType
    }

    override fun getEtag(): String? {
        return etag
    }

    override fun getSha1(): HashCode? {
        return sha1
    }

    override fun wasMissing(): Boolean {
        return wasMissing
    }

    override fun getFilename(): String? {
        return fileName
    }
}
