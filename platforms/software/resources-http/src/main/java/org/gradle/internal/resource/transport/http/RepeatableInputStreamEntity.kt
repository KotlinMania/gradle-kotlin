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

import org.apache.commons.io.IOUtils
import org.apache.http.entity.AbstractHttpEntity
import org.apache.http.entity.ContentType
import org.gradle.internal.IoActions
import org.gradle.internal.resource.ReadableContent
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class RepeatableInputStreamEntity(private val source: ReadableContent, contentType: ContentType?) : AbstractHttpEntity() {
    init {
        if (contentType != null) {
            setContentType(contentType.toString())
        }
    }

    override fun isRepeatable(): Boolean {
        return true
    }

    override fun getContentLength(): Long {
        return source.getContentLength()
    }

    @Throws(IOException::class, IllegalStateException::class)
    override fun getContent(): InputStream {
        return source.open()
    }

    @Throws(IOException::class)
    override fun writeTo(outstream: OutputStream) {
        val content = getContent()
        try {
            IOUtils.copyLarge(content, outstream)
        } finally {
            IoActions.closeQuietly(content)
        }
    }

    override fun isStreaming(): Boolean {
        return true
    }
}
