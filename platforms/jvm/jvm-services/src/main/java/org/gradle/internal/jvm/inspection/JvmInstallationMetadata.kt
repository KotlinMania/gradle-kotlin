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

import org.gradle.api.JavaVersion
import org.gradle.api.JavaVersion.Companion.toVersion
import org.gradle.api.internal.jvm.JavaVersionParser.parseMajorVersion
import org.gradle.internal.os.OperatingSystem.Companion.current
import org.gradle.internal.serialization.Cached
import java.io.File
import java.nio.file.Path
import java.util.EnumSet
import java.util.Objects

interface JvmInstallationMetadata {
    val javaHome: Path

    /**
     * Parsed equivalent of [.getJavaVersion].
     */
    val languageVersion: JavaVersion

    /**
     * The major Java version parsed from [.getJavaVersion].
     */
    val javaMajorVersion: Int

    /**
     * A wrapper around the raw value of the toolchain vendor.
     *
     * @see ProbedSystemProperty.JAVA_VENDOR
     */
    val vendor: JvmVendor

    /**
     * @see ProbedSystemProperty.JAVA_VERSION
     */
    val javaVersion: String

    /**
     * @see ProbedSystemProperty.RUNTIME_NAME
     */
    val runtimeName: String?

    /**
     * @see ProbedSystemProperty.RUNTIME_VERSION
     */
    val runtimeVersion: String?

    /**
     * @see ProbedSystemProperty.VM_NAME
     */
    val jvmName: String?

    /**
     * @see ProbedSystemProperty.VM_VERSION
     */
    val jvmVersion: String?

    /**
     * @see ProbedSystemProperty.VM_VENDOR
     */
    val jvmVendor: String?

    /**
     * @see ProbedSystemProperty.OS_ARCH
     */
    val architecture: String?

    val displayName: String

    val capabilities: MutableSet<JavaInstallationCapability>

    val errorMessage: String?

    val errorCause: Throwable?

    val isValidInstallation: Boolean

