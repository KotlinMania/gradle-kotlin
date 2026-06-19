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

import org.gradle.internal.UncheckedException
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.LinkedList
import kotlin.math.max
import kotlin.math.min

/**
 * An in-memory buffer that provides OutputStream and InputStream interfaces.
 *
 * This is more efficient than using ByteArrayOutputStream/ByteArrayInputStream
 *
 * Reading the buffer will clear the buffer.
 * This is not thread-safe, it is intended to be used by a single Thread.
 */
// TODO(mlopatkin): there are many violations, but they're confined in this class. We suppress to unblock downstream project's work.
class StreamByteBuffer @JvmOverloads constructor(private var nextChunkSize: Int = DEFAULT_CHUNK_SIZE) {
    private val chunks = LinkedList<StreamByteBufferChunk>()
    private var currentWriteChunk: StreamByteBufferChunk
    private var currentReadChunk: StreamByteBufferChunk? = null
    private val maxChunkSize: Int
    private val output: StreamByteBufferOutputStream
    private val input: StreamByteBufferInputStream
    private var totalBytesUnreadInList = 0

    init {
        this.maxChunkSize = max(nextChunkSize, MAX_CHUNK_SIZE)
        currentWriteChunk = StreamByteBufferChunk(nextChunkSize)
        output = StreamByteBufferOutputStream()
        input = StreamByteBufferInputStream()
    }

    val outputStream: OutputStream
        get() = output

    val inputStream: InputStream
        get() = input

    @Throws(IOException::class)
    fun writeTo(target: OutputStream) {
        while (prepareRead() != -1) {
            currentReadChunk!!.writeTo(target)
        }
    }

    @Throws(IOException::class)
    fun readFrom(inputStream: InputStream, len: Int) {
        var bytesLeft = len
        while (bytesLeft > 0) {
            val spaceLeft = allocateSpace()
            val limit = min(spaceLeft, bytesLeft)
            val readBytes = currentWriteChunk.readFrom(inputStream, limit)
            if (readBytes == -1) {
                throw EOFException("Unexpected EOF")
            }
            bytesLeft -= readBytes
        }
    }

    @Throws(IOException::class)
    fun readFully(inputStream: InputStream) {
        while (true) {
            val len = allocateSpace()
            val readBytes = currentWriteChunk.readFrom(inputStream, len)
            if (readBytes == -1) {
                break
            }
        }
    }

    fun readAsByteArray(): ByteArray {
        val buf = ByteArray(totalBytesUnread())
        input.readImpl(buf, 0, buf.size)
        return buf
    }

    fun readAsListOfByteArrays(): MutableList<ByteArray> {
        val listOfByteArrays: MutableList<ByteArray> = ArrayList<ByteArray>(chunks.size + 1)
        var buf: ByteArray?
        while ((input.readNextBuffer().also { buf = it }) != null) {
            if (buf!!.size > 0) {
                listOfByteArrays.add(buf)
            }
        }
        return listOfByteArrays
    }

    fun readAsString(encoding: String): String {
        val charset = Charset.forName(encoding)
        return readAsString(charset)
    }

    @JvmOverloads
    fun readAsString(charset: Charset = Charset.defaultCharset()): String {
        try {
            return doReadAsString(charset)
        } catch (e: CharacterCodingException) {
            throw UncheckedException.throwAsUncheckedException(e)
        }
    }

    @Throws(CharacterCodingException::class)
    private fun doReadAsString(charset: Charset): String {
        val unreadSize = totalBytesUnread()
        if (unreadSize > 0) {
            return readAsCharBuffer(charset).toString()
        }
        return ""
    }

    @Throws(CharacterCodingException::class)
    private fun readAsCharBuffer(charset: Charset): CharBuffer {
        val decoder = charset.newDecoder().onMalformedInput(
            CodingErrorAction.REPLACE
        ).onUnmappableCharacter(
            CodingErrorAction.REPLACE
        )
        val charbuffer = CharBuffer.allocate(totalBytesUnread())
        var buf: ByteBuffer? = null
        var wasUnderflow = false
        var nextBuf: ByteBuffer? = null
        var needsFlush = false
        while (hasRemaining(nextBuf) || hasRemaining(buf) || prepareRead() != -1) {
            if (hasRemaining(buf)) {
                // handle decoding underflow, multi-byte unicode character at buffer chunk boundary
                check(wasUnderflow) { "Unexpected state. Buffer has remaining bytes without underflow in decoding." }
                if (!hasRemaining(nextBuf) && prepareRead() != -1) {
                    nextBuf = currentReadChunk!!.readToNioBuffer()
                }
                // copy one by one until the underflow has been resolved
                buf = ByteBuffer.allocate(buf!!.remaining() + 1).put(buf)
                buf.put(nextBuf!!.get())
                BufferCaster.cast<ByteBuffer>(buf).flip()
            } else {
                if (hasRemaining(nextBuf)) {
                    buf = nextBuf
                } else if (prepareRead() != -1) {
                    buf = currentReadChunk!!.readToNioBuffer()
                    check(hasRemaining(buf)) { "Unexpected state. Buffer is empty." }
                }
                nextBuf = null
            }
            val endOfInput = !hasRemaining(nextBuf) && prepareRead() == -1
            val bufRemainingBefore = buf!!.remaining()
            var result = decoder.decode(buf, charbuffer, false)
            if (bufRemainingBefore > buf.remaining()) {
                needsFlush = true
            }
            if (endOfInput) {
                result = decoder.decode(ByteBuffer.allocate(0), charbuffer, true)
                if (!result.isUnderflow()) {
                    result.throwException()
                }
                break
            }
            wasUnderflow = result.isUnderflow()
        }
        if (needsFlush) {
            val result = decoder.flush(charbuffer)
            if (!result.isUnderflow()) {
                result.throwException()
            }
        }
        clear()
        // push back remaining bytes of multi-byte unicode character
        while (hasRemaining(buf)) {
            val b = buf!!.get()
            try {
                this.outputStream.write(b.toInt())
            } catch (e: IOException) {
                throw UncheckedException.throwAsUncheckedException(e)
            }
        }
        BufferCaster.cast<CharBuffer>(charbuffer).flip()
        return charbuffer
    }

