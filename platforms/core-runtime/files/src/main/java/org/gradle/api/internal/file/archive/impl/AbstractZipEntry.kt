/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.internal.file.archive.impl

import com.google.common.io.ByteStreams
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipEntry

internal abstract class AbstractZipEntry(protected val entry: ZipEntry) : org.gradle.api.internal.file.archive.ZipEntry {
    override val isDirectory: Boolean
        get() {
            return entry.isDirectory()
        }

    override val name: String?
        get() {
        return entry.getName()
    }

    override fun size(): Int {
        return entry.getSize().toInt()
    }

    @get:Throws(IOException::class)
    override val content: ByteArray?
        get() {
        return withInputStream<ByteArray?>(object : org.gradle.api.internal.file.archive.ZipEntry.IoFunction<InputStream?, ByteArray?> {
            @Throws(IOException::class)
            override fun apply(inputStream: InputStream?): ByteArray? {
                if (inputStream == null) {
                    return null
                }
                val size = size()
                if (size >= 0) {
                    val content = ByteArray(size)
                    ByteStreams.readFully(inputStream, content)
                    return content
                } else {
                    return ByteStreams.toByteArray(inputStream)
                }
            }
        })
    }

    override val compressionMethod: org.gradle.api.internal.file.archive.ZipEntry.ZipCompressionMethod?
        get() {
            return when (entry.getMethod()) {
                ZipEntry.STORED -> org.gradle.api.internal.file.archive.ZipEntry.ZipCompressionMethod.STORED
                ZipEntry.DEFLATED -> org.gradle.api.internal.file.archive.ZipEntry.ZipCompressionMethod.DEFLATED
                else -> org.gradle.api.internal.file.archive.ZipEntry.ZipCompressionMethod.OTHER
            }
        }
    
}
