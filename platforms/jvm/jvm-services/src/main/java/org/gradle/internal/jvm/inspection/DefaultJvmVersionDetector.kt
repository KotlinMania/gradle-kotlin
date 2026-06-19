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
package org.gradle.internal.jvm.inspection

import org.gradle.api.GradleException
import org.gradle.api.internal.jvm.JavaVersionParser.parseMajorVersion
import org.gradle.internal.jvm.JavaInfo
import org.gradle.jvm.toolchain.internal.InstallationLocation
import org.gradle.process.ProcessExecutionException
import java.io.File
import java.nio.file.NoSuchFileException

class DefaultJvmVersionDetector(private val detector: JvmMetadataDetector) : JvmVersionDetector {
    override fun getJavaVersionMajor(jvm: JavaInfo): Int {
        return getVersionFromJavaHome(jvm.getJavaHome())
    }

    override fun getJavaVersionMajor(javaCommand: String): Int {
        val executable = File(javaCommand)
        val parentFolder = executable.getParentFile()
        if (parentFolder == null || !parentFolder.exists()) {
            val cause: Exception = NoSuchFileException(javaCommand)
            throw ProcessExecutionException("A problem occurred starting process 'command '" + javaCommand + "''", cause)
        }
        return getVersionFromJavaHome(parentFolder.getParentFile())
    }

    private fun getVersionFromJavaHome(javaHome: File): Int {
        val metadata = validate(detector.getMetadata(InstallationLocation.Companion.autoDetected(javaHome, "specific path")))
        return parseMajorVersion(metadata.javaVersion)
    }

    private fun validate(metadata: JvmInstallationMetadata): JvmInstallationMetadata {
        if (metadata.isValidInstallation) {
            return metadata
        }
        throw GradleException("Unable to determine version for JDK located at " + metadata.javaHome + ". Reason: " + metadata.errorMessage, metadata.errorCause)
    }
}
