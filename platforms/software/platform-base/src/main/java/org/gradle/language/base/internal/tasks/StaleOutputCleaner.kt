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
package org.gradle.language.base.internal.tasks

import com.google.common.collect.ImmutableSet
import org.gradle.internal.execution.history.OutputsCleaner
import org.gradle.internal.file.Deleter
import org.gradle.internal.file.FileType
import java.io.File
import java.io.IOException
import java.io.UncheckedIOException
import java.util.function.Predicate
import java.util.stream.Collectors
import javax.annotation.CheckReturnValue

object StaleOutputCleaner {
    /**
     * Clean up the given stale output files under the given directory.
     *
     * Any files and directories are removed that are descendants of `directoryToClean`.
     * Files and directories outside `directoryToClean` and `directoryToClean` itself is not deleted.
     *
     * Returns {code true} if any file or directory was deleted, `false` otherwise.
     */
    @CheckReturnValue
    fun cleanOutputs(deleter: Deleter, filesToDelete: Iterable<File>, directoryToClean: File): Boolean {
        return cleanOutputs(deleter, filesToDelete, ImmutableSet.of(directoryToClean))
    }

    /**
     * Clean up the given stale output files under the given directories.
     *
     * Any files and directories are removed that are descendants of any of the `directoriesToClean`.
     * Files and directories outside `directoriesToClean` and `directoriesToClean` themselves are not deleted.
     *
     * Returns {code true} if any file or directory was deleted, `false` otherwise.
     */
    @CheckReturnValue
    fun cleanOutputs(deleter: Deleter, filesToDelete: Iterable<File>, directoriesToClean: ImmutableSet<File>): Boolean {
        val prefixes = directoriesToClean.stream()
            .map<String> { directoryToClean: File -> directoryToClean.getAbsolutePath() + File.separator }
            .collect(Collectors.toSet())

        val outputsCleaner = OutputsCleaner(
            deleter,
            Predicate { file: File ->
                val absolutePath = file.getAbsolutePath()
                prefixes.stream()
                    .anyMatch { prefix: String -> absolutePath.startsWith(prefix) }
            },
            Predicate { dir: File -> !directoriesToClean.contains(dir) }
        )

        try {
            for (f in filesToDelete) {
                if (f.isFile()) {
                    outputsCleaner.cleanupOutput(f, FileType.RegularFile)
                }
            }
            outputsCleaner.cleanupDirectories()
        } catch (e: IOException) {
            throw UncheckedIOException("Failed to clean up stale outputs", e)
        }

        return outputsCleaner.getDidWork()
    }

    @CheckReturnValue
    fun cleanEmptyOutputDirectories(deleter: Deleter, directories: Iterable<File>, directoryToClean: File): Boolean {
        return cleanEmptyOutputDirectories(deleter, directories, ImmutableSet.of(directoryToClean))
    }

    @CheckReturnValue
    fun cleanEmptyOutputDirectories(deleter: Deleter, directories: Iterable<File>, directoriesToClean: MutableCollection<File>): Boolean {
        val outputsCleaner = OutputsCleaner(
            deleter,
            Predicate { file: File -> false },
            Predicate { dir: File -> !directoriesToClean.contains(dir) }
        )

        try {
            for (f in directories) {
                if (f.isDirectory()) {
                    outputsCleaner.cleanupOutput(f, FileType.Directory)
                }
            }
            outputsCleaner.cleanupDirectories()
        } catch (e: IOException) {
            throw UncheckedIOException("Failed to clean up stale outputs", e)
        }

        return outputsCleaner.getDidWork()
    }
}
