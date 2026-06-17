/*
 * Copyright 2014 the original author or authors.
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

import java.io.File

/**
 * A file system accessible to Gradle.
 */
interface FileSystem : Chmod, Stat {
    /**
     * Tells whether the file system is case sensitive.
     *
     * @return `true` if the file system is case sensitive, `false` otherwise
     */
    @JvmField
    val isCaseSensitive: Boolean

    /**
     * Tells if the file system can create symbolic links. If the answer cannot be determined accurately,
     * `false` is returned.
     *
     * @return `true` if the file system can create symbolic links, `false` otherwise
     */
    fun canCreateSymbolicLink(): Boolean

    /**
     * Creates a symbolic link to a target file.
     *
     * @param link the link to be created
     * @param target the file to link to
     * @exception FileException if the operation fails
     */
    @Throws(FileException::class)
    fun createSymbolicLink(link: File, target: File)

    /**
     * Tells if the file is a symlink
     *
     * @param suspect the file to check
     * @return true if symlink, false otherwise
     */
    fun isSymlink(suspect: File): Boolean

    companion object {
        /**
         * Default Unix permissions for directories, `755`.
         */
        const val DEFAULT_DIR_MODE: Int = 493

        /**
         * Default Unix permissions for files, `644`.
         */
        const val DEFAULT_FILE_MODE: Int = 420
    }
}
