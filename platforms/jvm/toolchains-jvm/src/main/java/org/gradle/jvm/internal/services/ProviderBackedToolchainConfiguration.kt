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
package org.gradle.jvm.internal.services

import org.gradle.api.Transformer
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.initialization.JvmToolchainsConfigurationValidator
import org.gradle.internal.SystemProperties
import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.os.OperatingSystem.Companion.current
import org.gradle.jvm.toolchain.internal.AutoInstalledInstallationSupplier
import org.gradle.jvm.toolchain.internal.EnvironmentVariableListInstallationSupplier
import org.gradle.jvm.toolchain.internal.IntellijInstallationSupplier
import org.gradle.jvm.toolchain.internal.LocationListInstallationSupplier
import org.gradle.jvm.toolchain.internal.ToolchainConfiguration
import java.io.File
import java.util.Arrays
import javax.inject.Inject

/**
 * TODO: This class shouldn't exist.
 *
 * Instead of puling from ProviderFactory, the settings for toolchain discovery should come from build options.
 *
 * Build options are not exposed to services in the daemon, so this is a temporary solution to keep existing code working.
 *
 */
class ProviderBackedToolchainConfiguration internal constructor(
    private val providerFactory: ProviderFactory,
    private val systemProperties: SystemProperties,
    private val jvmToolchainsConfigurationValidator: JvmToolchainsConfigurationValidator
) : ToolchainConfiguration {
    @Inject
    constructor(providerFactory: ProviderFactory, jvmToolchainsConfigurationValidator: JvmToolchainsConfigurationValidator) : this(
        providerFactory,
        SystemProperties.getInstance(),
        jvmToolchainsConfigurationValidator
    )

    /**
     * Retrieves a Gradle property, and emits a deprecation warning if it was specified as a project property.
     */
    private fun fromGradleProperty(propertyName: String): Provider<String> {
        jvmToolchainsConfigurationValidator.validatePropertyConfiguration(propertyName)
        return providerFactory.gradleProperty(propertyName)
    }

    override var javaInstallationsFromEnvironment: MutableCollection<String>
        get() = Arrays.asList<String>(
            *fromGradleProperty(EnvironmentVariableListInstallationSupplier.JAVA_INSTALLATIONS_FROM_ENV_PROPERTY).getOrElse(
                ""
            ).split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        )
        set(installations) {
            throw UnsupportedOperationException()
        }

    override var installationsFromPaths: MutableCollection<String>
        get() = Arrays.asList<String>(
            *fromGradleProperty(LocationListInstallationSupplier.JAVA_INSTALLATIONS_PATHS_PROPERTY).getOrElse("").split(",".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray())
        set(installations) {
            throw UnsupportedOperationException()
        }

    override var isAutoDetectEnabled: Boolean
        get() = fromGradleProperty(ToolchainConfiguration.AUTO_DETECT).map<Boolean>(Transformer { s: String? -> s.toBoolean() })
            .getOrElse(java.lang.Boolean.TRUE)
        set(enabled) {
            throw UnsupportedOperationException()
        }

    override var isDownloadEnabled: Boolean
        get() = fromGradleProperty(AutoInstalledInstallationSupplier.AUTO_DOWNLOAD).map<Boolean>(Transformer { s: String? -> s.toBoolean() })
            .getOrElse(java.lang.Boolean.TRUE)
        set(enabled) {
            throw UnsupportedOperationException()
        }

    override val asdfDataDirectory: File
        get() {
            val asdfEnvVar = providerFactory.environmentVariable("ASDF_DATA_DIR").getOrNull()
            if (asdfEnvVar != null) {
                return File(asdfEnvVar)
            }
            return File(systemProperties.getUserHome(), ".asdf")
        }

    override var intelliJdkDirectory: File?
        get() = fromGradleProperty(IntellijInstallationSupplier.INTELLIJ_JDK_DIR_PROPERTY).map<File>(Transformer { pathname: String? ->
            File(
                pathname
            )
        }).getOrElse(defaultJdksDirectory(current()!!))
        set(intellijInstallationDirectory) {
            throw UnsupportedOperationException()
        }

    private fun defaultJdksDirectory(os: OperatingSystem): File {
        if (os.isMacOsX) {
            return File(systemProperties.getUserHome(), "Library/Java/JavaVirtualMachines")
        }
        return File(systemProperties.getUserHome(), ".jdks")
    }

    override val jabbaHomeDirectory: File?
        get() {
            val jabbaHome = providerFactory.environmentVariable("JABBA_HOME").getOrNull()
            if (jabbaHome != null) {
                return File(jabbaHome)
            }
            return null
        }

    override val sdkmanCandidatesDirectory: File
        get() {
            val asdfEnvVar = providerFactory.environmentVariable("SDKMAN_CANDIDATES_DIR").getOrNull()
            if (asdfEnvVar != null) {
                return File(asdfEnvVar)
            }
            return File(systemProperties.getUserHome(), ".sdkman/candidates")
        }

    override fun getEnvironmentVariableValue(variableName: String): String? {
        return providerFactory.environmentVariable(variableName).getOrNull()
    }
}
