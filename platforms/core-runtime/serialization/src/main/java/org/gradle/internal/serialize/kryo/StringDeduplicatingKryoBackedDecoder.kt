/*
 * Copyright 2018 the original author or authors.
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

/**
 * Note that this decoder uses buffering, so will attempt to read beyond the end of the encoded data. This means you should use this type only when this decoder will be used to decode the entire
 * stream.
 */
class StringDeduplicatingKryoBackedDecoder @JvmOverloads constructor(override val inputStream: InputStream, bufferSize: Int = 4096) : AbstractDecoder(), Decoder, Closeable {
    private val input: Input
    private var strings: Array<String?>? = INITIAL_CAPACITY_MARKER

    /**
     * Actual stored string indices start from 2 so `0` and `1` can be used as special codes:
     *
     *  * 0 for null
     *  * 1 for a new string
     *
     * And be efficiently encoded as var ints (writeVarInt/readVarInt) to save even more space.
     *
     * @see StringDeduplicatingKryoBackedEncoder.NULL_STRING
     *
     * @see StringDeduplicatingKryoBackedEncoder.NEW_STRING
     */
    private var nextString = 2
    private var extraSkipped: Long = 0

    init {
        input = Input(this.inputStream, bufferSize)
    }

    override fun maybeReadBytes(buffer: ByteArray, offset: Int, count: Int): Int {
        return input.read(buffer, offset, count)
    }

    @Throws(IOException::class)
    override fun maybeSkip(count: Long): Long {
        // Work around some bugs in Input.skip()
        val remaining = input.limit() - input.position()
        if (remaining == 0) {
            val skipped = inputStream.skip(count)
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
            val index = readStringIndex()
            when (index) {
                StringDeduplicatingKryoBackedEncoder.Companion.NULL_STRING -> return null
                StringDeduplicatingKryoBackedEncoder.Companion.NEW_STRING -> return readNewString()
                else -> return strings!![index]
            }
        } catch (e: KryoException) {
            throw maybeEndOfStream(e)
        }
    }

    private fun readStringIndex(): Int {
        return input.readVarInt(true)
    }

    private fun readNewString(): String? {
        if (nextString >= strings!!.size) {
            strings = Companion.growStringArray(strings!!)
        }
        val string = input.readString()
        strings!![nextString++] = string
        return string
    }

    val readPosition: Long
        /**
         * Returns the total number of bytes consumed by this decoder. Some additional bytes may also be buffered by this decoder but have not been consumed.
         */
        get() = input.total() + extraSkipped

    @Throws(IOException::class)
    override fun close() {
        strings = null
        input.close()
    }

    companion object {
        private const val INITIAL_CAPACITY = 32
        private val INITIAL_CAPACITY_MARKER = arrayOf<String?>()
        private fun growStringArray(strings: Array<String?>): Array<String?> {
            val grow = arrayOfNulls<String>(if (strings == INITIAL_CAPACITY_MARKER) INITIAL_CAPACITY else strings.size * 3 / 2)
            System.arraycopy(strings, 0, grow, 0, strings.size)
            return grow
        }
    }
}
