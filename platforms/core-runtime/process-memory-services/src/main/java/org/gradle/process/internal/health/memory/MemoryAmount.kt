/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.process.internal.health.memory

import com.google.common.base.Objects
import com.google.common.base.Preconditions

class MemoryAmount private constructor(bytes: Long, notation: String?) {
    val bytes: Long
    private val notation: String?

    init {
        Preconditions.checkArgument(bytes > 0, "bytes must be positive")
        this.bytes = bytes
        this.notation = notation
    }

    override fun toString(): String {
        return notation!!
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val that = o as MemoryAmount
        return bytes == that.bytes
    }

    override fun hashCode(): Int {
        return Objects.hashCode(bytes)
    }

    companion object {
        private const val KILO_FACTOR: Long = 1024
        private val MEGA_FACTOR: Long = KILO_FACTOR * 1024
        private val GIGA_FACTOR: Long = MEGA_FACTOR * 1024
        private val TERA_FACTOR: Long = GIGA_FACTOR * 1024

        fun of(bytes: Long): MemoryAmount {
            return MemoryAmount(bytes, bytes.toString())
        }

        fun ofKiloBytes(kiloBytes: Long): MemoryAmount {
            val bytes: Long = kiloBytes * KILO_FACTOR
            return MemoryAmount(bytes, bytes.toString() + "k")
        }

        fun ofMegaBytes(megaBytes: Long): MemoryAmount {
            val bytes: Long = megaBytes * MEGA_FACTOR
            return MemoryAmount(bytes, bytes.toString() + "m")
        }

        fun ofGigaBytes(gigaBytes: Long): MemoryAmount {
            val bytes: Long = gigaBytes * GIGA_FACTOR
            return MemoryAmount(bytes, bytes.toString() + "g")
        }

        fun ofTeraBytes(teraBytes: Long): MemoryAmount {
            val bytes: Long = teraBytes * TERA_FACTOR
            return MemoryAmount(bytes, bytes.toString() + "t")
        }

        fun of(notation: String?): MemoryAmount {
            return MemoryAmount(parseNotation(notation), notation)
        }

        /**
         * Parse memory amount notation.
         *
         * @return The parsed memory amount in bytes, -1 if the notation is null or empty.
         * @throws IllegalArgumentException if the notation is invalid
         */
        @JvmStatic
        fun parseNotation(notation: String?): Long {
            if (notation == null) {
                return -1
            }
            val normalized = notation.lowercase().trim { it <= ' ' }
            if (normalized.isEmpty()) {
                return -1
            }
            try {
                if (normalized.endsWith("k")) {
                    return parseWithFactor(normalized, KILO_FACTOR)
                }
                if (normalized.endsWith("m")) {
                    return parseWithFactor(normalized, MEGA_FACTOR)
                }
                if (normalized.endsWith("g")) {
                    return parseWithFactor(normalized, GIGA_FACTOR)
                }
                if (normalized.endsWith("t")) {
                    return parseWithFactor(normalized, TERA_FACTOR)
                }
                return normalized.toLong()
            } catch (ex: NumberFormatException) {
                throw IllegalArgumentException("Cannot parse memory amount notation: " + notation, ex)
            }
        }

        private fun parseWithFactor(notation: String, factor: Long): Long {
            return notation.substring(0, notation.length - 1).toLong() * factor
        }
    }
}
