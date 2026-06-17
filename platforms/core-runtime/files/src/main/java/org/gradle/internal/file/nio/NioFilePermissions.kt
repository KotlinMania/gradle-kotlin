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
package org.gradle.internal.file.nio

import org.gradle.internal.file.FilePermissionHandler
import org.gradle.internal.file.FileSystem
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.FileSystems

object NioFilePermissions {
    @JvmStatic
    fun createFilePermissionHandler(): FilePermissionHandler {
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            return PosixJdk7FilePermissionHandler()
        } else {
            return object : FilePermissionHandler {
                @Throws(IOException::class)
                override fun getUnixMode(file: File): Int {
                    if (file.isDirectory()) {
                        return FileSystem.DEFAULT_DIR_MODE
                    } else if (file.exists()) {
                        return FileSystem.DEFAULT_FILE_MODE
                    } else {
                        throw FileNotFoundException(String.format("File '%s' not found.", file))
                    }
                }

                @Throws(IOException::class)
                override fun chmod(file: File, mode: Int) {
                    if (!file.exists()) {
                        throw FileNotFoundException(String.format("File '%s' does not exist.", file))
                    }
                }
            }
        }
    }
}
