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
package org.gradle.internal.file.nio

import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * Similar to [java.nio.channels.Channels.newInputStream], but independently tracks the position. This allows multiple threads to read from different
 * positions in the same channel, without interfering with each other.
 */
class PositionTrackingFileChannelInputStream(private val channel: FileChannel, private var position: Long) : InputStream() {
    @Throws(IOException::class)
    override fun read(): Int {
        val b = ByteArray(1)
        val read = read(b, 0, 1)
        return if (read == -1) -1 else b[0].toInt() and 0xff
    }

    @Throws(IOException::class)
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val read = channel.read(ByteBuffer.wrap(b, off, len), position)
        if (read > 0) {
            position += read.toLong()
        }
        return read
    }
}