    class DefaultJvmInstallationMetadata(
        javaHome: File,
        override val javaVersion: String,
        private val javaVendor: String?,
        override val runtimeName: String?,
        override val runtimeVersion: String?,
        override val jvmName: String?,
        override val jvmVersion: String?,
        override val jvmVendor: String?,
        override val architecture: String?
    ) : JvmInstallationMetadata {
        override val javaHome: Path
        override val languageVersion: JavaVersion
        override val javaMajorVersion: Int

        private val capabilitiesCache = Cached.of({ this.gatherCapabilities() })

        init {
            this.javaHome = javaHome.toPath()
            this.languageVersion = toVersion(javaVersion)!!
            this.javaMajorVersion = parseMajorVersion(javaVersion)
        }

        override val vendor: JvmVendor
            get() = JvmVendor.Companion.fromString(javaVendor)

        override val displayName: String
            get() {
            val vendor = determineVendorName()
                return vendor + " " + determineInstallationType() + " " + javaMajorVersion + " (" + runtimeVersion + ")"
            }

        private fun determineVendorName(): String? {
            val vendor = vendor.knownVendor
            if (vendor == JvmVendor.KnownJvmVendor.ORACLE) {
                if (jvmName != null && jvmName.contains("OpenJDK")) {
                    return "OpenJDK"
                }
            }
            return this.vendor.displayName
        }

        private fun determineInstallationType(): String {
            if (capabilities.containsAll(JavaInstallationCapability.Companion.JDK_CAPABILITIES)) {
                return "JDK"
            } else {
                return "JRE"
            }
        }

        override val capabilities: MutableSet<JavaInstallationCapability>
            get() = capabilitiesCache.get()!!

        private fun gatherCapabilities(): MutableSet<JavaInstallationCapability> {
            val capabilities: MutableSet<JavaInstallationCapability> = EnumSet.noneOf(JavaInstallationCapability::class.java)
            if (getToolByExecutable("javac").exists()) {
                capabilities.add(JavaInstallationCapability.JAVA_COMPILER)
            }
            if (getToolByExecutable("javadoc").exists()) {
                capabilities.add(JavaInstallationCapability.JAVADOC_TOOL)
            }
            if (getToolByExecutable("jar").exists()) {
                capabilities.add(JavaInstallationCapability.JAR_TOOL)
            }
            if (getToolByExecutable("native-image").exists()) {
                capabilities.add(JavaInstallationCapability.NATIVE_IMAGE)
            }
            val isJ9vm = jvmName?.contains("J9") == true
            if (isJ9vm) {
                capabilities.add(JavaInstallationCapability.J9_VIRTUAL_MACHINE)
            }
            return capabilities
        }

        private fun getToolByExecutable(name: String): File {
            return File(File(javaHome.toFile(), "bin"), current()!!.getExecutableName(name))
        }

        override val errorMessage: String?
            get() = throw UnsupportedOperationException()

        override val errorCause: Throwable?
            get() = throw UnsupportedOperationException()

        override val isValidInstallation: Boolean = true

        override fun toString(): String {
            return "DefaultJvmInstallationMetadata{" +
                    "languageVersion=" + languageVersion +
                    ", javaVersion='" + javaVersion + '\'' +
                    ", javaVendor='" + javaVendor + '\'' +
                    ", runtimeName='" + runtimeName + '\'' +
                    ", runtimeVersion='" + runtimeVersion + '\'' +
                    ", jvmName='" + jvmName + '\'' +
                    ", jvmVersion='" + jvmVersion + '\'' +
                    ", jvmVendor='" + jvmVendor + '\'' +
                    ", architecture='" + architecture + '\'' +
                    '}'
        }

        override fun equals(o: Any?): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }
            val that = o as DefaultJvmInstallationMetadata
            return javaHome == that.javaHome && javaVersion == that.javaVersion && javaVendor == that.javaVendor && runtimeName == that.runtimeName && runtimeVersion == that.runtimeVersion && jvmName == that.jvmName && jvmVersion == that.jvmVersion && jvmVendor == that.jvmVendor && architecture == that.architecture
        }

        override fun hashCode(): Int {
            return Objects.hash(javaHome, javaVersion, javaVendor, runtimeName, runtimeVersion, jvmName, jvmVersion, jvmVendor, architecture)
        }
    }

    class FailureInstallationMetadata(private val javaHomeFile: File, override val errorMessage: String, override val errorCause: Throwable?) : JvmInstallationMetadata {
        override val javaHome: Path
            get() = javaHomeFile.toPath()

        override val languageVersion: JavaVersion
            get() = throw unsupportedOperation()

        override val javaMajorVersion: Int
            get() = throw unsupportedOperation()

        override val vendor: JvmVendor
            get() = throw unsupportedOperation()

        override val javaVersion: String
            get() = throw unsupportedOperation()

        override val runtimeName: String?
            get() = throw unsupportedOperation()

        override val runtimeVersion: String?
            get() = throw unsupportedOperation()

        override val jvmName: String?
            get() = throw unsupportedOperation()

        override val jvmVersion: String?
            get() = throw unsupportedOperation()

        override val jvmVendor: String?
            get() = throw unsupportedOperation()

        override val architecture: String?
            get() = throw unsupportedOperation()

        override val displayName: String
            get() = "Invalid installation: " + errorMessage

        override val capabilities: MutableSet<JavaInstallationCapability>
            get() = mutableSetOf()

        private fun unsupportedOperation(): UnsupportedOperationException {
            return UnsupportedOperationException("Installation is not valid. Original error message: " + errorMessage)
        }

        override val isValidInstallation: Boolean = false
    }

    companion object {
        fun from(
            javaHome: File,
            javaVersion: String,
            javaVendor: String?,
            runtimeName: String?,
            runtimeVersion: String?,
            jvmName: String?,
            jvmVersion: String?,
            jvmVendor: String?,
            architecture: String?
        ): DefaultJvmInstallationMetadata {
            return JvmInstallationMetadata.DefaultJvmInstallationMetadata(javaHome, javaVersion, javaVendor, runtimeName, runtimeVersion, jvmName, jvmVersion, jvmVendor, architecture)
        }

        fun failure(javaHome: File, errorMessage: String?): JvmInstallationMetadata {
            return JvmInstallationMetadata.FailureInstallationMetadata(javaHome, errorMessage ?: "Unknown error", null)
        }

        fun failure(javaHome: File, cause: Exception): JvmInstallationMetadata {
            return JvmInstallationMetadata.FailureInstallationMetadata(javaHome, cause.message ?: cause.toString(), cause)
        }
    }
}
