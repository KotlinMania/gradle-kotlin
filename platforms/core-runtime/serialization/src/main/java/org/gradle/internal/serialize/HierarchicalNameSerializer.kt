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

import com.google.common.base.CharMatcher
import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap
import com.google.common.collect.Interner
import java.io.IOException

/**
 * Efficiently serializes hierarchical names, like Java class names or relative paths of resources.
 * Splits names into prefixes and suffixes along package separators, inner class separators, file separators and camel case borders.
 * Reuses these prefixes and suffixes to efficiently store names or parts of names it has seen before.
 *
 * This class is stateful. Use a new one for each serialization/deserialization attempt.
 */
class HierarchicalNameSerializer(private val interner: Interner<String>) : AbstractSerializer<String>() {
    private val namesById: BiMap<Int, String> = HashBiMap.create<Int, String>()

    @Throws(Exception::class)
    override fun read(decoder: Decoder): String {
        val name = readName(decoder)
        return interner.intern(name)
    }

    @Throws(Exception::class)
    override fun write(encoder: Encoder, name: String) {
        writeName(name, encoder)
    }

    @Throws(IOException::class)
    private fun readName(decoder: Decoder): String {
        val id = decoder.readSmallInt()
        var name = namesById.get(id)
        if (name == null) {
            name = readFirstOccurrenceOfName(decoder)
            namesById.put(id, name)
        }
        return name!!
    }

    @Throws(IOException::class)
    private fun readFirstOccurrenceOfName(decoder: Decoder): String {
        val separator = decoder.readByte()
        if (separator.toInt() == 0) {
            return decoder.readString()
        } else {
            val parent = readName(decoder)
            val child = readName(decoder)
            return parent + (separator.toInt() and 0xFF).toChar() + child
        }
    }

    @Throws(IOException::class)
    private fun writeName(name: String, encoder: Encoder) {
        var id = namesById.inverse().get(name)
        if (id == null) {
            id = namesById.inverse().size
            namesById.inverse().put(name, id)
            encoder.writeSmallInt(id)
            writeFirstOccurrenceOfName(name, encoder)
        } else {
            encoder.writeSmallInt(id)
        }
    }

    @Throws(IOException::class)
    private fun writeFirstOccurrenceOfName(name: String, encoder: Encoder) {
        val separator: Int = SEPARATOR_MATCHER.lastIndexIn(name)
        if (separator > 0) {
            val parent = name.substring(0, separator)
            val child = name.substring(separator + 1)
            encoder.writeByte(name.get(separator).code.toByte())
            writeName(parent, encoder)
            writeName(child, encoder)
        } else {
            encoder.writeByte(0.toByte())
            encoder.writeString(name)
        }
    }

    companion object {
        private val SEPARATOR_MATCHER = CharMatcher.anyOf(".$/").or(CharMatcher.inRange('A', 'Z'))
    }
}
