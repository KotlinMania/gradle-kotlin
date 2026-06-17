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
package org.gradle.jvm.toolchain.internal

import org.gradle.api.Describable
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.internal.file.FileFactory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata
import org.gradle.internal.os.OperatingSystem.Companion.current
import org.gradle.jvm.toolchain.JavaInstallationMetadata
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaLanguageVersion.Companion.of

open class JavaToolchain(
    metadata: JvmInstallationMetadata,
    fileFactory: FileFactory,
    input: JavaToolchainInput?,
    isFallbackToolchain: Boolean
) : Describable, JavaInstallationMetadata {
    private val javaHome: Directory
    private val javaVersion: JavaLanguageVersion

    @get:Internal
    val metadata: JvmInstallationMetadata

    @get:Nested
    protected val taskInputs: JavaToolchainInput?

    @get:Internal
    val isFallbackToolchain: Boolean

    init {
        this.javaHome = fileFactory.dir(metadata.javaHome!!.toFile())
        this.javaVersion = deriveJavaLanguageVersion(metadata)
        this.metadata = metadata
        this.taskInputs = input
        this.isFallbackToolchain = isFallbackToolchain
    }

    override fun getLanguageVersion(): JavaLanguageVersion {
        return javaVersion
    }

    @Internal
    override fun getJavaRuntimeVersion(): String {
        return metadata.runtimeVersion!!
    }

    override fun getJvmVersion(): String {
        return metadata.jvmVersion!!
    }

    @Internal
    override fun getInstallationPath(): Directory {
        return javaHome
    }

    @Internal
    override fun isCurrentJvm(): Boolean {
        return javaHome.getAsFile() == Jvm.current().getJavaHome()
    }

    override fun getVendor(): String {
        return metadata.vendor!!.displayName!!
    }

    @Internal
    override fun getDisplayName(): String {
        return javaHome.toString()
    }

    open fun findExecutable(toolName: String?): RegularFile? {
        return getInstallationPath().file(getBinaryPath(toolName))
    }

    override fun toString(): String {
        return "JavaToolchain(javaHome=" + getDisplayName() + ")"
    }

    private fun getBinaryPath(java: String?): String {
        return "bin/" + current()!!.getExecutableName(java)
    }

    companion object {
        private fun deriveJavaLanguageVersion(metadata: JvmInstallationMetadata): JavaLanguageVersion {
            return if (metadata.isValidInstallation) of(metadata.javaMajorVersion) else DefaultJavaLanguageVersion.UNKNOWN
        }
    }
}
