/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.launcher.cli.converter

import org.gradle.api.Project
import org.gradle.api.specs.Spec
import org.gradle.initialization.BuildLayoutParametersBuildOptions
import org.gradle.initialization.ParallelismBuildOptions
import org.gradle.initialization.StartParameterBuildOptions
import org.gradle.initialization.layout.BuildLayoutFactory
import org.gradle.internal.Cast
import org.gradle.internal.UncheckedException
import org.gradle.internal.buildconfiguration.DaemonJvmPropertiesDefaults
import org.gradle.internal.buildoption.BuildOption
import org.gradle.internal.logging.LoggingConfigurationBuildOptions
import org.gradle.launcher.configuration.AllProperties
import org.gradle.launcher.configuration.BuildLayoutResult
import org.gradle.launcher.configuration.InitialProperties
import org.gradle.launcher.daemon.configuration.DaemonBuildOptions
import org.gradle.launcher.daemon.toolchain.ToolchainBuildOptions
import org.gradle.util.internal.CollectionUtils
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.Serializable
import java.util.Collections
import java.util.Properties

class LayoutToPropertiesConverter(private val buildLayoutFactory: BuildLayoutFactory) {
    private val allBuildOptions: MutableList<BuildOption<*>?> = ArrayList<BuildOption<*>?>()

    init {
        allBuildOptions.addAll(BuildLayoutParametersBuildOptions().getAllOptions())
        allBuildOptions.addAll(StartParameterBuildOptions().getAllOptions())
        allBuildOptions.addAll(LoggingConfigurationBuildOptions().getAllOptions()) // TODO maybe a new converter also here
        allBuildOptions.addAll(WelcomeMessageBuildOptions().getAllOptions()) // TODO maybe a new converter also here
        allBuildOptions.addAll(DaemonBuildOptions().getAllOptions())
        allBuildOptions.addAll(ParallelismBuildOptions().getAllOptions())
        allBuildOptions.addAll(ToolchainBuildOptions.forToolChainConfiguration().getAllOptions())
    }

    fun convert(initialProperties: InitialProperties, layout: BuildLayoutResult): AllProperties {
        val properties: MutableMap<String?, String?> = HashMap<String?, String?>()
        configureFromHomeDir(layout.getGradleInstallationHomeDir(), properties)
        configureFromBuildDir(layout, properties)
        configureFromHomeDir(layout.getGradleUserHomeDir(), properties)
        configureFromSystemPropertiesOfThisJvm(Cast.uncheckedNonnullCast<MutableMap<Any?, Any?>?>(properties))
        properties.putAll(initialProperties.requestedSystemProperties)

        val daemonJvmProperties: MutableMap<String?, String?> = HashMap<String?, String?>()
        configureFromDaemonJVMProperties(layout, daemonJvmProperties)
        return Result(properties, daemonJvmProperties, initialProperties)
    }

    private fun configureFromSystemPropertiesOfThisJvm(properties: MutableMap<Any?, Any?>) {
        for (entry in System.getProperties().entries) {
            val key = entry.key
            val value = entry.value
            if (key is Serializable && (value is Serializable || value == null)) {
                properties.put(key, value)
            }
        }
    }

    private fun configureFromHomeDir(gradleUserHomeDir: File?, result: MutableMap<String?, String?>) {
        maybeConfigureFrom(File(gradleUserHomeDir, Project.GRADLE_PROPERTIES), result)
    }

    private fun configureFromBuildDir(layoutResult: BuildLayoutResult, result: MutableMap<String?, String?>) {
        val layout = buildLayoutFactory.getLayoutFor(layoutResult.toLayoutConfiguration())
        maybeConfigureFrom(File(layout.getRootDirectory(), Project.GRADLE_PROPERTIES), result)
    }

    private fun configureFromDaemonJVMProperties(layoutResult: BuildLayoutResult, result: MutableMap<String?, String?>) {
        val layout = buildLayoutFactory.getLayoutFor(layoutResult.toLayoutConfiguration())
        configureFrom(File(layout.getRootDirectory(), DaemonJvmPropertiesDefaults.DAEMON_JVM_PROPERTIES_FILE), result)
    }

    private fun configureFrom(propertiesFile: File, result: MutableMap<String?, String?>) {
        val properties: Properties = readProperties(propertiesFile)
        for (key in properties.keys) {
            result.put(key.toString(), properties.get(key).toString())
        }
    }

    private fun maybeConfigureFrom(propertiesFile: File, result: MutableMap<String?, String?>) {
        val properties: Properties = readProperties(propertiesFile)
        for (key in properties.keys) {
            val keyAsString = key.toString()
            val validOption = CollectionUtils.findFirst<BuildOption<*>?>(
                allBuildOptions,
                Spec { option: BuildOption<*>? -> keyAsString == option!!.getProperty() || keyAsString == option.getDeprecatedProperty() })

            if (validOption != null) {
                result.put(key.toString(), properties.get(key).toString())
            }
        }
    }

    private class Result(private val properties: MutableMap<String?, String?>, private val daemonJvmProperties: MutableMap<String?, String?>, private val initialProperties: InitialProperties) :
        AllProperties {
        override fun getRequestedSystemProperties(): MutableMap<String?, String?>? {
            return initialProperties.requestedSystemProperties
        }

        override fun getProperties(): MutableMap<String?, String?> {
            return Collections.unmodifiableMap<String?, String?>(properties)
        }

        override fun getDaemonJvmProperties(): MutableMap<String?, String?> {
            return Collections.unmodifiableMap<String?, String?>(daemonJvmProperties)
        }

        override fun merge(systemProperties: MutableMap<String?, String?>): Result {
            val properties: MutableMap<String?, String?> = HashMap<String?, String?>(this.properties)
            properties.putAll(systemProperties)
            properties.putAll(initialProperties.requestedSystemProperties)
            return Result(properties, daemonJvmProperties, initialProperties)
        }
    }

    companion object {
        private fun readProperties(propertiesFile: File): Properties {
            val properties = Properties()

            if (propertiesFile.isFile()) {
                try {
                    FileInputStream(propertiesFile).use { inputStream ->
                        properties.load(inputStream)
                    }
                } catch (e: IOException) {
                    throw UncheckedException.throwAsUncheckedException(e)
                }
            }
            return properties
        }
    }
}