    private fun hasRemaining(nextBuf: ByteBuffer?): Boolean {
        return nextBuf != null && nextBuf.hasRemaining()
    }

    fun totalBytesUnread(): Int {
        var total = totalBytesUnreadInList
        if (currentReadChunk != null) {
            total += currentReadChunk!!.bytesUnread()
        }
        if (currentWriteChunk !== currentReadChunk && currentWriteChunk != null) {
            total += currentWriteChunk.bytesUnread()
        }
        return total
    }

    protected fun allocateSpace(): Int {
        var spaceLeft = currentWriteChunk.spaceLeft()
        if (spaceLeft == 0) {
            addChunk(currentWriteChunk)
            currentWriteChunk = StreamByteBufferChunk(nextChunkSize)
            if (nextChunkSize < maxChunkSize) {
                nextChunkSize = min(nextChunkSize * 2, maxChunkSize)
            }
            spaceLeft = currentWriteChunk.spaceLeft()
        }
        return spaceLeft
    }

    protected fun prepareRead(): Int {
        var bytesUnread = if (currentReadChunk != null) currentReadChunk!!.bytesUnread() else 0
        if (bytesUnread == 0) {
            if (!chunks.isEmpty()) {
                currentReadChunk = chunks.removeFirst()
                bytesUnread = currentReadChunk!!.bytesUnread()
                totalBytesUnreadInList -= bytesUnread
            } else if (currentReadChunk !== currentWriteChunk) {
                currentReadChunk = currentWriteChunk
                bytesUnread = currentReadChunk!!.bytesUnread()
            } else {
                bytesUnread = -1
            }
        }
        return bytesUnread
    }

    private fun addChunks(listOfByteArrays: MutableList<ByteArray>) {
        for (buf in listOfByteArrays) {
            addChunk(StreamByteBufferChunk(buf))
        }
    }

    private fun addChunk(chunk: StreamByteBufferChunk) {
        chunks.add(chunk)
        totalBytesUnreadInList += chunk.bytesUnread()
    }

    internal class StreamByteBufferChunk {
        private var pointer = 0
        private val buffer: ByteArray
        private val size: Int
        private var used = 0

        constructor(size: Int) {
            this.size = size
            buffer = ByteArray(size)
        }

        constructor(buf: ByteArray) {
            this.size = buf.size
            this.buffer = buf
            this.used = buf.size
        }

    fun readToNioBuffer(): ByteBuffer {
        if (pointer < used) {
            val result: ByteBuffer
            if (pointer > 0 || used < size) {
                result = ByteBuffer.wrap(buffer, pointer, used - pointer)
            } else {
                result = ByteBuffer.wrap(buffer)
            }
            pointer = used
            return result
        }

        return ByteBuffer.allocate(0)
    }

        fun write(b: Byte): Boolean {
            if (used < size) {
                buffer[used++] = b
                return true
            }

            return false
        }

        fun write(b: ByteArray, off: Int, len: Int) {
            System.arraycopy(b, off, buffer, used, len)
            used = used + len
        }

        fun read(b: ByteArray, off: Int, len: Int) {
            System.arraycopy(buffer, pointer, b, off, len)
            pointer = pointer + len
        }

        @Throws(IOException::class)
        fun writeTo(target: OutputStream) {
            if (pointer < used) {
                target.write(buffer, pointer, used - pointer)
                pointer = used
            }
        }

        fun reset() {
            pointer = 0
        }

        fun bytesUsed(): Int {
            return used
        }

        fun bytesUnread(): Int {
            return used - pointer
        }

        fun read(): Int {
            if (pointer < used) {
                return buffer[pointer++].toInt() and 0xff
            }

            return -1
        }

        fun spaceLeft(): Int {
            return size - used
        }

