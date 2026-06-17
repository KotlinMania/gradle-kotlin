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
package org.gradle.internal.file

import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile

/**
 * Reads from a [RandomAccessFile]. Each operation reads from and advances the current position of the file.
 *
 *
 * Closing this stream does not close the underlying file.
 */
// TODO Replace this with Channels.newInputStream(SeekableByteChannel) or PositionTrackingFileChannelInputStream
class RandomAccessFileInputStream(private val file: RandomAccessFile) : InputStream() {
    @Throws(IOException::class)
    override fun skip(n: Long): Long {
        file.seek(file.getFilePointer() + n)
        return n
    }

    @Throws(IOException::class)
    override fun read(bytes: ByteArray): Int {
        return file.read(bytes)
    }

    @Throws(IOException::class)
    override fun read(): Int {
        return file.read()
    }

    @Throws(IOException::class)
    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        return file.read(bytes, offset, length)
    }
}
