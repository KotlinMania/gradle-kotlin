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
import java.io.OutputStream
import java.io.RandomAccessFile

/**
 * Writes to a [RandomAccessFile]. Each operation writes to and advances the current position of the file.
 *
 *
 * Closing this stream does not close the underlying file. Flushing this stream does nothing.
 */
// TODO Replace with Channels.newOutputStream(SeekableByteChannel)
class RandomAccessFileOutputStream(private val file: RandomAccessFile) : OutputStream() {
    @Throws(IOException::class)
    override fun write(i: Int) {
        file.write(i)
    }

    @Throws(IOException::class)
    override fun write(bytes: ByteArray) {
        file.write(bytes)
    }

    @Throws(IOException::class)
    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        file.write(bytes, offset, length)
    }
}
