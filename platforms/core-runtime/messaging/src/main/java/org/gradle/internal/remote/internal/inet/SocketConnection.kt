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
package org.gradle.internal.remote.internal.inet

import com.google.common.base.Objects
import org.gradle.internal.UncheckedException
import org.gradle.internal.concurrent.CompositeStoppable
import org.gradle.internal.io.BufferCaster.cast
import org.gradle.internal.remote.internal.MessageIOException
import org.gradle.internal.remote.internal.MessageSerializer
import org.gradle.internal.remote.internal.RecoverableMessageIOException
import org.gradle.internal.remote.internal.RemoteConnection
import org.gradle.internal.serialize.FlushableEncoder
import org.gradle.internal.serialize.ObjectReader
import org.gradle.internal.serialize.ObjectWriter
import org.gradle.internal.serialize.StatefulSerializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.ObjectStreamException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.ClosedSelectorException
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import kotlin.math.min

class SocketConnection<T>(private val socket: SocketChannel, streamSerializer: MessageSerializer, messageSerializer: StatefulSerializer<T?>) : RemoteConnection<T?> {
    private val localAddress: SocketInetAddress
    private val remoteAddress: SocketInetAddress
    private val objectWriter: ObjectWriter<T?>
    private val objectReader: ObjectReader<T?>
    private val instr: InputStream
    private val outstr: OutputStream
    private val encoder: FlushableEncoder

    init {
        try {
            outstr = SocketOutputStream(socket)
            instr = SocketInputStream(socket)
        } catch (e: IOException) {
            throw UncheckedException.throwAsUncheckedException(e)
        }
        val localSocketAddress = socket.socket().getLocalSocketAddress() as InetSocketAddress
        localAddress = SocketInetAddress(localSocketAddress.getAddress(), localSocketAddress.getPort())
        val remoteSocketAddress = socket.socket().getRemoteSocketAddress() as InetSocketAddress
        remoteAddress = SocketInetAddress(remoteSocketAddress.getAddress(), remoteSocketAddress.getPort())
        objectReader = messageSerializer.newReader(streamSerializer.newDecoder(instr))
        encoder = streamSerializer.newEncoder(outstr)
        objectWriter = messageSerializer.newWriter(encoder)
    }

    override fun toString(): String {
        return "socket connection from " + localAddress + " to " + remoteAddress
    }

