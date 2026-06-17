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

import java.io.Closeable
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream

class InputStreamBackedDecoder(private val inputStream: DataInputStream) : AbstractDecoder(), Decoder, Closeable {
    constructor(inputStream: InputStream) : this(DataInputStream(inputStream))

    @Throws(IOException::class)
    override fun maybeReadBytes(buffer: ByteArray?, offset: Int, count: Int): Int {
        return inputStream.read(buffer, offset, count)
    }

    @Throws(IOException::class)
    override fun maybeSkip(count: Long): Long {
        return inputStream.skip(count)
    }

    @Throws(IOException::class)
    override fun readLong(): Long {
        return inputStream.readLong()
    }

    @Throws(EOFException::class, IOException::class)
    override fun readInt(): Int {
        return inputStream.readInt()
    }

    @Throws(EOFException::class, IOException::class)
    override fun readShort(): Short {
        return inputStream.readShort()
    }

    @Throws(EOFException::class, IOException::class)
    override fun readFloat(): Float {
        return inputStream.readFloat()
    }

    @Throws(EOFException::class, IOException::class)
    override fun readDouble(): Double {
        return inputStream.readDouble()
    }

    @Throws(EOFException::class, IOException::class)
    override fun readBoolean(): Boolean {
        return inputStream.readBoolean()
    }

    @Throws(EOFException::class, IOException::class)
    override fun readString(): String? {
        return inputStream.readUTF()
    }

    @Throws(IOException::class)
    override fun readByte(): Byte {
        return (inputStream.readByte().toInt() and 0xff).toByte()
    }

    @Throws(IOException::class)
    override fun readBytes(buffer: ByteArray?, offset: Int, count: Int) {
        inputStream.readFully(buffer, offset, count)
    }

    @Throws(IOException::class)
    override fun close() {
        inputStream.close()
    }
}
