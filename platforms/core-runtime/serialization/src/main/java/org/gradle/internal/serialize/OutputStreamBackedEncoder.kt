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
import java.io.DataOutputStream
import java.io.IOException
import java.io.OutputStream

class OutputStreamBackedEncoder(outputStream: OutputStream?) : AbstractEncoder(), Closeable, FlushableEncoder {
    private val outputStream: DataOutputStream

    init {
        this.outputStream = DataOutputStream(outputStream)
    }

    @Throws(IOException::class)
    override fun writeLong(value: Long) {
        outputStream.writeLong(value)
    }

    @Throws(IOException::class)
    override fun writeInt(value: Int) {
        outputStream.writeInt(value)
    }

    @Throws(IOException::class)
    override fun writeShort(value: Short) {
        outputStream.writeShort(value.toInt())
    }

    @Throws(IOException::class)
    override fun writeFloat(value: Float) {
        outputStream.writeFloat(value)
    }

    @Throws(IOException::class)
    override fun writeDouble(value: Double) {
        outputStream.writeDouble(value)
    }

    @Throws(IOException::class)
    override fun writeBoolean(value: Boolean) {
        outputStream.writeBoolean(value)
    }

    @Throws(IOException::class)
    override fun writeString(value: CharSequence) {
        requireNotNull(value) { "Cannot encode a null string." }
        outputStream.writeUTF(value.toString())
    }

    @Throws(IOException::class)
    override fun writeByte(value: Byte) {
        outputStream.writeByte(value.toInt())
    }

    @Throws(IOException::class)
    override fun writeBytes(bytes: ByteArray?, offset: Int, count: Int) {
        outputStream.write(bytes, offset, count)
    }

    @Throws(IOException::class)
    override fun flush() {
    }

    @Throws(IOException::class)
    override fun close() {
        outputStream.close()
    }
}
