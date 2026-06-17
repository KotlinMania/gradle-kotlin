/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.internal.logging

import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.configuration.ConsoleOutput
import org.gradle.api.logging.configuration.ConsoleUnicodeSupport
import org.gradle.api.logging.configuration.LoggingConfiguration
import org.gradle.api.logging.configuration.ShowStacktrace
import org.gradle.api.logging.configuration.WarningMode
import org.gradle.cli.CommandLineParser
import org.gradle.cli.OptionCategory
import org.gradle.cli.ParsedCommandLine
import org.gradle.internal.buildoption.AbstractBuildOption
import org.gradle.internal.buildoption.BuildOption
import org.gradle.internal.buildoption.BuildOptionSet
import org.gradle.internal.buildoption.CommandLineOptionConfiguration
import org.gradle.internal.buildoption.EnabledOnlyBooleanBuildOption
import org.gradle.internal.buildoption.Origin
import org.gradle.internal.buildoption.StringBuildOption
import org.gradle.util.internal.TextUtil
import org.jspecify.annotations.NullMarked
import java.util.Arrays

class LoggingConfigurationBuildOptions : BuildOptionSet<LoggingConfiguration?>() {
    // This can be removed once we've moved to compiling for Java 8+
    private val options: MutableList<out BuildOption<in LoggingConfiguration?>?> = Arrays.asList<AbstractBuildOption<LoggingConfiguration?, CommandLineOptionConfiguration?>?>(
        LogLevelOption(),
        StacktraceOption(),
        WarningsOption(),
        ConsoleOption(),
        ConsoleUnicodeOption(),
        NonInteractiveOption()
    )

    override fun getAllOptions(): MutableList<out BuildOption<in LoggingConfiguration?>?> {
        return options
    }

    val longLogLevelOptions: MutableCollection<String?>
        get() = Arrays.asList<String?>(
            LogLevelOption.Companion.DEBUG_LONG_OPTION,
            LogLevelOption.Companion.WARN_LONG_OPTION,
            LogLevelOption.Companion.INFO_LONG_OPTION,
            LogLevelOption.Companion.QUIET_LONG_OPTION
        )

    class LogLevelOption : AbstractBuildOption<LoggingConfiguration?, CommandLineOptionConfiguration?>(
        GRADLE_PROPERTY,
        CommandLineOptionConfiguration.create(QUIET_LONG_OPTION, QUIET_SHORT_OPTION, "Logs errors only."),
        CommandLineOptionConfiguration.create(WARN_LONG_OPTION, WARN_SHORT_OPTION, "Sets the log level to warn."),
        CommandLineOptionConfiguration.create(INFO_LONG_OPTION, INFO_SHORT_OPTION, "Sets the log level to info."),
        CommandLineOptionConfiguration.create(DEBUG_LONG_OPTION, DEBUG_SHORT_OPTION, "Sets log level to debug. Includes the normal stacktrace.")
    ) {
        override fun getCategory(): OptionCategory {
            return OptionCategory.LOGGING
        }

        override fun applyFromProperty(properties: MutableMap<String?, String?>, settings: LoggingConfiguration) {
            val value = properties.get(property)

            if (value != null) {
                val level: LogLevel? = parseLogLevel(value)
                settings.logLevel = level
            }
        }

        override fun configure(parser: CommandLineParser) {
            for (config in commandLineOptionConfigurations) {
                configureCommandLineOption(parser, config!!.getAllOptions(), config.getDescription(), config.isDeprecated(), config.isIncubating())
            }

            parser.allowOneOf(*ALL_SHORT_OPTIONS)
        }

        override fun applyFromCommandLine(options: ParsedCommandLine, settings: LoggingConfiguration) {
            if (options.hasOption(QUIET_LONG_OPTION)) {
                settings.logLevel = LogLevel.QUIET
            } else if (options.hasOption(WARN_LONG_OPTION)) {
                settings.logLevel = LogLevel.WARN
            } else if (options.hasOption(INFO_LONG_OPTION)) {
                settings.logLevel = LogLevel.INFO
            } else if (options.hasOption(DEBUG_LONG_OPTION)) {
                settings.logLevel = LogLevel.DEBUG
            }
        }

        companion object {
            const val GRADLE_PROPERTY: String = "org.gradle.logging.level"
            const val QUIET_LONG_OPTION: String = "quiet"
            const val QUIET_SHORT_OPTION: String = "q"
            const val WARN_LONG_OPTION: String = "warn"
            const val WARN_SHORT_OPTION: String = "w"
            const val INFO_LONG_OPTION: String = "info"
            const val INFO_SHORT_OPTION: String = "i"
            const val DEBUG_LONG_OPTION: String = "debug"
            const val DEBUG_SHORT_OPTION: String = "d"
            private val ALL_SHORT_OPTIONS: Array<String?> = arrayOf<String>(QUIET_SHORT_OPTION, WARN_SHORT_OPTION, INFO_SHORT_OPTION, DEBUG_SHORT_OPTION)

            fun parseLogLevel(value: String): LogLevel? {
                try {
                    val logLevel = LogLevel.valueOf(value.uppercase())
                    require(logLevel != LogLevel.ERROR) { "Log level cannot be set to 'ERROR'." }
                    return logLevel
                } catch (e: IllegalArgumentException) {
                    Origin.forGradleProperty(GRADLE_PROPERTY).handleInvalidValue(value, "must be one of quiet, warn, lifecycle, info, or debug)")
                }
                return null
            }
        }
    }

