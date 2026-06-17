/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.tooling.internal.provider.connection

import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.configuration.LoggingConfiguration
import org.gradle.cli.CommandLineConverter
import org.gradle.cli.CommandLineParser
import org.gradle.cli.ParsedCommandLine
import org.gradle.cli.SystemPropertiesCommandLineConverter
import org.gradle.internal.logging.LoggingConfigurationBuildOptions
import java.util.Optional
import java.util.function.Supplier

class BuildLogLevelMixIn(parameters: ProviderOperationParameters) {
    val buildLogLevel: LogLevel?

    init {
        this.buildLogLevel = calcBuildLogLevel(parameters)
    }

    companion object {
        private fun calcBuildLogLevel(parameters: ProviderOperationParameters): LogLevel? {
            val loggingBuildOptions = LoggingConfigurationBuildOptions()
            val converter: CommandLineConverter<LoggingConfiguration?> = loggingBuildOptions.commandLineConverter()

            val propertiesCommandLineConverter = SystemPropertiesCommandLineConverter()
            val parser = CommandLineParser().allowUnknownOptions().allowMixedSubcommandsAndOptions()

            converter.configure(parser)
            propertiesCommandLineConverter.configure(parser)

            val arguments = parameters.getArguments()
            val parsedCommandLine = parser.parse(if (arguments == null) mutableListOf<String?>() else arguments)

            //configure verbosely only if arguments do not specify any log level.
            return getLogLevelFromCommandLineOptions(loggingBuildOptions, parsedCommandLine)
                .orElseGet(Supplier {
                    getLogLevelFromCommandLineProperties(propertiesCommandLineConverter, parsedCommandLine).orElseGet(Supplier {
                        if (parameters.getVerboseLogging()) {
                            return@orElseGet LogLevel.DEBUG
                        }
                        null
                    })
                }
                )
        }

        private fun getLogLevelFromCommandLineOptions(loggingBuildOptions: LoggingConfigurationBuildOptions, parsedCommandLine: ParsedCommandLine): Optional<LogLevel?> {
            return loggingBuildOptions.getLongLogLevelOptions().stream()
                .filter { option: String? -> parsedCommandLine.hasOption(option) }
                .map<LogLevel?> { value: String? -> LoggingConfigurationBuildOptions.LogLevelOption.parseLogLevel(value) }
                .findFirst()
        }

        private fun getLogLevelFromCommandLineProperties(propertiesCommandLineConverter: SystemPropertiesCommandLineConverter, parsedCommandLine: ParsedCommandLine): Optional<LogLevel?> {
            val properties = propertiesCommandLineConverter.convert(parsedCommandLine, HashMap<String?, String?>())
            val logLevelCommandLineProperty = properties.get(LoggingConfigurationBuildOptions.LogLevelOption.GRADLE_PROPERTY)
            if (logLevelCommandLineProperty != null) {
                try {
                    return Optional.of<LogLevel?>(LoggingConfigurationBuildOptions.LogLevelOption.parseLogLevel(logLevelCommandLineProperty))
                } catch (e: IllegalArgumentException) {
                    // fall through
                }
            }
            return Optional.empty<LogLevel?>()
        }
    }
}
