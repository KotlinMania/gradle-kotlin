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
package org.gradle.internal.file.impl

import com.google.common.annotations.VisibleForTesting
import org.gradle.internal.file.Deleter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.ArrayDeque
import java.util.Deque
import java.util.function.LongSupplier
import java.util.function.Predicate

open class DefaultDeleter(private val timeProvider: LongSupplier, private val isSymlink: Predicate<in File>, private val runGcOnFailedDelete: Boolean) : Deleter {
    @Throws(IOException::class)
    override fun deleteRecursively(target: File): Boolean {
        return deleteRecursively(target, false)
    }

    @Throws(IOException::class)
    override fun deleteRecursively(root: File, followSymlinks: Boolean): Boolean {
        if (root.exists()) {
            return deleteRecursively(
                root, if (followSymlinks)
                    Handling.FOLLOW_SYMLINKED_DIRECTORIES
                else
                    Handling.DO_NOT_FOLLOW_SYMLINKS
            )
        } else {
            return false
        }
    }

    @Throws(IOException::class)
    override fun ensureEmptyDirectory(target: File): Boolean {
        return ensureEmptyDirectory(target, false)
    }

    @Throws(IOException::class)
    override fun ensureEmptyDirectory(root: File, followSymlinks: Boolean): Boolean {
        if (root.exists()) {
            if (root.isDirectory()
                && (followSymlinks || !isSymlink.test(root))
            ) {
                return deleteRecursively(
                    root, if (followSymlinks)
                        Handling.KEEP_AND_FOLLOW_SYMLINKED_DIRECTORIES
                    else
                        Handling.KEEP_AND_DO_NOT_FOLLOW_CHILD_SYMLINKS
                )
            }
            tryHardToDeleteOrThrow(root)
        }
        if (!root.mkdirs()) {
            throw IOException("Couldn't create directory: " + root)
        }
        return true
    }

    @Throws(IOException::class)
    override fun delete(target: File): Boolean {
        if (!target.exists()) {
            return false
        }
        tryHardToDeleteOrThrow(target)
        return true
    }

    @Throws(IOException::class)
    private fun deleteRecursively(root: File, handling: Handling): Boolean {
        LOGGER.debug("Deleting {}", root)
        val startTime = timeProvider.getAsLong()
        val failedPaths: MutableMap<String, FileDeletionResult> = LinkedHashMap<String, FileDeletionResult>()
        val attemptedToRemoveAnything = deleteRecursively(startTime, root, root, handling, failedPaths)
        if (!failedPaths.isEmpty()) {
            throwWithHelpMessage(startTime, root, handling, failedPaths, false)
        }
        return attemptedToRemoveAnything
    }

    @Throws(IOException::class)
    private fun deleteRecursively(startTime: Long, baseDir: File, file: File, handling: Handling, failedPaths: MutableMap<String, FileDeletionResult>): Boolean {
        if (shouldRemoveContentsOf(file, handling)) {
            val contents = file.listFiles()

            // Something else may have removed it
            if (contents == null) {
                return false
            }

            var attemptedToDeleteAnything = false
            for (item in contents) {
                deleteRecursively(startTime, baseDir, item, handling.descendantHandling!!, failedPaths)
                attemptedToDeleteAnything = true
            }

            if (handling.shouldKeepEntry()) {
                return attemptedToDeleteAnything
            }
        }

        val result = tryHardToDelete(file)
        if (!result.isSuccessful) {
            failedPaths.put(file.getAbsolutePath(), result)

            // Fail fast
            if (failedPaths.size == MAX_REPORTED_PATHS) {
                throwWithHelpMessage(startTime, baseDir, handling, failedPaths, true)
            }
        }
        return true
    }

    private fun shouldRemoveContentsOf(file: File, handling: Handling): Boolean {
        return file.isDirectory() && (handling.shouldFollowLinkedDirectory() || !isSymlink.test(file))
    }

    @Throws(IOException::class)
    private fun tryHardToDeleteOrThrow(file: File) {
        val result = tryHardToDelete(file)
        if (!result.isSuccessful) {
            throw IOException("Couldn't delete " + file, result.exception)
        }
    }

