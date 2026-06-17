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
package org.gradle.internal.stream

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Provides Input/OutputStream implementations that are able to encode/decode using a simple algorithm (byte&lt;-&gt;2 digit hex string(2 bytes)).
 * Useful when streams are interpreted a text streams as it happens on IBM java for standard input.
 */
object EncodedStream {
    private val HEX_DIGIT = charArrayOf(
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
    )

    class EncodedInput(private val delegate: InputStream) : InputStream() {
        @Throws(IOException::class)
        override fun read(): Int {
            val byte1 = delegate.read()
            if (byte1 < 0) {
                return -1
            }
            val byte2 = delegate.read()
            if (byte2 < 0) {
                throw IOException("Unable to decode, expected 2 bytes but received only 1 byte. It seems the stream was not encoded correctly.")
            }
            return (hexToByte(byte1) shl 4) or hexToByte(byte2)
        }

        companion object {
            @Throws(IOException::class)
            fun hexToByte(s: Int): Int {
                if (s >= '0'.code && s <= '9'.code) {
                    return s - '0'.code
                }
                if (s >= 'a'.code && s <= 'f'.code) {
                    return s - 'a'.code + 10
                }
                throw IOException(String.format("Unexpected value %s received. It seems the stream was not encoded correctly.", s))
            }
        }
    }

    class EncodedOutput(private val delegate: OutputStream) : OutputStream() {
        @Throws(IOException::class)
        override fun write(b: Int) {
            delegate.write(HEX_DIGIT[(b shr 4) and 0x0f].code)
            delegate.write(HEX_DIGIT[b and 0x0f].code)
        }

        @Throws(IOException::class)
        override fun flush() {
            delegate.flush()
        }

        @Throws(IOException::class)
        override fun close() {
            delegate.close()
        }
    }
}
