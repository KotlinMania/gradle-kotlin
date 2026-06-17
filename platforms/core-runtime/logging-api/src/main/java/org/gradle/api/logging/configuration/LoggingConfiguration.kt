/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.api.logging.configuration

import org.gradle.api.Incubating
import org.gradle.internal.instrumentation.api.annotations.NotToBeMigratedToLazy

/**
 * A `LoggingConfiguration` defines the logging settings for a Gradle build.
 */
@NotToBeMigratedToLazy
interface LoggingConfiguration {
    /**
     * Returns the minimum logging level to use. All log messages with a lower log level are ignored.
     * Defaults to [LogLevel.LIFECYCLE].
     */
    /**
     * Specifies the minimum logging level to use. All log messages with a lower log level are ignored.
     */
    @JvmField
    var logLevel: LogLevel?

    /**
     * Returns the style of logging output that should be written to the console.
     * Defaults to [ConsoleOutput.Auto]
     */
    /**
     * Specifies the style of logging output that should be written to the console.
     */
    var consoleOutput: ConsoleOutput?

    @get:Incubating
    @set:Incubating
    var consoleUnicodeSupport: ConsoleUnicodeSupport?

    /**
     * Specifies which type of warnings should be written to the console.
     *
     * @since 4.5
     */
    /**
     * Specifies which type of warnings should be written to the console.
     *
     * @since 4.5
     */
    var warningMode: WarningMode?

    @get:Incubating
    @set:Incubating
    var isNonInteractive: Boolean

    /**
     * Returns the detail that should be included in stacktraces. Defaults to [ShowStacktrace.INTERNAL_EXCEPTIONS].
     */
    /**
     * Sets the detail that should be included in stacktraces.
     */
    @JvmField
    var showStacktrace: ShowStacktrace?
}
