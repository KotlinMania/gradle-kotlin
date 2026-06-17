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
    @JvmField
    val javaHome: Path?

    /**
     * Parsed equivalent of [.getJavaVersion].
     */
    @JvmField
    val languageVersion: JavaVersion?

    /**
     * The major Java version parsed from [.getJavaVersion].
     */
    @JvmField
    val javaMajorVersion: Int

    /**
     * A wrapper around the raw value of the toolchain vendor.
     *
     * @see ProbedSystemProperty.JAVA_VENDOR
     */
    @JvmField
    val vendor: JvmVendor?

    /**
     * @see ProbedSystemProperty.JAVA_VERSION
     */
    @JvmField
    val javaVersion: String?

    /**
     * @see ProbedSystemProperty.RUNTIME_NAME
     */
    @JvmField
    val runtimeName: String?

    /**
     * @see ProbedSystemProperty.RUNTIME_VERSION
     */
    @JvmField
    val runtimeVersion: String?

    /**
     * @see ProbedSystemProperty.VM_NAME
     */
    @JvmField
    val jvmName: String?

    /**
     * @see ProbedSystemProperty.VM_VERSION
     */
    @JvmField
    val jvmVersion: String?

    /**
     * @see ProbedSystemProperty.VM_VENDOR
     */
    @JvmField
    val jvmVendor: String?

    /**
     * @see ProbedSystemProperty.OS_ARCH
     */
    @JvmField
    val architecture: String?

    @JvmField
    val displayName: String?

    @JvmField
    val capabilities: MutableSet<JavaInstallationCapability?>?

    @JvmField
    val errorMessage: String?

    @JvmField
    val errorCause: Throwable?

    @JvmField
    val isValidInstallation: Boolean

    class DefaultJvmInstallationMetadata private constructor(
        javaHome: File,
        private val javaVersion: String,
        private val javaVendor: String?,
        private val runtimeName: String?,
        private val runtimeVersion: String?,
        private val jvmName: String?,
        private val jvmVersion: String?,
        private val jvmVendor: String?,
        private val architecture: String?
    ) : JvmInstallationMetadata {
        private val javaHome: Path
        private val languageVersion: JavaVersion?
        private val javaMajorVersion: Int

        private val capabilities = Cached.of({ this.gatherCapabilities() })

        init {
            this.javaHome = javaHome.toPath()
            this.languageVersion = toVersion(javaVersion)
            this.javaMajorVersion = parseMajorVersion(javaVersion)
        }

        override fun getJavaHome(): Path {
            return javaHome
        }

        override fun getLanguageVersion(): JavaVersion? {
            return languageVersion
        }

        override fun getJavaMajorVersion(): Int {
            return javaMajorVersion
        }

        override fun getVendor(): JvmVendor {
            return JvmVendor.Companion.fromString(javaVendor)
        }

        override fun getJavaVersion(): String {
            return javaVersion
        }

        override fun getRuntimeName(): String? {
            return runtimeName
        }

        override fun getRuntimeVersion(): String? {
            return runtimeVersion
        }

        override fun getJvmName(): String? {
            return jvmName
        }

        override fun getJvmVersion(): String? {
            return jvmVersion
        }

        override fun getJvmVendor(): String? {
            return jvmVendor
        }

        override fun getArchitecture(): String? {
            return architecture
        }

        override fun getDisplayName(): String {
            val vendor = determineVendorName()
            return vendor + " " + determineInstallationType() + " " + getJavaMajorVersion() + " (" + getRuntimeVersion() + ")"
        }

        private fun determineVendorName(): String? {
            val vendor = getVendor().getKnownVendor()
            if (vendor == JvmVendor.KnownJvmVendor.ORACLE) {
                if (jvmName != null && jvmName.contains("OpenJDK")) {
                    return "OpenJDK"
                }
            }
            return getVendor().getDisplayName()
        }

        private fun determineInstallationType(): String {
            if (getCapabilities()!!.containsAll(JavaInstallationCapability.Companion.JDK_CAPABILITIES)) {
                return "JDK"
            } else {
                return "JRE"
            }
        }

        override fun getCapabilities(): MutableSet<JavaInstallationCapability?>? {
            return capabilities.get()
        }

        private fun gatherCapabilities(): MutableSet<JavaInstallationCapability?> {
            val capabilities: MutableSet<JavaInstallationCapability?> = EnumSet.noneOf<JavaInstallationCapability?>(JavaInstallationCapability::class.java)
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
            val isJ9vm = jvmName!!.contains("J9")
            if (isJ9vm) {
                capabilities.add(JavaInstallationCapability.J9_VIRTUAL_MACHINE)
            }
            return capabilities
        }

        private fun getToolByExecutable(name: String?): File {
            return File(File(javaHome.toFile(), "bin"), current()!!.getExecutableName(name))
        }

        override fun getErrorMessage(): String? {
            throw UnsupportedOperationException()
        }

        override fun getErrorCause(): Throwable? {
            throw UnsupportedOperationException()
        }

        override fun isValidInstallation(): Boolean {
            return true
        }

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

    class FailureInstallationMetadata private constructor(private val javaHome: File, private val errorMessage: String, private val cause: Exception?) : JvmInstallationMetadata {
        override fun getJavaHome(): Path {
            return javaHome.toPath()
        }

        override fun getLanguageVersion(): JavaVersion? {
            throw unsupportedOperation()
        }

        override fun getJavaMajorVersion(): Int {
            throw unsupportedOperation()
        }

        override fun getVendor(): JvmVendor? {
            throw unsupportedOperation()
        }

        override fun getJavaVersion(): String? {
            throw unsupportedOperation()
        }

        override fun getRuntimeName(): String? {
            throw unsupportedOperation()
        }

        override fun getRuntimeVersion(): String? {
            throw unsupportedOperation()
        }

        override fun getJvmName(): String? {
            throw unsupportedOperation()
        }

        override fun getJvmVersion(): String? {
            throw unsupportedOperation()
        }

        override fun getJvmVendor(): String? {
            throw unsupportedOperation()
        }

        override fun getArchitecture(): String? {
            throw unsupportedOperation()
        }

        override fun getDisplayName(): String {
            return "Invalid installation: " + getErrorMessage()
        }

        override fun getCapabilities(): MutableSet<JavaInstallationCapability?> {
            return mutableSetOf<JavaInstallationCapability?>()
        }

        private fun unsupportedOperation(): UnsupportedOperationException {
            return UnsupportedOperationException("Installation is not valid. Original error message: " + getErrorMessage())
        }

        override fun getErrorMessage(): String {
            return errorMessage
        }

        override fun getErrorCause(): Throwable? {
            return cause
        }

        override fun isValidInstallation(): Boolean {
            return false
        }
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

        fun failure(javaHome: File, errorMessage: String): JvmInstallationMetadata {
            return JvmInstallationMetadata.FailureInstallationMetadata(javaHome, errorMessage, null)
        }

        fun failure(javaHome: File, cause: Exception): JvmInstallationMetadata {
            return JvmInstallationMetadata.FailureInstallationMetadata(javaHome, cause.message, cause)
        }
    }
}
