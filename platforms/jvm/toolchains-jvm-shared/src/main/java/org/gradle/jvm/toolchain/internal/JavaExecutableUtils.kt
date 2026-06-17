/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.jvm.toolchain.internal

import org.gradle.api.InvalidUserDataException
import org.gradle.internal.deprecation.DeprecationLogger.deprecateBehaviour
import java.io.File
import java.io.IOException

/**
 * Utility class for resolving and validating string paths pointing to
 * Java executables.
 */
object JavaExecutableUtils {
    fun resolveExecutable(executable: String): File {
        val executableFile = File(executable)
        if (!executableFile.isAbsolute()) {
            deprecateBehaviour("Configuring a Java executable via a relative path.")
                .withContext("Resolving relative file paths might yield unexpected results, there is no single clear location it would make sense to resolve against.")!!
                .withAdvice("Configure an absolute path to a Java executable instead.")!!
                .willBecomeAnErrorInGradle10()
                .withUpgradeGuideSection(8, "no_relative_paths_for_java_executables")!!
                .nagUser()
        }
        val executableAbsoluteFile = executableFile.getAbsoluteFile()
        if (!executableAbsoluteFile.exists()) {
            throw InvalidUserDataException("The configured executable does not exist (" + executableFile.getAbsolutePath() + ")")
        }
        if (executableAbsoluteFile.isDirectory()) {
            throw InvalidUserDataException("The configured executable is a directory (" + executableFile.getAbsolutePath() + ")")
        }
        return executableAbsoluteFile
    }

    fun resolveJavaHomeOfExecutable(executable: String): File? {
        // Relying on the layout of the toolchain distribution: <JAVA HOME>/bin/<executable>
        return resolveExecutable(executable).getParentFile().getParentFile()
    }


    @JvmStatic
    fun validateExecutable(executable: String?, executableDescription: String?, referenceFile: File, referenceDescription: String?) {
        if (executable == null) {
            return
        }

        val executableFile = resolveExecutable(executable)
        validateMatchingFiles(executableFile, executableDescription, referenceFile, referenceDescription)
    }

    fun validateMatchingFiles(customFile: File, customDescription: String?, referenceFile: File, referenceDescription: String?) {
        if (customFile == referenceFile) {
            return
        }

        val canonicalCustomFile = canonicalFile(customFile)
        val canonicalReferenceFile = canonicalFile(referenceFile)
        if (canonicalCustomFile == canonicalReferenceFile) {
            return
        }

        throw IllegalStateException(customDescription + " does not match " + referenceDescription + ".")
    }

    private fun canonicalFile(file: File): File {
        try {
            return file.getCanonicalFile()
        } catch (e: IOException) {
            throw RuntimeException("Can't resolve canonical path of file " + file, e)
        }
    }
}
