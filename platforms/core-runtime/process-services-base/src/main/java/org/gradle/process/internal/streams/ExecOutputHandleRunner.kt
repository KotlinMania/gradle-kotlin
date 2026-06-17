/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.process.internal.streams

import org.gradle.api.logging.Logging.getLogger
import org.gradle.internal.concurrent.CompositeStoppable
import org.gradle.internal.operations.BuildOperationRef
import org.gradle.internal.operations.CurrentBuildOperationRef
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.Volatile

class ExecOutputHandleRunner internal constructor(
    private val displayName: String?,
    private val inputStream: InputStream,
    private val outputStream: OutputStream,
    private val bufferSize: Int,
    private val completed: CountDownLatch
) : Runnable {
    @Volatile
    private var closed = false

    @Volatile
    private var associatedBuildOperation: BuildOperationRef? = null

    constructor(displayName: String?, inputStream: InputStream, outputStream: OutputStream, completed: CountDownLatch) : this(displayName, inputStream, outputStream, 8192, completed)

    fun associateBuildOperation(startupRef: BuildOperationRef?) {
        this.associatedBuildOperation = startupRef
    }

    fun clearAssociatedBuildOperation() {
        this.associatedBuildOperation = null
    }

    override fun run() {
        try {
            forwardContent()
        } finally {
            completed.countDown()
        }
    }

    private fun forwardContent() {
        try {
            val buffer = ByteArray(bufferSize)
            while (!closed) {
                val nread = inputStream.read(buffer)
                if (nread < 0) {
                    break
                }
                val startupRef = this.associatedBuildOperation
                if (startupRef != null) {
                    CurrentBuildOperationRef.instance().with<Any?, IOException?>(startupRef, CurrentBuildOperationRef.Callable {
                        writeBuffer(buffer, nread)
                        null
                    })
                } else {
                    writeBuffer(buffer, nread)
                }
            }
            CompositeStoppable.stoppable(inputStream, outputStream).stop()
        } catch (t: Throwable) {
            if (!closed && !wasInterrupted(t)) {
                LOGGER!!.error(String.format("Could not %s.", displayName), t)
            }
        }
    }

    @Throws(IOException::class)
    private fun writeBuffer(buffer: ByteArray, nread: Int) {
        outputStream.write(buffer, 0, nread)
        outputStream.flush()
    }

    /**
     * This can happen e.g. on IBM JDK when a remote process was terminated. Instead of
     * returning -1 on the next read() call, it will interrupt the current read call.
     */
    private fun wasInterrupted(t: Throwable?): Boolean {
        return t is IOException && "Interrupted system call" == t.message
    }

    @Throws(IOException::class)
    fun closeInput() {
        disconnect()
        inputStream.close()
    }

    override fun toString(): String {
        return displayName!!
    }

    fun disconnect() {
        closed = true
    }

    companion object {
        private val LOGGER = getLogger(ExecOutputHandleRunner::class.java)
    }
}
