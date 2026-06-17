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
package org.gradle.internal.io

import java.io.IOException
import java.io.OutputStream
import java.nio.charset.StandardCharsets

/**
 * An OutputStream which separates bytes written into lines of text. Uses the platform default encoding. Is not thread safe.
 */
class LineBufferingOutputStream @JvmOverloads constructor(private val handler: TextStream, lineSeparator: String, bufferLength: Int = 8192, private val lineMaxLength: Int = LINE_MAX_LENGTH) :
    OutputStream() {
    private var hasBeenClosed = false
    private val buffer: StreamByteBuffer
    private val output: OutputStream
    private val lastLineSeparatorByte: Byte
    private var counter = 0

    init {
        buffer = StreamByteBuffer(bufferLength)
        output = buffer.getOutputStream()
        val lineSeparatorBytes = lineSeparator.toByteArray(StandardCharsets.UTF_8)
        lastLineSeparatorByte = lineSeparatorBytes[lineSeparatorBytes.size - 1]
    }

    /**
     * Closes this output stream and releases any system resources associated with this stream. The general contract of
     * `close` is that it closes the output stream. A closed stream cannot perform output operations and
     * cannot be reopened.
     */
    @Throws(IOException::class)
    override fun close() {
        hasBeenClosed = true
        flush()
        handler.endOfStream(null)
    }

    /**
     * Writes the specified byte to this output stream. The general contract for `write` is that one byte is
     * written to the output stream. The byte to be written is the eight low-order bits of the argument `b`.
     * The 24 high-order bits of `b` are ignored.
     *
     * @param b the `byte` to write
     * @throws IOException if an I/O error occurs. In particular, an `IOException` may be thrown if
     * the output stream has been closed.
     */
    @Throws(IOException::class)
    override fun write(b: Int) {
        if (hasBeenClosed) {
            throw IOException("The stream has been closed.")
        }
        output.write(b)
        counter++
        if (endsWithLineSeparator(b) || counter >= lineMaxLength) {
            flush()
        }
    }

    // only check for the last byte of a multi-byte line separator
    // besides this, always check for '\n'
    // this handles '\r' (MacOSX 9), '\r\n' (Windows) and '\n' (Linux/Unix/MacOSX 10)
    private fun endsWithLineSeparator(b: Int): Boolean {
        val currentByte = (b and 0xff).toByte()
        return currentByte == lastLineSeparatorByte || currentByte == '\n'.code.toByte()
    }

    override fun flush() {
        val text = buffer.readAsString()
        if (text.length > 0) {
            handler.text(text)
        }
        counter = 0
    }

    companion object {
        private val LINE_MAX_LENGTH = 1024 * 1024 // Split line if a single line goes over 1 MB
    }
}
