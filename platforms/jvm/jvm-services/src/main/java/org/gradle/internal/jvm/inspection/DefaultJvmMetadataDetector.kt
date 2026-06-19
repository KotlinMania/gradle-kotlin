/*
 * Copyright 2020 the original author or authors.
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

import com.google.common.io.Files
import org.gradle.api.JavaVersion.Companion.toVersion
import org.gradle.api.internal.file.temp.TemporaryFileProvider
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem.Companion.current
import org.gradle.jvm.toolchain.internal.InstallationLocation
import org.gradle.process.ProcessExecutionException
import org.gradle.process.internal.ClientExecHandleBuilderFactory
import org.gradle.util.internal.GFileUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Arrays
import java.util.EnumMap
import javax.inject.Inject

class DefaultJvmMetadataDetector @Inject constructor(
    private val execHandleFactory: ClientExecHandleBuilderFactory,
    private val temporaryFileProvider: TemporaryFileProvider
) : JvmMetadataDetector {
    private val logger: Logger = LoggerFactory.getLogger(DefaultJvmMetadataDetector::class.java)

    override fun getMetadata(javaInstallationLocation: InstallationLocation): JvmInstallationMetadata {
        val javaHome = javaInstallationLocation.location
        if (!javaHome.exists()) {
            return failure(javaHome, "No such directory: " + javaHome)
        }
        if (Jvm.current().getJavaHome() == javaHome) {
            return getMetadataFromCurrentJvm(javaHome)
        }
        return getMetadataFromInstallation(javaHome)
    }

    private fun getMetadataFromCurrentJvm(javaHome: File): JvmInstallationMetadata {
        val result = EnumMap<ProbedSystemProperty, String>(ProbedSystemProperty::class.java)
        for (type in ProbedSystemProperty.entries) {
            if (type != ProbedSystemProperty.Z_ERROR) {
                result[type] = System.getProperty(type.systemPropertyKey)
            }
        }
        logger.info("Received JVM installation metadata from '{}': {}", javaHome.getAbsolutePath(), result)
        return asMetadata(javaHome, result)
    }

    private fun asMetadata(javaHome: File, metadata: EnumMap<ProbedSystemProperty, String>): JvmInstallationMetadata {
        val javaVersion = metadata.get(ProbedSystemProperty.JAVA_VERSION)
        if (javaVersion == null) {
            return failure(javaHome, metadata.get(ProbedSystemProperty.Z_ERROR))
        }
        try {
            toVersion(javaVersion)
        } catch (e: IllegalArgumentException) {
            return failure(javaHome, "Cannot parse version number: " + javaVersion)
        }
        val javaVendor = metadata.get(ProbedSystemProperty.JAVA_VENDOR)
        val runtimeName = metadata.get(ProbedSystemProperty.RUNTIME_NAME)
        val runtimeVersion = metadata.get(ProbedSystemProperty.RUNTIME_VERSION)
        val jvmName = metadata.get(ProbedSystemProperty.VM_NAME)
        val jvmVersion = metadata.get(ProbedSystemProperty.VM_VERSION)
        val jvmVendor = metadata.get(ProbedSystemProperty.VM_VENDOR)
        val architecture = metadata.get(ProbedSystemProperty.OS_ARCH)
        return JvmInstallationMetadata.Companion.from(javaHome, javaVersion, javaVendor, runtimeName, runtimeVersion, jvmName, jvmVersion, jvmVendor, architecture)
    }

    //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
    private fun getMetadataFromInstallation(jdkPath: File): JvmInstallationMetadata {
        val tmpDir = temporaryFileProvider.createTemporaryDirectory("jvm", "probe")
        val probe = writeProbeClass(tmpDir)
        val exec = execHandleFactory.newExecHandleBuilder()
        exec!!.setWorkingDir(probe.getParentFile())
        exec.executable = javaExecutable(jdkPath).getAbsolutePath()
        try {
            val out = ByteArrayOutputStream()
            val errorOutput = ByteArrayOutputStream()
            val mainClassname = Files.getNameWithoutExtension(probe.getName())
            exec.args("-Xmx32m", "-Xms32m", "-cp", ".", mainClassname)
            exec.setStandardOutput(out)
            exec.setErrorOutput(errorOutput)
            val result = exec.build()!!.start()!!.waitForFinish()
            val exitValue = result!!.exitValue
            if (exitValue == 0) {
                return parseExecOutput(jdkPath, out.toString())
            }
            val errorMessage = "Command returned unexpected result code: " + exitValue + "\nError output:\n" + errorOutput
            logger.debug("Failed to get metadata from JVM installation at '{}'. {}", jdkPath, errorMessage)
            return failure(jdkPath, errorMessage)
        } catch (ex: ProcessExecutionException) {
            logger.debug("Failed to get metadata from JVM installation at '{}'.", jdkPath, ex)
            return failure(jdkPath, ex)
        } finally {
            GFileUtils.deleteQuietly(tmpDir)
        }
    }


    private fun parseExecOutput(jdkPath: File, probeResult: String): JvmInstallationMetadata {
        val split = probeResult.lineSequence()
            .filter { line: String -> line.startsWith(MetadataProbe.MARKER_PREFIX) }
            .map { line: String -> line.substring(MetadataProbe.MARKER_PREFIX.length) }
            .toList()
        if (split.size != ProbedSystemProperty.entries.size - 1) { // -1 because of Z_ERROR
            val errorMessage = "Unexpected command output: \n" + probeResult
            logger.info("Failed to parse JVM installation metadata output at '" + jdkPath + "'. " + errorMessage)
            return failure(jdkPath, errorMessage)
        }
        val result = EnumMap<ProbedSystemProperty, String>(ProbedSystemProperty::class.java)
        for (type in ProbedSystemProperty.entries) {
            if (type != ProbedSystemProperty.Z_ERROR) {
                result[type] = split[type.ordinal].trim { it <= ' ' }
            }
        }
        logger.info("Received JVM installation metadata from '{}': {}", jdkPath.getAbsolutePath(), result)
        return asMetadata(jdkPath, result)
    }

    private fun failure(jdkPath: File, errorMessage: String?): JvmInstallationMetadata {
        return JvmInstallationMetadata.Companion.failure(jdkPath, errorMessage)
    }

    private fun failure(jdkPath: File, cause: Exception): JvmInstallationMetadata {
        return JvmInstallationMetadata.Companion.failure(jdkPath, cause)
    }

    private fun writeProbeClass(tmpDir: File): File {
        return MetadataProbe().writeClass(tmpDir)
    }

    companion object {
        private fun javaExecutable(jdkPath: File): File {
            return File(File(jdkPath, "bin"), current()!!.getExecutableName("java"))
        }
    }
}
