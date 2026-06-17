/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.util.internal

import org.gradle.api.Action
import org.gradle.internal.UncheckedException
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.min

/**
 * An `InputStream` which reads from the source `InputStream`. In addition, when the `InputStream` is
 * closed, all threads blocked reading from the stream will receive an end-of-stream.
 */
class DisconnectableInputStream @JvmOverloads internal constructor(source: InputStream, executer: Action<Runnable?>, bufferLength: Int = 1024) : BulkReadInputStream() {
    private val lock: Lock = ReentrantLock()
    private val condition: Condition = lock.newCondition()
    private val buffer: ByteArray
    private var readPos = 0
    private var writePos = 0
    private var closed = false
    private var inputFinished = false

    /*
        The song and dance with Action<Runnable> is to ease testing.
        See DisconnectableInputStreamTest
     */
    internal class ThreadExecuter : Action<Runnable?> {
        override fun execute(runnable: Runnable?) {
            val thread = Thread(runnable)
            thread.setName("DisconnectableInputStream source reader")
            thread.setDaemon(true)
            thread.start()
        }
    }

    @JvmOverloads
    constructor(source: InputStream, bufferLength: Int = 1024) : this(source, ThreadExecuter(), bufferLength)

    init {
        buffer = ByteArray(bufferLength)
        val consume: Runnable = object : Runnable {
            override fun run() {
                try {
                    while (true) {
                        var pos: Int
                        lock.lock()
                        try {
                            while (!closed && writePos == buffer.size && writePos != readPos) {
                                // buffer is full, wait until it has been read
                                condition.await()
                            }
                            assert(writePos >= readPos)
                            if (closed) {
                                // stream has been closed, don't bother reading anything else
                                inputFinished = true
                                condition.signalAll()
                                return
                            }
                            if (readPos == writePos) {
                                // buffer has been fully read, start at the beginning
                                readPos = 0
                                writePos = 0
                            }
                            pos = writePos
                        } finally {
                            lock.unlock()
                        }

                        val nread = source.read(buffer, pos, buffer.size - pos)

                        lock.lock()
                        try {
                            if (nread > 0) {
                                // Have read some data - let readers know
                                assert(writePos >= readPos)
                                writePos += nread
                                assert(buffer.size >= writePos)
                                condition.signalAll()
                            }
                            if (nread < 0) {
                                // End of the stream
                                inputFinished = true
                                condition.signalAll()
                                return
                            }
                        } finally {
                            lock.unlock()
                        }
                    }
                } catch (throwable: Throwable) {
                    lock.lock()
                    try {
                        inputFinished = true
                        condition.signalAll()
                    } finally {
                        lock.unlock()
                    }
                    throw UncheckedException.throwAsUncheckedException(throwable)
                }
            }
        }

        executer.execute(consume)
    }

    @Throws(IOException::class)
    override fun read(bytes: ByteArray, pos: Int, count: Int): Int {
        lock.lock()
        try {
            while (!inputFinished && !closed && readPos == writePos) {
                condition.await()
            }
            if (closed) {
                return -1
            }

            // Drain the buffer before returning end-of-stream
            if (writePos > readPos) {
                val nread = min(count, writePos - readPos)
                System.arraycopy(buffer, readPos, bytes, pos, nread)
                readPos += nread
                assert(writePos >= readPos)
                condition.signalAll()
                return nread
            }

            assert(inputFinished)
            return -1
        } catch (e: InterruptedException) {
            throw UncheckedException.throwAsUncheckedException(e)
        } finally {
            lock.unlock()
        }
    }

    /**
     * Closes this `InputStream` for reading. Any threads blocked in read() will receive an end-of-stream. Also requests that the
     * reader thread stop reading, if possible, but does not block waiting for this to happen.
     *
     *
     * NOTE: this method does not close the source input stream.
     */
    @Throws(IOException::class)
    override fun close() {
        lock.lock()
        try {
            closed = true
            condition.signalAll()
        } finally {
            lock.unlock()
        }
    }
}
