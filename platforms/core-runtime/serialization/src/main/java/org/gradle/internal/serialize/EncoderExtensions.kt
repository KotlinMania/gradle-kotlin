/*
 * Copyright 2024 the original author or authors.
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

import org.jspecify.annotations.NullMarked
import java.io.IOException

@NullMarked
object EncoderExtensions {
    @JvmStatic
    @Throws(IOException::class)
    fun writeLengthPrefixedShorts(encoder: Encoder, array: ShortArray) {
        encoder.writeInt(array.size)
        writeShorts(encoder, array)
    }

    @Throws(IOException::class)
    fun writeShorts(encoder: Encoder, array: ShortArray) {
        for (e in array) {
            encoder.writeShort(e)
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun writeLengthPrefixedInts(encoder: Encoder, array: IntArray) {
        encoder.writeInt(array.size)
        writeInts(encoder, array)
    }

    @Throws(IOException::class)
    fun writeInts(encoder: Encoder, array: IntArray) {
        for (e in array) {
            encoder.writeInt(e)
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun writeLengthPrefixedLongs(encoder: Encoder, array: LongArray) {
        encoder.writeInt(array.size)
        writeLongs(encoder, array)
    }

    @Throws(IOException::class)
    fun writeLongs(encoder: Encoder, array: LongArray) {
        for (e in array) {
            encoder.writeLong(e)
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun writeLengthPrefixedFloats(encoder: Encoder, array: FloatArray) {
        encoder.writeInt(array.size)
        writeFloats(encoder, array)
    }

    @Throws(IOException::class)
    fun writeFloats(encoder: Encoder, array: FloatArray) {
        for (e in array) {
            encoder.writeFloat(e)
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun writeLengthPrefixedDoubles(encoder: Encoder, array: DoubleArray) {
        encoder.writeInt(array.size)
        writeDoubles(encoder, array)
    }

    @Throws(IOException::class)
    fun writeDoubles(encoder: Encoder, array: DoubleArray) {
        for (e in array) {
            encoder.writeDouble(e)
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun writeLengthPrefixedChars(encoder: Encoder, array: CharArray) {
        encoder.writeInt(array.size)
        writeChars(encoder, array)
    }

    @Throws(IOException::class)
    fun writeChars(encoder: Encoder, array: CharArray) {
        for (e in array) {
            encoder.writeInt(e.code)
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun writeLengthPrefixedBooleans(encoder: Encoder, array: BooleanArray) {
        encoder.writeInt(array.size)
        writeBooleans(encoder, array)
    }

    @Throws(IOException::class)
    private fun writeBooleans(encoder: Encoder, array: BooleanArray) {
        for (e in array) {
            encoder.writeBoolean(e)
        }
    }
}
