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
package org.gradle.launcher.cli.converter

import org.gradle.api.internal.StartParameterInternal
import org.gradle.cli.CommandLineConverter
import org.gradle.cli.CommandLineParser
import org.gradle.cli.ParsedCommandLine
import org.gradle.initialization.BuildLayoutParameters
import org.gradle.initialization.BuildLayoutParametersBuildOptions
import org.gradle.initialization.LayoutCommandLineConverter
import org.gradle.initialization.layout.BuildLayoutConfiguration
import org.gradle.launcher.configuration.BuildLayoutResult
import org.gradle.launcher.configuration.InitialProperties
import java.io.File
import java.util.function.Consumer

class BuildLayoutConverter {
    private val buildLayoutConverter: CommandLineConverter<BuildLayoutParameters?> = LayoutCommandLineConverter()

    fun configure(parser: CommandLineParser?) {
        buildLayoutConverter.configure(parser)
    }

    fun defaultValues(): BuildLayoutResult {
        return Result(BuildLayoutParameters())
    }

    @JvmOverloads
    fun convert(
        systemProperties: InitialProperties,
        commandLine: ParsedCommandLine?,
        workingDir: File?,
        defaults: Consumer<BuildLayoutParameters?> = Consumer { parameters: BuildLayoutParameters? -> }
    ): BuildLayoutResult {
        val layoutParameters = BuildLayoutParameters()
        if (workingDir != null) {
            layoutParameters.setCurrentDir(workingDir)
        }
        defaults.accept(layoutParameters)
        val requestedSystemProperties = systemProperties.requestedSystemProperties
        BuildLayoutParametersBuildOptions().propertiesConverter().convert(requestedSystemProperties, layoutParameters)
        buildLayoutConverter.convert(commandLine, layoutParameters)
        return Result(layoutParameters)
    }

    private class Result(private val buildLayout: BuildLayoutParameters) : BuildLayoutResult {
        override fun applyTo(buildLayout: BuildLayoutParameters) {
            buildLayout.setCurrentDir(this.buildLayout.getCurrentDir())
            buildLayout.setProjectDir(this.buildLayout.getProjectDir())
            buildLayout.setGradleUserHomeDir(this.buildLayout.getGradleUserHomeDir())
            buildLayout.setGradleInstallationHomeDir(this.buildLayout.getGradleInstallationHomeDir())
        }

        @Suppress("deprecation") // StartParameter.setSettingsFile()
        override fun applyTo(startParameter: StartParameterInternal) {
            // Note that order is important here, as the setters have some side effects
            if (buildLayout.getProjectDir() != null) {
                startParameter.setProjectDir(buildLayout.getProjectDir())
            }
            startParameter.setCurrentDir(buildLayout.getCurrentDir())
            startParameter.setGradleUserHomeDir(buildLayout.getGradleUserHomeDir())
        }

        override fun toLayoutConfiguration(): BuildLayoutConfiguration {
            return BuildLayoutConfiguration(buildLayout)
        }

        override fun getGradleInstallationHomeDir(): File? {
            return buildLayout.getGradleInstallationHomeDir()
        }

        override fun getGradleUserHomeDir(): File? {
            return buildLayout.getGradleUserHomeDir()
        }
    }
}
