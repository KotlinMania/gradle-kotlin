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

import org.gradle.StartParameter
import org.gradle.api.internal.StartParameterInternal
import org.gradle.api.launcher.cli.WelcomeMessageConfiguration
import org.gradle.api.logging.configuration.LoggingConfiguration
import org.gradle.cli.CommandLineArgumentException
import org.gradle.cli.CommandLineParser
import org.gradle.cli.ParsedCommandLine
import org.gradle.cli.ProjectPropertiesCommandLineConverter
import org.gradle.concurrent.ParallelismConfiguration
import org.gradle.initialization.ParallelismBuildOptions
import org.gradle.initialization.StartParameterBuildOptions
import org.gradle.internal.logging.LoggingConfigurationBuildOptions
import org.gradle.launcher.configuration.AllProperties
import org.gradle.launcher.configuration.BuildLayoutResult
import org.gradle.launcher.daemon.toolchain.ToolchainBuildOptions

class StartParameterConverter {
    private val welcomeMessageConfigurationCommandLineConverter = BuildOptionBackedConverter<WelcomeMessageConfiguration?>(WelcomeMessageBuildOptions())
    private val loggingConfigurationCommandLineConverter = BuildOptionBackedConverter<LoggingConfiguration?>(LoggingConfigurationBuildOptions())
    private val parallelConfigurationCommandLineConverter = BuildOptionBackedConverter<ParallelismConfiguration?>(ParallelismBuildOptions())
    private val projectPropertiesCommandLineConverter = ProjectPropertiesCommandLineConverter()
    private val buildOptionsConverter = BuildOptionBackedConverter<StartParameterInternal?>(StartParameterBuildOptions())
    private val toolchainOptionsConverter = BuildOptionBackedConverter<StartParameter?>(ToolchainBuildOptions.forStartParameter())

    fun configure(parser: CommandLineParser) {
        welcomeMessageConfigurationCommandLineConverter.configure(parser)
        loggingConfigurationCommandLineConverter.configure(parser)
        parallelConfigurationCommandLineConverter.configure(parser)
        projectPropertiesCommandLineConverter.configure(parser)
        toolchainOptionsConverter.configure(parser)
        parser.allowMixedSubcommandsAndOptions()
        buildOptionsConverter.configure(parser)
    }

    @Throws(CommandLineArgumentException::class)
    fun convert(
        parsedCommandLine: ParsedCommandLine,
        buildLayout: BuildLayoutResult,
        properties: AllProperties,
        environmentVariables: MutableMap<String?, String?>?,
        startParameter: StartParameterInternal
    ): StartParameterInternal {
        buildLayout.applyTo(startParameter)

        welcomeMessageConfigurationCommandLineConverter.convert(parsedCommandLine, properties.getProperties(), environmentVariables, startParameter.getWelcomeMessageConfiguration())
        loggingConfigurationCommandLineConverter.convert(parsedCommandLine, properties.getProperties(), environmentVariables, startParameter)
        parallelConfigurationCommandLineConverter.convert(parsedCommandLine, properties.getProperties(), environmentVariables, startParameter)

        startParameter.getSystemPropertiesArgs().putAll(properties.getRequestedSystemProperties())

        projectPropertiesCommandLineConverter.convert(parsedCommandLine, startParameter.getProjectPropertiesUntracked())
        toolchainOptionsConverter.convert(parsedCommandLine, properties.getRequestedSystemProperties(), environmentVariables, startParameter)

        if (!parsedCommandLine.getExtraArguments().isEmpty()) {
            startParameter.setTaskNames(parsedCommandLine.getExtraArguments())
        }

        buildOptionsConverter.convert(parsedCommandLine, properties.getProperties(), environmentVariables, startParameter)

        return startParameter
    }
}
