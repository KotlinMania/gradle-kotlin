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

import com.esotericsoftware.kryo.io.Output
import org.gradle.internal.serialize.AbstractEncoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.FlushableEncoder
import org.gradle.internal.serialize.PositionAwareEncoder
import java.io.Closeable
import java.io.IOException
import java.io.OutputStream

class KryoBackedEncoder @JvmOverloads constructor(outputStream: OutputStream, bufferSize: Int = 4096) : AbstractEncoder(), PositionAwareEncoder, FlushableEncoder, Closeable {
    private val output: Output
    private var nested: KryoBackedEncoder? = null

    init {
        output = Output(outputStream, bufferSize)
    }

    override fun writeByte(value: Byte) {
        output.writeByte(value)
    }

    override fun writeBytes(bytes: ByteArray, offset: Int, count: Int) {
        output.writeBytes(bytes, offset, count)
    }

    override fun writeLong(value: Long) {
        output.writeLong(value)
    }

    override fun writeSmallLong(value: Long) {
        output.writeLong(value, true)
    }

    override fun writeInt(value: Int) {
        output.writeInt(value)
    }

    override fun writeSmallInt(value: Int) {
        output.writeInt(value, true)
    }

    @Throws(IOException::class)
    override fun writeShort(value: Short) {
        output.writeShort(value.toInt())
    }

    @Throws(IOException::class)
    override fun writeFloat(value: Float) {
        output.writeFloat(value)
    }

    @Throws(IOException::class)
    override fun writeDouble(value: Double) {
        output.writeDouble(value)
    }

    override fun writeBoolean(value: Boolean) {
        output.writeBoolean(value)
    }

    override fun writeString(value: CharSequence?) {
        requireNotNull(value) { "Cannot encode a null string." }
        output.writeString(value)
    }

    override fun writeNullableString(value: CharSequence?) {
        output.writeString(value)
    }

    @Throws(Exception::class)
    override fun encodeChunked(writeAction: Encoder.EncodeAction<Encoder>) {
        if (nested == null) {
            nested = KryoBackedEncoder(object : OutputStream() {
                override fun write(buffer: ByteArray, offset: Int, length: Int) {
                    if (length == 0) {
                        return
                    }
                    writeSmallInt(length)
                    writeBytes(buffer, offset, length)
                }

                @Throws(IOException::class)
                override fun write(buffer: ByteArray) {
                    write(buffer, 0, buffer.size)
                }

                override fun write(b: Int) {
                    throw UnsupportedOperationException()
                }
            })
        }
        writeAction.write(nested!!)
        nested!!.flush()
        writeSmallInt(0)
    }

    /**
     * Returns the total number of bytes written by this encoder, some of which may still be buffered.
     */
    override val writePosition: Long
        get() = output.total()

    override fun flush() {
        output.flush()
    }

    override fun close() {
        output.close()
    }
}
