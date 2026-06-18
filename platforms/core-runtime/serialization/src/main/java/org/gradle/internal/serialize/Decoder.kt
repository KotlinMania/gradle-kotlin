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

/**
 * Provides a way to decode structured data from a backing byte stream. Implementations may buffer incoming bytes read
 * from the backing stream prior to decoding.
 */
interface Decoder {
    /**
     * Returns an InputStream which can be used to read raw bytes.
     */
    val inputStream: InputStream

    /**
     * Reads a signed 64 bit long value. Can read any value that was written using [Encoder.writeLong].
     *
     * @throws EOFException when the end of the byte stream is reached before the long value can be fully read.
     */
    @Throws(EOFException::class, IOException::class)
    fun readLong(): Long

    /**
     * Reads a signed 64 bit int value. Can read any value that was written using [Encoder.writeSmallLong].
     *
     * @throws EOFException when the end of the byte stream is reached before the int value can be fully read.
     */
    @Throws(EOFException::class, IOException::class)
    fun readSmallLong(): Long

    /**
     * Reads a signed 32 bit int value. Can read any value that was written using [Encoder.writeInt].
     *
     * @throws EOFException when the end of the byte stream is reached before the int value can be fully read.
     */
    @Throws(EOFException::class, IOException::class)
    fun readInt(): Int

    /**
     * Reads a signed 32 bit int value. Can read any value that was written using [Encoder.writeSmallInt].
     *
     * @throws EOFException when the end of the byte stream is reached before the int value can be fully read.
     */
    @Throws(EOFException::class, IOException::class)
    fun readSmallInt(): Int

    /**
     * Reads a nullable signed 32 bit int value.
     *
     * @see .readSmallInt
     */
    @Throws(EOFException::class, IOException::class)
    fun readNullableSmallInt(): Int?

    /**
     * Reads a short value that was written with [Encoder.writeShort]
     *
     * @throws EOFException when the end of the byte stream is reached before the short value can be fully read.
     * @since 8.7
     */
    @Throws(EOFException::class, IOException::class)
    fun readShort(): Short

    /**
     * Reads a float value that was written with [Encoder.writeFloat]
     *
     * @throws EOFException when the end of the byte stream is reached before the float value can be fully read.
     * @since 8.7
     */
    @Throws(EOFException::class, IOException::class)
    fun readFloat(): Float

    /**
     * Reads a double value that was written with [Encoder.writeDouble]
     *
     * @throws EOFException when the end of the byte stream is reached before the double value can be fully read.
     * @since 8.7
     */
    @Throws(EOFException::class, IOException::class)
    fun readDouble(): Double

    /**
     * Reads a boolean value. Can read any value that was written using [Encoder.writeBoolean].
     *
     * @throws EOFException when the end of the byte stream is reached before the boolean value can be fully read.
     */
    @Throws(EOFException::class, IOException::class)
    fun readBoolean(): Boolean

    /**
     * Reads a non-null string value. Can read any value that was written using [Encoder.writeString].
     *
     * @throws EOFException when the end of the byte stream is reached before the string can be fully read.
     */
    @Throws(EOFException::class, IOException::class)
    fun readString(): String

    /**
     * Reads a nullable string value. Can reads any value that was written using [Encoder.writeNullableString].
     *
     * @throws EOFException when the end of the byte stream is reached before the string can be fully read.
     */
    @Throws(EOFException::class, IOException::class)
    fun readNullableString(): String?

    /**
     * Reads a byte value. Can read any byte value that was written using one of the raw byte methods on [Encoder], such as [Encoder.writeByte] or [Encoder.getOutputStream]
     *
     * @throws EOFException when the end of the byte stream is reached.
     */
    @Throws(EOFException::class, IOException::class)
    fun readByte(): Byte

    /**
     * Reads bytes into the given buffer, filling the buffer. Can read any byte values that were written using one of the raw byte methods on [Encoder], such as [ ][Encoder.writeBytes] or [Encoder.getOutputStream]
     *
     * @throws EOFException when the end of the byte stream is reached before the buffer is full.
     */
    @Throws(EOFException::class, IOException::class)
    fun readBytes(buffer: ByteArray)

    /**
     * Reads the specified number of bytes into the given buffer. Can read any byte values that were written using one of the raw byte methods on [Encoder], such as [ ][Encoder.writeBytes] or [Encoder.getOutputStream]
     *
     * @throws EOFException when the end of the byte stream is reached before the specified number of bytes were read.
     */
    @Throws(EOFException::class, IOException::class)
    fun readBytes(buffer: ByteArray, offset: Int, count: Int)

    /**
     * Reads a byte array. Can read any byte array written using [Encoder.writeBinary] or [Encoder.writeBinary].
     *
     * @throws EOFException when the end of the byte stream is reached before the byte array was fully read.
     */
    @Throws(EOFException::class, IOException::class)
    fun readBinary(): ByteArray

    /**
     * Skips the given number of bytes. Can skip over any byte values that were written using one of the raw byte methods on [Encoder].
     */
    @Throws(EOFException::class, IOException::class)
    fun skipBytes(count: Long)

    /**
     * Reads a byte stream written using [Encoder.encodeChunked].
     */
    @Throws(EOFException::class, Exception::class)
    fun <T> decodeChunked(decodeAction: DecodeAction<Decoder, T>): T

    /**
     * Skips over a byte stream written using [Encoder.encodeChunked], discarding its content.
     */
    @Throws(EOFException::class, IOException::class)
    fun skipChunked()

    interface DecodeAction<IN, OUT> {
        @Throws(Exception::class)
        fun read(source: IN): OUT
    }
}