    @Throws(MessageIOException::class)
    override fun receive(): T? {
        try {
            return objectReader.read()
        } catch (e: EOFException) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Discarding EOFException: {}", e.toString())
            }
            return null
        } catch (e: ObjectStreamException) {
            throw RecoverableMessageIOException(String.format("Could not read message from '%s'.", remoteAddress), e)
        } catch (e: ClassNotFoundException) {
            throw RecoverableMessageIOException(String.format("Could not read message from '%s'.", remoteAddress), e)
        } catch (e: IOException) {
            throw RecoverableMessageIOException(String.format("Could not read message from '%s'.", remoteAddress), e)
        } catch (e: Throwable) {
            throw MessageIOException(String.format("Could not read message from '%s'.", remoteAddress), e)
        }
    }

    @Throws(MessageIOException::class)
    override fun dispatch(message: T?) {
        try {
            objectWriter.write(message)
        } catch (e: ObjectStreamException) {
            throw RecoverableMessageIOException(String.format("Could not write message %s to '%s'.", message, remoteAddress), e)
        } catch (e: ClassNotFoundException) {
            throw RecoverableMessageIOException(String.format("Could not write message %s to '%s'.", message, remoteAddress), e)
        } catch (e: IOException) {
            throw RecoverableMessageIOException(String.format("Could not write message %s to '%s'.", message, remoteAddress), e)
        } catch (e: Throwable) {
            throw MessageIOException(String.format("Could not write message %s to '%s'.", message, remoteAddress), e)
        }
    }

    @Throws(MessageIOException::class)
    override fun flush() {
        try {
            encoder.flush()
            outstr.flush()
        } catch (e: Throwable) {
            throw MessageIOException(String.format("Could not write '%s'.", remoteAddress), e)
        }
    }

    override fun stop() {
        CompositeStoppable.stoppable(object : Closeable {
            @Throws(IOException::class)
            override fun close() {
                flush()
            }
        }, instr, outstr, socket).stop()
    }

    private class SocketInputStream(private val socket: SocketChannel) : InputStream() {
        private val selector: Selector
        private val buffer: ByteBuffer
        private val readBuffer = ByteArray(1)

        init {
            selector = Selector.open()
            socket.register(selector, SelectionKey.OP_READ)
            buffer = ByteBuffer.allocateDirect(4096)
            cast<ByteBuffer?>(buffer).limit(0)
        }

        @Throws(IOException::class)
        override fun read(): Int {
            val nread = read(readBuffer, 0, 1)
            if (nread <= 0) {
                return nread
            }
            return readBuffer[0].toInt()
        }

        @Throws(IOException::class)
        override fun read(dest: ByteArray, offset: Int, max: Int): Int {
            if (max == 0) {
                return 0
            }

            if (buffer.remaining() == 0) {
                try {
                    selector.select()
                } catch (e: ClosedSelectorException) {
                    return -1
                }
                if (!selector.isOpen()) {
                    return -1
                }

                cast<ByteBuffer?>(buffer).clear()
                val nread: Int
                try {
                    nread = socket.read(buffer)
                } catch (e: IOException) {
                    if (isEndOfStream(e)) {
                        cast<ByteBuffer?>(buffer).position(0)
                        cast<ByteBuffer?>(buffer).limit(0)
                        return -1
                    }
                    throw e
                }
                cast<ByteBuffer?>(buffer).flip()

                if (nread < 0) {
                    return -1
                }
            }

            val count = min(buffer.remaining(), max)
            buffer.get(dest, offset, count)
            return count
        }

        @Throws(IOException::class)
        override fun close() {
            selector.close()
        }
    }

    private class SocketOutputStream(private val socket: SocketChannel) : OutputStream() {
        private var selector: Selector? = null
        private val buffer: ByteBuffer
        private val writeBuffer = ByteArray(1)

        init {
            buffer = ByteBuffer.allocateDirect(32 * 1024)
        }

        @Throws(IOException::class)
        override fun write(b: Int) {
            writeBuffer[0] = b.toByte()
            write(writeBuffer)
        }

        @Throws(IOException::class)
        override fun write(src: ByteArray, offset: Int, max: Int) {
            var remaining = max
            var currentPos = offset
            while (remaining > 0) {
                val count = min(remaining, buffer.remaining())
                if (count > 0) {
                    buffer.put(src, currentPos, count)
                    remaining -= count
                    currentPos += count
                }
                while (buffer.remaining() == 0) {
                    writeBufferToChannel()
                }
            }
        }

        @Throws(IOException::class)
        override fun flush() {
            while (buffer.position() > 0) {
                writeBufferToChannel()
            }
        }

        @Throws(IOException::class)
        fun writeBufferToChannel() {
            cast<ByteBuffer?>(buffer).flip()
            val count = writeWithNonBlockingRetry()
            if (count == 0) {
                // buffer was still full after non-blocking retries, now block
                waitForWriteBufferToDrain()
            }
            buffer.compact()
        }

        @Throws(IOException::class)
        fun writeWithNonBlockingRetry(): Int {
            var count = 0
            var retryCount = 0
            while (count == 0 && retryCount++ < RETRIES_WHEN_BUFFER_FULL) {
                count = socket.write(buffer)
                if (count < 0) {
                    throw EOFException()
                } else if (count == 0) {
                    // buffer was full, just call Thread.yield
                    Thread.yield()
                }
            }
            return count
        }

        @Throws(IOException::class)
        fun waitForWriteBufferToDrain() {
            if (selector == null) {
                selector = Selector.open()
            }
            val key = socket.register(selector, SelectionKey.OP_WRITE)
            // block until ready for write operations
            selector!!.select()
            // cancel OP_WRITE selection
            key.cancel()
            // complete cancelling key
            selector!!.selectNow()
        }

        @Throws(IOException::class)
        override fun close() {
            if (selector != null) {
                selector!!.close()
                selector = null
            }
        }

        companion object {
            private const val RETRIES_WHEN_BUFFER_FULL = 2
        }
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(SocketConnection::class.java)
        private fun isEndOfStream(e: Exception): Boolean {
            if (e is EOFException) {
                return true
            }
            if (e is IOException) {
                if (Objects.equal(e.message, "An existing connection was forcibly closed by the remote host")) {
                    return true
                }
                if (Objects.equal(e.message, "An established connection was aborted by the software in your host machine")) {
                    return true
                }
                if (Objects.equal(e.message, "Connection reset by peer")) {
                    return true
                }
                if (Objects.equal(e.message, "Connection reset")) {
                    return true
                }
            }
            return false
        }
    }
}
