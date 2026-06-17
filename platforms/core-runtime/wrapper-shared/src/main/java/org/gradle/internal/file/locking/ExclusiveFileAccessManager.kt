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
package org.gradle.internal.file.locking

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.util.concurrent.Callable

class ExclusiveFileAccessManager(private val timeoutMs: Int, private val pollIntervalMs: Int) {
    @Throws(Exception::class)
    fun <T> access(exclusiveFile: File, task: Callable<T?>): T? {
        val lockFile = File(exclusiveFile.getParentFile(), exclusiveFile.getName() + LOCK_FILE_SUFFIX)
        val lockFileDirectory = lockFile.getParentFile()
        if (!lockFileDirectory.mkdirs()
            && (!lockFileDirectory.exists() || !lockFileDirectory.isDirectory())
        ) {
            throw RuntimeException("Could not create parent directory for lock file " + lockFile.getAbsolutePath())
        }
        var randomAccessFile: RandomAccessFile? = null
        var channel: FileChannel? = null
        try {
            val expiry = this.timeMillis + timeoutMs
            var lock: FileLock? = null

            while (lock == null && this.timeMillis < expiry) {
                randomAccessFile = RandomAccessFile(lockFile, "rw")
                channel = randomAccessFile.getChannel()
                lock = channel.tryLock()

                if (lock == null) {
                    maybeCloseQuietly(channel)
                    maybeCloseQuietly(randomAccessFile)
                    Thread.sleep(pollIntervalMs.toLong())
                }
            }

            if (lock == null) {
                throw RuntimeException("Timeout of " + timeoutMs + " reached waiting for exclusive access to file: " + exclusiveFile.getAbsolutePath())
            }

            try {
                return task.call()
            } finally {
                lock.release()

                maybeCloseQuietly(channel)
                channel = null
                maybeCloseQuietly(randomAccessFile)
                randomAccessFile = null
            }
        } finally {
            maybeCloseQuietly(channel)
            maybeCloseQuietly(randomAccessFile)
        }
    }

    private val timeMillis: Long
        get() = System.nanoTime() / (1000L * 1000L)

    companion object {
        const val LOCK_FILE_SUFFIX: String = ".lck"

        private fun maybeCloseQuietly(closeable: Closeable?) {
            if (closeable != null) {
                try {
                    closeable.close()
                } catch (ignore: Exception) {
                    //
                }
            }
        }
    }
}
