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

import com.esotericsoftware.kryo.io.Output
import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import org.gradle.internal.serialize.AbstractEncoder
import org.gradle.internal.serialize.FlushableEncoder
import org.gradle.internal.serialize.PositionAwareEncoder
import java.io.Closeable
import java.io.IOException
import java.io.OutputStream

class StringDeduplicatingKryoBackedEncoder @JvmOverloads constructor(outputStream: OutputStream, bufferSize: Int = 4096) : AbstractEncoder(), PositionAwareEncoder, FlushableEncoder, Closeable {
    private var strings: Object2IntMap<String?>? = null

    private val output: Output

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

    override fun writeNullableString(value: CharSequence?) {
        if (value == null) {
            writeStringIndex(NULL_STRING)
            return
        }
        writeNonnullString(value)
    }

    override fun writeString(value: CharSequence?) {
        requireNotNull(value) { "Cannot encode a null string." }
        writeNonnullString(value)
    }

    private fun writeNonnullString(value: CharSequence) {
        val key = value.toString()
        if (strings == null) {
            strings = Object2IntOpenHashMap<String?>(1024)
            writeNewString(key)
        } else {
            val index = strings!!.getOrDefault(key, -1)
            if (index == -1) {
                writeNewString(key)
            } else {
                writeStringIndex(index)
            }
        }
    }

    private fun writeNewString(key: String?) {
        /*
          Actual stored string indices start from 2 so `0` and `1` can be used as special codes:
          - 0 for null
          - 1 for a new string
          And be efficiently encoded as var ints (writeVarInt/readVarInt) to save even more space.
         */
        val newIndex: Int = strings!!.size + 2
        strings!!.put(key, newIndex)
        writeStringIndex(NEW_STRING)
        output.writeString(key)
    }

    private fun writeStringIndex(index: Int) {
        output.writeVarInt(index, true)
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

    fun done() {
        strings = null
    }

    companion object {
        const val NULL_STRING: Int = 0
        const val NEW_STRING: Int = 1
    }
}