    class StacktraceOption : AbstractBuildOption<LoggingConfiguration?, CommandLineOptionConfiguration?>(
        GRADLE_PROPERTY, CommandLineOptionConfiguration.create(STACKTRACE_LONG_OPTION, STACKTRACE_SHORT_OPTION, "Prints the stacktrace for all exceptions."), CommandLineOptionConfiguration.create(
            FULL_STACKTRACE_LONG_OPTION, FULL_STACKTRACE_SHORT_OPTION, "Prints the full (very verbose) stacktrace for all exceptions."
        )
    ) {
        override fun getCategory(): OptionCategory {
            return OptionCategory.LOGGING
        }

        override fun applyFromProperty(properties: MutableMap<String?, String?>, settings: LoggingConfiguration) {
            val value = properties.get(property)

            if (value != null) {
                if (value.equals("internal", ignoreCase = true)) {
                    settings.showStacktrace = ShowStacktrace.INTERNAL_EXCEPTIONS
                } else if (value.equals("all", ignoreCase = true)) {
                    settings.showStacktrace = ShowStacktrace.ALWAYS
                } else if (value.equals("full", ignoreCase = true)) {
                    settings.showStacktrace = ShowStacktrace.ALWAYS_FULL
                } else {
                    Origin.forGradleProperty(GRADLE_PROPERTY).handleInvalidValue(value, "must be one of internal, all, or full")
                }
            }
        }

        override fun configure(parser: CommandLineParser) {
            for (config in commandLineOptionConfigurations) {
                configureCommandLineOption(parser, config!!.getAllOptions(), config.getDescription(), config.isDeprecated(), config.isIncubating())
            }

            parser.allowOneOf(*ALL_SHORT_OPTIONS)
        }

        override fun applyFromCommandLine(options: ParsedCommandLine, settings: LoggingConfiguration) {
            if (options.hasOption(STACKTRACE_LONG_OPTION)) {
                settings.showStacktrace = ShowStacktrace.ALWAYS
            } else if (options.hasOption(FULL_STACKTRACE_LONG_OPTION)) {
                settings.showStacktrace = ShowStacktrace.ALWAYS_FULL
            }
        }

        companion object {
            const val GRADLE_PROPERTY: String = "org.gradle.logging.stacktrace"
            const val STACKTRACE_LONG_OPTION: String = "stacktrace"
            const val STACKTRACE_SHORT_OPTION: String = "s"
            const val FULL_STACKTRACE_LONG_OPTION: String = "full-stacktrace"
            const val FULL_STACKTRACE_SHORT_OPTION: String = "S"
            private val ALL_SHORT_OPTIONS: Array<String?> = arrayOf<String>(STACKTRACE_SHORT_OPTION, FULL_STACKTRACE_SHORT_OPTION)
        }
    }

