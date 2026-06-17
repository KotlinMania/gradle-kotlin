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

import com.google.common.collect.AbstractIterator
import org.gradle.api.internal.file.archive.ZipEntry
import org.gradle.api.internal.file.archive.ZipInput
import org.gradle.internal.file.FileException
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

class StreamZipInput(inputStream: InputStream) : ZipInput {
    private val inputStream: ZipInputStream

    init {
        this.inputStream = ZipInputStream(inputStream)
    }

    override fun iterator(): MutableIterator<ZipEntry?> {
        return object : AbstractIterator<ZipEntry?>() {
            override fun computeNext(): ZipEntry? {
                val nextEntry: java.util.zip.ZipEntry?
                try {
                    nextEntry = inputStream.getNextEntry()
                } catch (e: IOException) {
                    throw FileException(e)
                }
                return if (nextEntry == null) endOfData() else StreamZipInput.StreamZipEntry(inputStream, nextEntry)
            }
        }
    }

    @Throws(IOException::class)
    override fun close() {
        inputStream.close()
    }

    private class StreamZipEntry(private val inputStream: ZipInputStream, entry: java.util.zip.ZipEntry) : AbstractZipEntry(entry) {
        private var opened = false

        @Throws(IOException::class)
        override fun <T> withInputStream(action: ZipEntry.IoFunction<InputStream?, T?>?): T? {
            if (action == null) {
                return null
            }
            check(!opened) { "The input stream for " + name + " has already been opened.  It cannot be reopened again." }

            opened = true

            try {
                return action.apply(inputStream)
            } finally {
                closeEntry()
            }
        }

        fun closeEntry() {
            try {
                inputStream.closeEntry()
            } catch (e: IOException) {
                throw FileException(e)
            }
        }

        override fun canReopen(): Boolean {
            return false
        }
    }
}
