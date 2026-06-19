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
    private val environment: Map<String, String>
) : ToolchainConfiguration {
    override var javaInstallationsFromEnvironment: MutableCollection<String>
    override var installationsFromPaths: MutableCollection<String>
    override var isAutoDetectEnabled = true
    override var isDownloadEnabled = true
    override var intelliJdkDirectory: File?

    @Inject
    constructor() : this(System.getenv())

    constructor(environment: Map<String, String>) : this(current()!!, SystemProperties.getInstance(), environment)

    init {
        this.intelliJdkDirectory = defaultJdksDirectory(os)
        this.javaInstallationsFromEnvironment = mutableListOf()
        this.installationsFromPaths = mutableListOf()
    }

    override val asdfDataDirectory: File
        get() {
            val asdfEnvVar = environment["ASDF_DATA_DIR"]
            if (asdfEnvVar != null) {
                return File(asdfEnvVar)
            }
            return File(systemProperties.getUserHome(), ".asdf")
        }

    private fun defaultJdksDirectory(os: OperatingSystem): File {
        if (os.isMacOsX) {
            return File(systemProperties.getUserHome(), "Library/Java/JavaVirtualMachines")
        }
        return File(systemProperties.getUserHome(), ".jdks")
    }

    override val jabbaHomeDirectory: File?
        get() {
            val jabbaHome = environment["JABBA_HOME"]
            if (jabbaHome != null) {
                return File(jabbaHome)
            }
            return null
        }

    override val sdkmanCandidatesDirectory: File
        get() {
            val asdfEnvVar = environment["SDKMAN_CANDIDATES_DIR"]
            if (asdfEnvVar != null) {
                return File(asdfEnvVar)
            }
            return File(systemProperties.getUserHome(), ".sdkman/candidates")
        }

    override fun getEnvironmentVariableValue(variableName: String): String? {
        return environment[variableName]
    }
}
