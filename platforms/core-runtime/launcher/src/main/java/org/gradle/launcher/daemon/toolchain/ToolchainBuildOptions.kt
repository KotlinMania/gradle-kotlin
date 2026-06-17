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
package org.gradle.launcher.daemon.toolchain

import org.gradle.StartParameter
import org.gradle.cli.OptionCategory
import org.gradle.internal.buildoption.AbstractBuildOption
import org.gradle.internal.buildoption.BooleanBuildOption
import org.gradle.internal.buildoption.BooleanCommandLineOptionConfiguration
import org.gradle.internal.buildoption.BuildOption
import org.gradle.internal.buildoption.BuildOptionSet
import org.gradle.internal.buildoption.CommandLineOptionConfiguration
import org.gradle.internal.buildoption.Origin
import org.gradle.internal.buildoption.StringBuildOption
import org.gradle.jvm.toolchain.internal.AutoInstalledInstallationSupplier
import org.gradle.jvm.toolchain.internal.EnvironmentVariableListInstallationSupplier
import org.gradle.jvm.toolchain.internal.IntellijInstallationSupplier
import org.gradle.jvm.toolchain.internal.LocationListInstallationSupplier
import org.gradle.jvm.toolchain.internal.ToolchainConfiguration
import java.io.File
import java.util.Arrays

object ToolchainBuildOptions {
    @JvmStatic
    fun forToolChainConfiguration(): BuildOptionSet<ToolchainConfiguration> {
        return object : BuildOptionSet<ToolchainConfiguration>() {
            private val options: MutableList<out BuildOption<in ToolchainConfiguration>> = Arrays.asList<AbstractBuildOption<ToolchainConfiguration, out CommandLineOptionConfiguration>>(
                object : JavaInstallationPathsOption<ToolchainConfiguration>() {
                    override fun applyTo(value: String, settings: ToolchainConfiguration, origin: Origin) {
                        settings.setInstallationsFromPaths(Arrays.asList<String>(*value.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()))
                    }
                },
                object : JavaInstallationEnvironmentPathsOption<ToolchainConfiguration>() {
                    override fun applyTo(value: String, settings: ToolchainConfiguration, origin: Origin) {
                        settings.setJavaInstallationsFromEnvironment(Arrays.asList<String>(*value.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()))
                    }
                },
                object : AutoDetectionOption<ToolchainConfiguration>() {
                    override fun applyTo(value: Boolean, settings: ToolchainConfiguration, origin: Origin) {
                        settings.isAutoDetectEnabled = value
                    }
                },
                object : AutoDownloadOption<ToolchainConfiguration>() {
                    override fun applyTo(value: Boolean, settings: ToolchainConfiguration, origin: Origin) {
                        settings.isDownloadEnabled = value
                    }
                },
                object : IntellijJdkBuildOption<ToolchainConfiguration>() {
                    override fun applyTo(value: String, settings: ToolchainConfiguration, origin: Origin) {
                        settings.intelliJdkDirectory = File(value)
                    }
                }
            )

            override fun getAllOptions(): MutableList<out BuildOption<in ToolchainConfiguration>> {
                return options
            }
        }
    }

    fun forStartParameter(): BuildOptionSet<StartParameter> {
        return object : BuildOptionSet<StartParameter>() {
            private val options: MutableList<out BuildOption<in StartParameter>> = Arrays.asList<AbstractBuildOption<StartParameter, out CommandLineOptionConfiguration>>(
                object : JavaInstallationPathsOption<StartParameter>() {
                    override fun applyTo(value: String, settings: StartParameter, origin: Origin) {
                        ToolchainBuildOptions.putProjectPropertyIfAbsent<StartParameter, CommandLineOptionConfiguration>(settings, this, value)
                    }
                },
                object : JavaInstallationEnvironmentPathsOption<StartParameter>() {
                    override fun applyTo(value: String, settings: StartParameter, origin: Origin) {
                        ToolchainBuildOptions.putProjectPropertyIfAbsent<StartParameter, CommandLineOptionConfiguration>(settings, this, value)
                    }
                },
                object : AutoDetectionOption<StartParameter>() {
                    override fun applyTo(value: Boolean, settings: StartParameter, origin: Origin) {
                        ToolchainBuildOptions.putProjectPropertyIfAbsent<StartParameter, BooleanCommandLineOptionConfiguration>(settings, this, value.toString())
                    }
                },
                object : AutoDownloadOption<StartParameter>() {
                    override fun applyTo(value: Boolean, settings: StartParameter, origin: Origin) {
                        ToolchainBuildOptions.putProjectPropertyIfAbsent<StartParameter, BooleanCommandLineOptionConfiguration>(settings, this, value.toString())
                    }
                },
                object : IntellijJdkBuildOption<StartParameter>() {
                    override fun applyTo(value: String, settings: StartParameter, origin: Origin) {
                        ToolchainBuildOptions.putProjectPropertyIfAbsent<StartParameter, CommandLineOptionConfiguration>(settings, this, value)
                    }
                }
            )

            override fun getAllOptions(): MutableList<out BuildOption<in StartParameter>> {
                return options
            }
        }
    }

    private fun <K, V : CommandLineOptionConfiguration?> putProjectPropertyIfAbsent(
        settings: StartParameter,
        option: AbstractBuildOption<K?, V?>,
        value: String
    ) {
        val property: String? = checkNotNull(option.getProperty())
        settings.getProjectProperties().putIfAbsent(property!!, value)
    }

    private abstract class JavaInstallationPathsOption<T> : StringBuildOption<T?>(GRADLE_PROPERTY) {
        override fun getCategory(): OptionCategory {
            return OptionCategory.CONFIGURATION
        }

        companion object {
            private val GRADLE_PROPERTY = LocationListInstallationSupplier.JAVA_INSTALLATIONS_PATHS_PROPERTY
        }
    }

    private abstract class JavaInstallationEnvironmentPathsOption<T> : StringBuildOption<T?>(GRADLE_PROPERTY) {
        override fun getCategory(): OptionCategory {
            return OptionCategory.CONFIGURATION
        }

        companion object {
            private val GRADLE_PROPERTY = EnvironmentVariableListInstallationSupplier.JAVA_INSTALLATIONS_FROM_ENV_PROPERTY
        }
    }

    private abstract class AutoDetectionOption<T> : BooleanBuildOption<T?>(GRADLE_PROPERTY) {
        override fun getCategory(): OptionCategory {
            return OptionCategory.CONFIGURATION
        }

        companion object {
            private val GRADLE_PROPERTY = ToolchainConfiguration.AUTO_DETECT
        }
    }

    private abstract class AutoDownloadOption<T> : BooleanBuildOption<T?>(GRADLE_PROPERTY) {
        override fun getCategory(): OptionCategory {
            return OptionCategory.CONFIGURATION
        }

        companion object {
            private val GRADLE_PROPERTY = AutoInstalledInstallationSupplier.AUTO_DOWNLOAD
        }
    }

    private abstract class IntellijJdkBuildOption<T> : StringBuildOption<T?>(GRADLE_PROPERTY) {
        override fun getCategory(): OptionCategory {
            return OptionCategory.CONFIGURATION
        }

        companion object {
            private val GRADLE_PROPERTY = IntellijInstallationSupplier.INTELLIJ_JDK_DIR_PROPERTY
        }
    }
}
