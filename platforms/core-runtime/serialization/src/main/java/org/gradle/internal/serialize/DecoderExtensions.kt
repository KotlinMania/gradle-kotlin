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
object DecoderExtensions {
    @JvmStatic
    @Throws(IOException::class)
    fun readLengthPrefixedShorts(decoder: Decoder): ShortArray {
        val length = decoder.readInt()
        val array = ShortArray(length)
        readShorts(decoder, array)
        return array
    }

    @Throws(IOException::class)
    fun readShorts(decoder: Decoder, array: ShortArray) {
        for (i in array.indices) {
            array[i] = decoder.readShort()
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun readLengthPrefixedInts(decoder: Decoder): IntArray {
        val length = decoder.readInt()
        val array = IntArray(length)
        readInts(decoder, array)
        return array
    }

    @Throws(IOException::class)
    fun readInts(decoder: Decoder, array: IntArray) {
        for (i in array.indices) {
            array[i] = decoder.readInt()
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun readLengthPrefixedLongs(decoder: Decoder): LongArray {
        val length = decoder.readInt()
        val array = LongArray(length)
        readLongs(decoder, array)
        return array
    }

    @Throws(IOException::class)
    fun readLongs(decoder: Decoder, array: LongArray) {
        for (i in array.indices) {
            array[i] = decoder.readLong()
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun readLengthPrefixedFloats(decoder: Decoder): FloatArray {
        val length = decoder.readInt()
        val array = FloatArray(length)
        readFloats(decoder, array)
        return array
    }

    @Throws(IOException::class)
    fun readFloats(decoder: Decoder, array: FloatArray) {
        for (i in array.indices) {
            array[i] = decoder.readFloat()
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun readLengthPrefixedDoubles(decoder: Decoder): DoubleArray {
        val length = decoder.readInt()
        val array = DoubleArray(length)
        readDoubles(decoder, array)
        return array
    }

    @Throws(IOException::class)
    fun readDoubles(decoder: Decoder, array: DoubleArray) {
        for (i in array.indices) {
            array[i] = decoder.readDouble()
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun readLengthPrefixedChars(decoder: Decoder): CharArray {
        val length = decoder.readInt()
        val array = CharArray(length)
        readChars(decoder, array)
        return array
    }

    @Throws(IOException::class)
    fun readChars(decoder: Decoder, array: CharArray) {
        for (i in array.indices) {
            array[i] = decoder.readInt().toChar()
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun readLengthPrefixedBooleans(decoder: Decoder): BooleanArray {
        val length = decoder.readInt()
        val array = BooleanArray(length)
        readBooleans(decoder, array)
        return array
    }

    @Throws(IOException::class)
    fun readBooleans(decoder: Decoder, array: BooleanArray) {
        for (i in array.indices) {
            array[i] = decoder.readBoolean()
        }
    }
}
