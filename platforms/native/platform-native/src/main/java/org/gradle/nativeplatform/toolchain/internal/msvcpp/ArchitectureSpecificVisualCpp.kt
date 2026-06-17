/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.nativeplatform.toolchain.internal.msvcpp

import org.gradle.util.internal.VersionNumber
import java.io.File

class ArchitectureSpecificVisualCpp internal constructor(
    val implementationVersion: VersionNumber?,
    val path: MutableList<File?>?,
    val binDir: File,
    private val libDir: File,
    private val compilerPath: File,
    private val includeDir: File?,
    private val assemblerFilename: String,
    val preprocessorMacros: MutableMap<String?, String?>?
) : VisualCpp {
    val compilerExecutable: File
        get() = File(binDir, COMPILER_FILENAME)

    val linkerExecutable: File
        get() = File(binDir, LINKER_FILENAME)

    val archiverExecutable: File
        get() = File(binDir, ARCHIVER_FILENAME)

    val assemblerExecutable: File
        get() = File(binDir, assemblerFilename)

    val libDirs: MutableList<File?>
        get() = mutableListOf<File?>(libDir)

    val includeDirs: MutableList<File?>
        get() = mutableListOf<File?>(includeDir)

    val isInstalled: Boolean
        get() = binDir.exists() && compilerPath.exists() && libDir.exists()

    companion object {
        private const val COMPILER_FILENAME = "cl.exe"
        private const val LINKER_FILENAME = "link.exe"
        private const val ARCHIVER_FILENAME = "lib.exe"
    }
}
