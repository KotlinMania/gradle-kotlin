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
package org.gradle.api.internal.file.archive

import java.io.IOException
import java.io.InputStream

interface ZipEntry {
    /**
     * The compression method used for an entry
     */
    enum class ZipCompressionMethod {
        /**
         * The entry is compressed with DEFLATE algorithm.
         */
        DEFLATED,

        /**
         * The entry is stored uncompressed.
         */
        STORED,

        /**
         * The entry is compressed with some other method (Zip spec declares about a dozen of these).
         */
        OTHER,
    }

    val isDirectory: Boolean

    val name: String?

    @get:Throws(IOException::class)
    val content: ByteArray?

    /**
     * Declare an action to be run against this ZipEntry's content as a [InputStream].
     * The [InputStream] passed to the [IoFunction.apply] will
     * be closed right after the action's return.
     *
     * This method or [.getContent] may or may not support being called more than once per entry.
     * Use [.canReopen] to determine if more than one call is supported.
     */
    @Throws(IOException::class)
    fun <T> withInputStream(action: IoFunction<InputStream?, T?>?): T?

    /**
     * The size of the content in bytes, or -1 if not known.
     */
    fun size(): Int

    /**
     * Whether or not the zip entry can safely be read again if any bytes
     * have already been read from it.
     */
    fun canReopen(): Boolean

    val compressionMethod: ZipCompressionMethod?

    // Copied from org.gradle.internal.io.IoFunction in :functional due to
    // JDK version mismatch between the projects
    interface IoFunction<T : Any?, R : Any?> {
        @Throws(IOException::class)
        fun apply(t: T?): R?
    }
}
