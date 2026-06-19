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
package org.gradle.internal.serialize.kryo

import com.esotericsoftware.kryo.KryoException
import com.esotericsoftware.kryo.io.Input
import org.gradle.internal.serialize.AbstractDecoder
import org.gradle.internal.serialize.Decoder
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import kotlin.math.min

/**
 * Note that this decoder uses buffering, so will attempt to read beyond the end of the encoded data. This means you should use this type only when this decoder will be used to decode the entire
 * stream.
 */
class KryoBackedDecoder @JvmOverloads constructor(inputStream: InputStream, bufferSize: Int = 4096) : AbstractDecoder(), Decoder, Closeable {
    private var backingInputStream: InputStream
    private val input: Input
    private var extraSkipped: Long = 0
    private var nested: KryoBackedDecoder? = null

    init {
        backingInputStream = inputStream
        input = Input(backingInputStream, bufferSize)
    }

    fun restart(inputStream: InputStream) {
        backingInputStream = inputStream
        input.setInputStream(inputStream)
        extraSkipped = 0
    }

    override fun maybeReadBytes(buffer: ByteArray, offset: Int, count: Int): Int {
        return input.read(buffer, offset, count)
    }

    @Throws(IOException::class)
    override fun maybeSkip(count: Long): Long {
        // Work around some bugs in Input.skip()
        val remaining = input.limit() - input.position()
        if (remaining == 0) {
            val skipped = backingInputStream.skip(count)
            if (skipped > 0) {
                extraSkipped += skipped
            }
            return skipped
        } else if (count <= remaining) {
            input.setPosition(input.position() + count.toInt())
            return count
        } else {
            input.setPosition(input.limit())
            return remaining.toLong()
        }
    }

    @Throws(EOFException::class)
    private fun maybeEndOfStream(e: KryoException): Throwable {
        if (e.message == "Buffer underflow.") {
            return EOFException().also { it.initCause(e) }
        }
        return e
    }

    @Throws(EOFException::class)
    override fun readByte(): Byte {
        try {
            return input.readByte()
        } catch (e: KryoException) {
            throw maybeEndOfStream(e)
        }
    }

    @Throws(EOFException::class)
    override fun readBytes(buffer: ByteArray, offset: Int, count: Int) {
        try {
            input.readBytes(buffer, offset, count)
        } catch (e: KryoException) {
            throw maybeEndOfStream(e)
        }
    }

    @Throws(EOFException::class)
    override fun readLong(): Long {
        try {
            return input.readLong()
        } catch (e: KryoException) {
            throw maybeEndOfStream(e)
        }
    }

    @Throws(EOFException::class, IOException::class)
    override fun readSmallLong(): Long {
        try {
            return input.readLong(true)
        } catch (e: KryoException) {
            throw maybeEndOfStream(e)
        }
    }

    @Throws(EOFException::class)
    override fun readInt(): Int {
        try {
            return input.readInt()
        } catch (e: KryoException) {
            throw maybeEndOfStream(e)
        }
    }

    @Throws(EOFException::class)
    override fun readSmallInt(): Int {
        try {
            return input.readInt(true)
        } catch (e: KryoException) {
            throw maybeEndOfStream(e)
        }
    }

    @Throws(EOFException::class, IOException::class)
    override fun readShort(): Short {
        try {
            return input.readShort()
        } catch (e: KryoException) {
            throw maybeEndOfStream(e)
        }
    }

    @Throws(EOFException::class, IOException::class)
    override fun readFloat(): Float {
        try {
            return input.readFloat()
        } catch (e: KryoException) {
            throw maybeEndOfStream(e)
        }
    }

    @Throws(EOFException::class, IOException::class)
    override fun readDouble(): Double {
        try {
            return input.readDouble()
        } catch (e: KryoException) {
            throw maybeEndOfStream(e)
        }
    }

    @Throws(EOFException::class)
    override fun readBoolean(): Boolean {
        try {
            return input.readBoolean()
        } catch (e: KryoException) {
            throw maybeEndOfStream(e)
        }
    }

    @Throws(EOFException::class)
    override fun readString(): String {
        return requireNotNull(readNullableString()) { "Cannot decode a null string." }
    }

    @Throws(EOFException::class)
    override fun readNullableString(): String? {
        try {
            return input.readString()
        } catch (e: KryoException) {
            throw maybeEndOfStream(e)
        }
    }

    @Throws(EOFException::class, IOException::class)
    override fun skipChunked() {
        while (true) {
            val count = readSmallInt()
            if (count == 0) {
                break
            }
            skipBytes(count.toLong())
        }
    }

    @Throws(EOFException::class, Exception::class)
    override fun <T> decodeChunked(decodeAction: Decoder.DecodeAction<Decoder, T>): T {
        if (nested == null) {
            nested = KryoBackedDecoder(object : InputStream() {
                private var leftover = 0

                @Throws(IOException::class)
                override fun read(): Int {
                    throw UnsupportedOperationException()
                }

                @Throws(IOException::class)
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    if (leftover > 0) {
                        val count = min(leftover, length)
                        leftover -= count
                        readBytes(buffer, offset, count)
                        return count
                    }

                    var count = readSmallInt()
                    if (count == 0) {
                        // End of stream has been reached
                        return -1
                    }
                    if (count > length) {
                        leftover = count - length
                        count = length
                    }
                    readBytes(buffer, offset, count)
                    return count
                }
            })
        }
        val value = decodeAction.read(nested!!)
        check(readSmallInt() == 0) { "Expecting the end of nested stream." }
        return value
    }

    val readPosition: Long
        /**
         * Returns the total number of bytes consumed by this decoder. Some additional bytes may also be buffered by this decoder but have not been consumed.
         */
        get() = input.total() + extraSkipped

    @Throws(IOException::class)
    override fun close() {
        input.close()
    }
}
