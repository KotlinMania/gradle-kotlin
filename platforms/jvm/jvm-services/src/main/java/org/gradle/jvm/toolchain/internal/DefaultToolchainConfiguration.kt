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
package org.gradle.jvm.toolchain.internal

import com.google.common.annotations.VisibleForTesting
import org.gradle.internal.SystemProperties
import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.os.OperatingSystem.Companion.current
import java.io.File
import javax.inject.Inject

class DefaultToolchainConfiguration @VisibleForTesting internal constructor(
    os: OperatingSystem,
    private val systemProperties: SystemProperties,
    private val environment: MutableMap<String?, String?>
) : ToolchainConfiguration {
    private var javaInstallationsFromEnvironment: MutableCollection<String?>?
    private var installationsFromPaths: MutableCollection<String?>?
    private var autoDetectEnabled = true
    private var downloadEnabled = true
    private var intellijInstallationDirectory: File?

    @Inject
    constructor() : this(System.getenv())

    constructor(environment: MutableMap<String?, String?>) : this(current()!!, SystemProperties.getInstance(), environment)

    init {
        this.intellijInstallationDirectory = defaultJdksDirectory(os)
        this.javaInstallationsFromEnvironment = mutableListOf<String?>()
        this.installationsFromPaths = mutableListOf<String?>()
    }

    override fun getJavaInstallationsFromEnvironment(): MutableCollection<String?>? {
        return javaInstallationsFromEnvironment
    }

    override fun setJavaInstallationsFromEnvironment(javaInstallationsFromEnvironment: MutableCollection<String?>?) {
        this.javaInstallationsFromEnvironment = javaInstallationsFromEnvironment
    }

    override fun getInstallationsFromPaths(): MutableCollection<String?>? {
        return installationsFromPaths
    }

    override fun setInstallationsFromPaths(installationsFromPaths: MutableCollection<String?>?) {
        this.installationsFromPaths = installationsFromPaths
    }

    override fun isAutoDetectEnabled(): Boolean {
        return autoDetectEnabled
    }

    override fun setAutoDetectEnabled(autoDetectEnabled: Boolean) {
        this.autoDetectEnabled = autoDetectEnabled
    }

    override fun isDownloadEnabled(): Boolean {
        return downloadEnabled
    }

    override fun setDownloadEnabled(enabled: Boolean) {
        this.downloadEnabled = enabled
    }

    override fun getAsdfDataDirectory(): File {
        val asdfEnvVar = environment.get("ASDF_DATA_DIR")
        if (asdfEnvVar != null) {
            return File(asdfEnvVar)
        }
        return File(systemProperties.getUserHome(), ".asdf")
    }

    override fun getIntelliJdkDirectory(): File? {
        return intellijInstallationDirectory
    }

    override fun setIntelliJdkDirectory(intellijInstallationDirectory: File?) {
        this.intellijInstallationDirectory = intellijInstallationDirectory
    }

    private fun defaultJdksDirectory(os: OperatingSystem): File {
        if (os.isMacOsX) {
            return File(systemProperties.getUserHome(), "Library/Java/JavaVirtualMachines")
        }
        return File(systemProperties.getUserHome(), ".jdks")
    }

    override fun getJabbaHomeDirectory(): File? {
        val jabbaHome = environment.get("JABBA_HOME")
        if (jabbaHome != null) {
            return File(jabbaHome)
        }
        return null
    }

    override fun getSdkmanCandidatesDirectory(): File {
        val asdfEnvVar = environment.get("SDKMAN_CANDIDATES_DIR")
        if (asdfEnvVar != null) {
            return File(asdfEnvVar)
        }
        return File(systemProperties.getUserHome(), ".sdkman/candidates")
    }

    override fun getEnvironmentVariableValue(variableName: String?): String? {
        return environment.get(variableName)
    }
}
