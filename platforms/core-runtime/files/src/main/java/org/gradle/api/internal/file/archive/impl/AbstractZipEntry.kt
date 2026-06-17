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
    override fun isDirectory(): Boolean {
        return entry.isDirectory()
    }

    override fun getName(): String {
        return entry.getName()
    }

    override fun size(): Int {
        return entry.getSize().toInt()
    }

    @Throws(IOException::class)
    override fun getContent(): ByteArray? {
        return withInputStream<ByteArray?>(object : org.gradle.api.internal.file.archive.ZipEntry.IoFunction<InputStream?, ByteArray?> {
            @Throws(IOException::class)
            override fun apply(inputStream: InputStream): ByteArray? {
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

    override fun getCompressionMethod(): org.gradle.api.internal.file.archive.ZipEntry.ZipCompressionMethod {
        when (entry.getMethod()) {
            ZipEntry.STORED -> return org.gradle.api.internal.file.archive.ZipEntry.ZipCompressionMethod.STORED
            ZipEntry.DEFLATED -> return org.gradle.api.internal.file.archive.ZipEntry.ZipCompressionMethod.DEFLATED
            else -> return org.gradle.api.internal.file.archive.ZipEntry.ZipCompressionMethod.OTHER
        }
    }
}
