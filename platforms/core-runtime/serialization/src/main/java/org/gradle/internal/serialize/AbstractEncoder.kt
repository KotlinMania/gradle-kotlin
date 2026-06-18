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

import java.io.IOException
import java.io.OutputStream

abstract class AbstractEncoder : Encoder {
    private var stream: EncoderStream? = null

    override val outputStream: OutputStream
        get() {
            if (stream == null) {
                stream = EncoderStream()
            }
            return stream!!
        }

    @Throws(IOException::class)
    override fun writeBytes(bytes: ByteArray) {
        writeBytes(bytes, 0, bytes.size)
    }

    @Throws(IOException::class)
    override fun writeBinary(bytes: ByteArray) {
        writeBinary(bytes, 0, bytes.size)
    }

    @Throws(IOException::class)
    override fun writeBinary(bytes: ByteArray, offset: Int, count: Int) {
        writeSmallInt(count)
        writeBytes(bytes, offset, count)
    }

    @Throws(Exception::class)
    override fun encodeChunked(writeAction: Encoder.EncodeAction<Encoder>) {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun writeSmallInt(value: Int) {
        writeInt(value)
    }

    @Throws(IOException::class)
    override fun writeSmallLong(value: Long) {
        writeLong(value)
    }

    @Throws(IOException::class)
    override fun writeNullableSmallInt(value: Int?) {
        if (value == null) {
            writeBoolean(false)
        } else {
            writeBoolean(true)
            writeSmallInt(value)
        }
    }

    @Throws(IOException::class)
    override fun writeNullableString(value: CharSequence?) {
        if (value == null) {
            writeBoolean(false)
        } else {
            writeBoolean(true)
            writeString(value.toString())
        }
    }

    private inner class EncoderStream : OutputStream() {
        @Throws(IOException::class)
        override fun write(buffer: ByteArray) {
            writeBytes(buffer)
        }

        @Throws(IOException::class)
        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            writeBytes(buffer, offset, length)
        }

        @Throws(IOException::class)
        override fun write(b: Int) {
            writeByte(b.toByte())
        }
    }
}
