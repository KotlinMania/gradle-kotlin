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
import org.gradle.api.internal.file.archive.ZipInput
import org.gradle.internal.file.FileException
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.Enumeration
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class FileZipInput private constructor(file: File) : ZipInput {
    private val file: ZipFile
    private val entries: Enumeration<out ZipEntry?>

    init {
        try {
            this.file = ZipFile(file)
        } catch (e: IOException) {
            throw FileException(e)
        }
        this.entries = this.file.entries()
    }

    override fun iterator(): MutableIterator<org.gradle.api.internal.file.archive.ZipEntry?> {
        return object : AbstractIterator<org.gradle.api.internal.file.archive.ZipEntry?>() {
            override fun computeNext(): org.gradle.api.internal.file.archive.ZipEntry? {
                if (!entries.hasMoreElements()) {
                    return endOfData()
                }
                return FileZipInput.FileZipEntry(entries.nextElement())
            }
        }
    }

    @Throws(IOException::class)
    override fun close() {
        file.close()
    }

    private inner class FileZipEntry(entry: ZipEntry?) : AbstractZipEntry(entry) {
        @Throws(IOException::class)
        override fun <T> withInputStream(action: org.gradle.api.internal.file.archive.ZipEntry.IoFunction<InputStream?, T?>): T? {
            val inputStream = this.inputStream
            try {
                return action.apply(inputStream)
            } finally {
                inputStream.close()
            }
        }

        val inputStream: InputStream
            get() {
                try {
                    return file.getInputStream(getEntry())
                } catch (e: IOException) {
                    throw FileException(e)
                }
            }

        override fun canReopen(): Boolean {
            return true
        }
    }

    companion object {
        /**
         * Creates a stream of the entries in the given zip file. Caller is responsible for closing the return value.
         *
         * @throws FileException on failure to open the Zip
         */
        @JvmStatic
        @Throws(FileException::class)
        fun create(file: File): ZipInput {
            if (isZipFileSafeToUse) {
                return FileZipInput(file)
            } else {
                try {
                    return StreamZipInput(FileInputStream(file))
                } catch (e: FileNotFoundException) {
                    throw FileException(e)
                }
            }
        }

        private val isZipFileSafeToUse: Boolean
            /**
             * [ZipFile] is more efficient, but causes memory leaks on older Java versions, so we only use it on more recent ones.
             */
            get() {
                val versionString = System.getProperty("java.specification.version")
                // Java versions 8 and older had 1.8 versioning scheme, later ones have single number 9, 10, ...
                val versionParts: Array<String?> = versionString.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                require(versionParts.size >= 1) { "Could not determine java version from '" + versionString + "'." }
                return versionParts[0]!!.toInt() >= 11
            }
    }
}