        @Throws(IOException::class)
        fun readFrom(inputStream: InputStream, len: Int): Int {
            val readBytes = inputStream.read(buffer, used, len)
            if (readBytes > 0) {
                used += readBytes
            }
            return readBytes
        }

        fun clear() {
            pointer = 0
            used = pointer
        }

        fun readBuffer(): ByteArray {
            if (used == buffer.size && pointer == 0) {
                pointer = used
                return buffer
            } else if (pointer < used) {
                val buf = ByteArray(used - pointer)
                read(buf, 0, used - pointer)
                return buf
            } else {
                return ByteArray(0)
            }
        }
    }

    internal inner class StreamByteBufferOutputStream : OutputStream() {
        var isClosed: Boolean = false
            private set

        @Throws(IOException::class)
        override fun write(b: ByteArray, off: Int, len: Int) {
            if (b == null) {
                throw NullPointerException()
            }

            if ((off < 0) || (off > b.size) || (len < 0)
                || ((off + len) > b.size) || ((off + len) < 0)
            ) {
                throw IndexOutOfBoundsException()
            }

            if (len == 0) {
                return
            }

            var bytesLeft = len
            var currentOffset = off
            while (bytesLeft > 0) {
                val spaceLeft = allocateSpace()
                val writeBytes = min(spaceLeft, bytesLeft)
                currentWriteChunk.write(b, currentOffset, writeBytes)
                bytesLeft -= writeBytes
                currentOffset += writeBytes
            }
        }

        @Throws(IOException::class)
        override fun close() {
            this.isClosed = true
        }

        @Throws(IOException::class)
        override fun write(b: Int) {
            allocateSpace()
            currentWriteChunk.write(b.toByte())
        }

        val buffer: StreamByteBuffer
            get() = this@StreamByteBuffer
    }

    internal inner class StreamByteBufferInputStream : InputStream() {
        @Throws(IOException::class)
        override fun read(): Int {
            if (prepareRead() == -1) {
                return -1
            }
            return currentReadChunk!!.read()
        }

        @Throws(IOException::class)
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            return readImpl(b, off, len)
        }

        fun readImpl(b: ByteArray, off: Int, len: Int): Int {
            if (b == null) {
                throw NullPointerException()
            }

            if ((off < 0) || (off > b.size) || (len < 0)
                || ((off + len) > b.size) || ((off + len) < 0)
            ) {
                throw IndexOutOfBoundsException()
            }

            if (len == 0) {
                return 0
            }

            var bytesLeft = len
            var currentOffset = off
            var bytesUnread = prepareRead()
            var totalBytesRead = 0
            while (bytesLeft > 0 && bytesUnread != -1) {
                val readBytes = min(bytesUnread, bytesLeft)
                currentReadChunk!!.read(b, currentOffset, readBytes)
                bytesLeft -= readBytes
                currentOffset += readBytes
                totalBytesRead += readBytes
                bytesUnread = prepareRead()
            }
            if (totalBytesRead > 0) {
                return totalBytesRead
            }

            return -1
        }

        @Throws(IOException::class)
        override fun available(): Int {
            return totalBytesUnread()
        }

        val buffer: StreamByteBuffer
            get() = this@StreamByteBuffer

        fun readNextBuffer(): ByteArray? {
            if (prepareRead() != -1) {
                return currentReadChunk!!.readBuffer()
            }
            return null
        }
    }

    fun clear() {
        chunks.clear()
        currentReadChunk = null
        totalBytesUnreadInList = 0
        currentWriteChunk.clear()
    }

    companion object {
        private const val DEFAULT_CHUNK_SIZE = 4096
        private val MAX_CHUNK_SIZE = 1024 * 1024

        @JvmStatic
        @Throws(IOException::class)
        fun of(inputStream: InputStream): StreamByteBuffer {
            val buffer = StreamByteBuffer(chunkSizeInDefaultRange(inputStream.available()))
            buffer.readFully(inputStream)
            return buffer
        }

        @JvmStatic
        @Throws(IOException::class)
        fun of(inputStream: InputStream, len: Int): StreamByteBuffer {
            val buffer = StreamByteBuffer(chunkSizeInDefaultRange(len))
            buffer.readFrom(inputStream, len)
            return buffer
        }

        @JvmStatic
        fun createWithChunkSizeInDefaultRange(value: Int): StreamByteBuffer {
            return StreamByteBuffer(chunkSizeInDefaultRange(value))
        }

        @JvmStatic
        fun chunkSizeInDefaultRange(value: Int): Int {
            return valueInRange(value, DEFAULT_CHUNK_SIZE, MAX_CHUNK_SIZE)
        }

        private fun valueInRange(value: Int, min: Int, max: Int): Int {
            return min(max(value, min), max)
        }

        @JvmStatic
        fun of(listOfByteArrays: MutableList<ByteArray>): StreamByteBuffer {
            val buffer = StreamByteBuffer()
            buffer.addChunks(listOfByteArrays)
            return buffer
        }
    }
}
