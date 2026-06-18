/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.internal.serialize

import java.io.EOFException
import java.io.IOException
import java.io.InputStream

abstract class AbstractDecoder : Decoder {
    private var stream: DecoderStream? = null

    override val inputStream: InputStream
        get() {
            if (stream == null) {
                stream = DecoderStream()
            }
            return stream!!
        }

    @Throws(IOException::class)
    override fun readBytes(buffer: ByteArray) {
        readBytes(buffer, 0, buffer.size)
    }

    @Throws(EOFException::class, IOException::class)
    override fun readBinary(): ByteArray {
        val size = readSmallInt()
        val result = ByteArray(size)
        readBytes(result)
        return result
    }

    @Throws(EOFException::class, IOException::class)
    override fun readSmallInt(): Int {
        return readInt()
    }

    @Throws(EOFException::class, IOException::class)
    override fun readSmallLong(): Long {
        return readLong()
    }

    @Throws(IOException::class)
    override fun readNullableSmallInt(): Int? {
        if (readBoolean()) {
            return readSmallInt()
        } else {
            return null
        }
    }

    @Throws(EOFException::class, IOException::class)
    override fun readNullableString(): String? {
        if (readBoolean()) {
            return readString()
        } else {
            return null
        }
    }

    @Throws(EOFException::class, IOException::class)
    override fun skipBytes(count: Long) {
        var remaining = count
        while (remaining > 0) {
            val skipped = maybeSkip(remaining)
            if (skipped <= 0) {
                break
            }
            remaining -= skipped
        }
        if (remaining > 0) {
            throw EOFException()
        }
    }

    @Throws(EOFException::class, Exception::class)
    override fun <T> decodeChunked(decodeAction: Decoder.DecodeAction<Decoder, T>): T {
        throw UnsupportedOperationException()
    }

    @Throws(EOFException::class, IOException::class)
    override fun skipChunked() {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    protected abstract fun maybeReadBytes(buffer: ByteArray, offset: Int, count: Int): Int

    @Throws(IOException::class)
    protected abstract fun maybeSkip(count: Long): Long

    private inner class DecoderStream : InputStream() {
        var buffer: ByteArray = ByteArray(1)

        @Throws(IOException::class)
        override fun skip(n: Long): Long {
            return maybeSkip(n)
        }

        @Throws(IOException::class)
        override fun read(): Int {
            val read = maybeReadBytes(buffer, 0, 1)
            if (read <= 0) {
                return read
            }
            return buffer[0].toInt() and 0xff
        }

        @Throws(IOException::class)
        override fun read(buffer: ByteArray): Int {
            return maybeReadBytes(buffer, 0, buffer.size)
        }

        @Throws(IOException::class)
        override fun read(buffer: ByteArray?, offset: Int, count: Int): Int {
            return maybeReadBytes(buffer, offset, count)
        }
    }
}