    class ConsoleOption : StringBuildOption<LoggingConfiguration?>(
        GRADLE_PROPERTY,
        CommandLineOptionConfiguration.create(LONG_OPTION, "Specifies which type of console output to generate. Supported values are 'plain', 'colored', 'auto' (default), 'rich', or 'verbose'.")
    ) {
        override fun getCategory(): OptionCategory {
            return OptionCategory.CONSOLE
        }

        override fun applyTo(value: String, settings: LoggingConfiguration, origin: Origin) {
            val normalized = value.lowercase()
            val consoleValue: String = TextUtil.capitalize(normalized)!!
            try {
                val consoleOutput = ConsoleOutput.valueOf(consoleValue)
                settings.consoleOutput = consoleOutput
            } catch (e: IllegalArgumentException) {
                origin.handleInvalidValue(value)
            }
        }

        companion object {
            const val LONG_OPTION: String = "console"
            const val GRADLE_PROPERTY: String = "org.gradle.console"
        }
    }

    @NullMarked
    class ConsoleUnicodeOption : StringBuildOption<LoggingConfiguration>(
        GRADLE_PROPERTY,
        CommandLineOptionConfiguration.create(LONG_OPTION, "Specifies which character types are allowed in the console output. Supported values are 'auto' (default), 'disable', or 'enable'.")
    ) {
        override fun getCategory(): OptionCategory {
            return OptionCategory.CONSOLE
        }

        override fun applyTo(value: String, settings: LoggingConfiguration, origin: Origin) {
            val normalized = value.lowercase()
            val consoleValue: String = TextUtil.capitalize(normalized)!!
            try {
                val consoleUnicodeSupport = ConsoleUnicodeSupport.valueOf(consoleValue)
                settings.consoleUnicodeSupport = consoleUnicodeSupport
            } catch (e: IllegalArgumentException) {
                origin.handleInvalidValue(value)
            }
        }

        companion object {
            const val LONG_OPTION: String = "console-unicode"
            const val GRADLE_PROPERTY: String = "org.gradle.console.unicode"
        }
    }

    @NullMarked
    private class NonInteractiveOption : EnabledOnlyBooleanBuildOption<LoggingConfiguration>(null, CommandLineOptionConfiguration.create("non-interactive", "Do not do interactive prompting.")) {
        override fun getCategory(): OptionCategory {
            return OptionCategory.CONSOLE
        }

        override fun applyTo(settings: LoggingConfiguration, origin: Origin) {
            settings.isNonInteractive = true
        }
    }

    class WarningsOption : StringBuildOption<LoggingConfiguration?>(
        GRADLE_PROPERTY,
        CommandLineOptionConfiguration.create(LONG_OPTION, "Specifies which mode of warnings to generate. Supported values are 'all', 'fail', 'summary' (default), or 'none'.")
    ) {
        override fun applyTo(value: String, settings: LoggingConfiguration, origin: Origin) {
            try {
                settings.warningMode = WarningMode.valueOf(TextUtil.capitalize(value.lowercase())!!)
            } catch (e: IllegalArgumentException) {
                origin.handleInvalidValue(value)
            }
        }

        override fun getCategory(): OptionCategory {
            return OptionCategory.DIAGNOSTICS
        }

        companion object {
            const val LONG_OPTION: String = "warning-mode"
            const val GRADLE_PROPERTY: String = "org.gradle.warning.mode"
        }
    }
}
