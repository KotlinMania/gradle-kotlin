/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.security.internal

import org.bouncycastle.openpgp.PGPPublicKey

class Fingerprint private constructor(val bytes: ByteArray) {
    private val hashCode: Int

    init {
        this.hashCode = bytes.contentHashCode()
    }

    override fun toString(): String {
        val sb = StringBuilder(2 * bytes.size)
        for (b in this.bytes) {
            sb.append(HEX_DIGITS[(b.toInt() shr 4) and 0xf]).append(HEX_DIGITS[b.toInt() and 0xf])
        }
        return sb.toString()
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val that = o as Fingerprint

        return bytes.contentEquals(that.bytes)
    }

    override fun hashCode(): Int {
        return hashCode
    }

    companion object {
        private val HEX_DIGITS = "0123456789ABCDEF".toCharArray()

        @JvmStatic
        fun of(key: PGPPublicKey): Fingerprint {
            return Fingerprint(key.getFingerprint())
        }

        @JvmStatic
        fun wrap(fingerprint: ByteArray): Fingerprint {
            return Fingerprint(fingerprint)
        }

        @JvmStatic
        fun fromString(hexString: String): Fingerprint {
            val length = hexString.length
            check(length % 2 != 1) { "Unexpected hex string length: " + length }
            val len = length / 2
            val result = ByteArray(len)
            for (i in 0..<len) {
                val hi: Int = decode(hexString.get(2 * i)) shl 4
                val lo: Int = decode(hexString.get(2 * i + 1))
                result[i] = (hi + lo).toByte()
            }
            return Fingerprint(result)
        }

        private fun decode(ch: Char): Int {
            if (ch >= '0' && ch <= '9') {
                return ch.code - '0'.code
            }
            if (ch >= 'a' && ch <= 'f') {
                return ch.code - 'a'.code + 10
            }
            if (ch >= 'A' && ch <= 'F') {
                return ch.code - 'A'.code + 10
            }
            throw IllegalArgumentException("Illegal hexadecimal character: " + ch)
        }
    }
}