    private fun tryHardToDelete(file: File): FileDeletionResult {
        var lastResult = deleteFile(file)
        if (lastResult.isSuccessful) {
            return lastResult
        }

        // This is copied from Ant (see org.apache.tools.ant.util.FileUtils.tryHardToDelete).
        // This was introduced in Ant by https://github.com/apache/ant/commit/ececc5c3e332b97f962b94a475408606433ee0e6
        // This is a workaround for https://bz.apache.org/bugzilla/show_bug.cgi?id=45786
        if (runGcOnFailedDelete) {
            System.gc()
        }

        var failedAttempts = 1
        while (failedAttempts < EMPTY_DIRECTORY_DELETION_ATTEMPTS) {
            try {
                Thread.sleep(DELETE_RETRY_SLEEP_MILLIS.toLong())
            } catch (ex: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            lastResult = deleteFile(file)
            if (lastResult.isSuccessful) {
                return lastResult
            } else {
                failedAttempts++
            }
        }
        return lastResult
    }

    protected open fun deleteFile(file: File): FileDeletionResult {
        try {
            return FileDeletionResult.Companion.withoutException(Files.deleteIfExists(file.toPath()))
        } catch (original: IOException) {
            // Let's try again after making it writable, as this is needed on Windows in some cases
            if (file.setWritable(true)) {
                try {
                    return FileDeletionResult.Companion.withoutException(Files.deleteIfExists(file.toPath()))
                } catch (ignored: IOException) {
                    // Ignored, will use the original exception
                }
            }
            return FileDeletionResult.Companion.withException(original)
        }
    }

    protected class FileDeletionResult private constructor(val isSuccessful: Boolean, val exception: Exception?) {
        companion object {
            @JvmStatic
            fun withoutException(isSuccessful: Boolean): FileDeletionResult {
                return FileDeletionResult(isSuccessful, null)
            }

            @JvmStatic
            fun withException(exception: Exception): FileDeletionResult {
                return FileDeletionResult(false, exception)
            }
        }
    }

    @Throws(IOException::class)
    private fun throwWithHelpMessage(startTime: Long, file: File, handling: Handling, failedPaths: MutableMap<String, FileDeletionResult>, more: Boolean) {
        val ex = IOException(buildHelpMessageForFailedDelete(startTime, file, handling, failedPaths.keys, more))
        for (result in failedPaths.values) {
            if (result.exception != null) {
                ex.addSuppressed(result.exception)
            }
        }
        throw ex
    }

    private fun buildHelpMessageForFailedDelete(startTime: Long, file: File, handling: Handling, failedPaths: MutableCollection<String>, more: Boolean): String {
        val help = StringBuilder("Unable to delete ")
        if (isSymlink.test(file)) {
            help.append("symlink to ")
        }
        if (file.isDirectory()) {
            help.append("directory ")
        } else {
            help.append("file ")
        }
        help.append('\'').append(file).append('\'')

        if (shouldRemoveContentsOf(file, handling)) {
            val absolutePath = file.getAbsolutePath()
            failedPaths.remove(absolutePath)
            if (!failedPaths.isEmpty()) {
                help.append("\n  ").append(HELP_FAILED_DELETE_CHILDREN)
                for (failed in failedPaths) {
                    help.append("\n  - ").append(failed)
                }
                if (more) {
                    help.append("\n  - and more ...")
                }
            }

            val newPaths: MutableCollection<String> = listNewPaths(startTime, file, failedPaths)
            if (!newPaths.isEmpty()) {
                help.append("\n  ").append(HELP_NEW_CHILDREN)
                for (newPath in newPaths) {
                    help.append("\n  - ").append(newPath)
                }
                if (newPaths.size == MAX_REPORTED_PATHS) {
                    help.append("\n  - and more ...")
                }
            }
        }
        return help.toString()
    }

    private enum class Handling(private val shouldKeepEntry: Boolean, private val shouldFollowLinkedDirectory: Boolean) {
        KEEP_AND_FOLLOW_SYMLINKED_DIRECTORIES(true, true) {
            override val descendantHandling: Handling
                get() = Handling.FOLLOW_SYMLINKED_DIRECTORIES
        },
        KEEP_AND_DO_NOT_FOLLOW_CHILD_SYMLINKS(true, true) {
            override val descendantHandling: Handling
                get() = Handling.DO_NOT_FOLLOW_SYMLINKS
        },
        FOLLOW_SYMLINKED_DIRECTORIES(false, true) {
            override val descendantHandling: Handling
                get() = Handling.FOLLOW_SYMLINKED_DIRECTORIES
        },
        DO_NOT_FOLLOW_SYMLINKS(false, false) {
            override val descendantHandling: Handling
                get() = Handling.DO_NOT_FOLLOW_SYMLINKS
        };

        /**
         * Whether or not the entry with this handling should be kept or deleted.
         */
        fun shouldKeepEntry(): Boolean {
            return shouldKeepEntry
        }

        /**
         * Whether or not this entry should be followed if it is a symlinked directory.
         */
        fun shouldFollowLinkedDirectory(): Boolean {
            return shouldFollowLinkedDirectory
        }

        /**
         * How to handle descendants.
         */
        abstract val descendantHandling: Handling
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(DefaultDeleter::class.java)

        private const val DELETE_RETRY_SLEEP_MILLIS = 10

        @VisibleForTesting
        const val MAX_REPORTED_PATHS: Int = 16

        @VisibleForTesting
        const val EMPTY_DIRECTORY_DELETION_ATTEMPTS: Int = 10

        @VisibleForTesting
        const val HELP_FAILED_DELETE_CHILDREN: String = "Failed to delete some children. This might happen because a process has files open or has its working directory set in the target directory."

        @VisibleForTesting
        const val HELP_NEW_CHILDREN: String = "New files were found. This might happen because a process is still writing to the target directory."

        private fun listNewPaths(startTime: Long, directory: File, failedPaths: MutableCollection<String>): MutableCollection<String> {
            val paths: MutableList<String> = ArrayList<String>(MAX_REPORTED_PATHS)
            val stack: Deque<File> = ArrayDeque<File>()
            stack.push(directory)
            while (!stack.isEmpty() && paths.size < MAX_REPORTED_PATHS) {
                val current = stack.pop()
                val absolutePath = current.getAbsolutePath()
                if ((current != directory) && !failedPaths.contains(absolutePath) && current.lastModified() >= startTime) {
                    paths.add(absolutePath)
                }
                if (current.isDirectory()) {
                    val children = current.listFiles()
                    if (children != null) {
                        for (child in children) {
                            stack.push(child)
                        }
                    }
                }
            }
            return paths
        }
    }
}
