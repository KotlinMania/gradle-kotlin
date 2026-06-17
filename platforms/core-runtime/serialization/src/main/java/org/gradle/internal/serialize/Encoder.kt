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

/**
 * Provides a way to encode structured data to a backing byte stream. Implementations may buffer outgoing encoded bytes prior
 * to writing to the backing byte stream.
 */
interface Encoder {
    /**
     * Returns an [OutputStream] that can be used to write raw bytes to the stream.
     */
    @JvmField
    val outputStream: OutputStream?

    /**
     * Writes a raw byte value to the stream.
     */
    @Throws(IOException::class)
    fun writeByte(value: Byte)

    /**
     * Writes the given raw bytes to the stream. Does not encode any length information.
     */
    @Throws(IOException::class)
    fun writeBytes(bytes: ByteArray?)

    /**
     * Writes the given raw bytes to the stream. Does not encode any length information.
     */
    @Throws(IOException::class)
    fun writeBytes(bytes: ByteArray?, offset: Int, count: Int)

    /**
     * Writes the given byte array to the stream. Encodes the bytes and length information.
     */
    @Throws(IOException::class)
    fun writeBinary(bytes: ByteArray?)

    /**
     * Writes the given byte array to the stream. Encodes the bytes and length information.
     */
    @Throws(IOException::class)
    fun writeBinary(bytes: ByteArray?, offset: Int, count: Int)

    /**
     * Appends an encoded stream to this stream. Encodes the stream as a series of chunks with length information.
     */
    @Throws(Exception::class)
    fun encodeChunked(writeAction: EncodeAction<Encoder?>?)

    /**
     * Writes a signed 64 bit long value. The implementation may encode the value as a variable number of bytes, not necessarily as 8 bytes.
     */
    @Throws(IOException::class)
    fun writeLong(value: Long)

    /**
     * Writes a signed 64 bit long value whose value is likely to be small and positive but may not be. The implementation may encode the value in a way that is more efficient for small positive
     * values.
     */
    @Throws(IOException::class)
    fun writeSmallLong(value: Long)

    /**
     * Writes a signed 32 bit int value. The implementation may encode the value as a variable number of bytes, not necessarily as 4 bytes.
     */
    @Throws(IOException::class)
    fun writeInt(value: Int)

    /**
     * Writes a signed 32 bit int value whose value is likely to be small and positive but may not be. The implementation may encode the value in a way that
     * is more efficient for small positive values.
     */
    @Throws(IOException::class)
    fun writeSmallInt(value: Int)

    /**
     * Writes a short value.
     *
     * @since 8.7
     */
    @Throws(IOException::class)
    fun writeShort(value: Short)

    /**
     * Writes a float value.
     *
     * @since 8.7
     */
    @Throws(IOException::class)
    fun writeFloat(value: Float)

    /**
     * Writes a double value.
     *
     * @since 8.7
     */
    @Throws(IOException::class)
    fun writeDouble(value: Double)

    /**
     * Writes a nullable signed 32 bit int value whose value is likely to be small and positive but may not be.
     *
     * @see .writeSmallInt
     */
    @Throws(IOException::class)
    fun writeNullableSmallInt(value: Int?)

    /**
     * Writes a boolean value.
     */
    @Throws(IOException::class)
    fun writeBoolean(value: Boolean)

    /**
     * Writes a non-null string value.
     */
    @Throws(IOException::class)
    fun writeString(value: CharSequence?)

    /**
     * Writes a nullable string value.
     */
    @Throws(IOException::class)
    fun writeNullableString(value: CharSequence?)

    interface EncodeAction<T> {
        @Throws(Exception::class)
        fun write(target: T?)
    }
}
