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
package org.gradle.internal.logging

import org.apache.commons.lang3.builder.EqualsBuilder
import org.apache.commons.lang3.builder.HashCodeBuilder
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.configuration.ConsoleOutput
import org.gradle.api.logging.configuration.ConsoleUnicodeSupport
import org.gradle.api.logging.configuration.LoggingConfiguration
import org.gradle.api.logging.configuration.ShowStacktrace
import org.gradle.api.logging.configuration.WarningMode
import java.io.Serializable

class DefaultLoggingConfiguration : Serializable, LoggingConfiguration {
    override var logLevel: LogLevel? = LogLevel.LIFECYCLE
    override var showStacktrace: ShowStacktrace? = ShowStacktrace.INTERNAL_EXCEPTIONS
    override var consoleOutput: ConsoleOutput? = ConsoleOutput.Auto
    override var consoleUnicodeSupport: ConsoleUnicodeSupport? = ConsoleUnicodeSupport.Auto
    override var isNonInteractive = false
    override var warningMode: WarningMode? = WarningMode.Summary

    override fun equals(obj: Any?): Boolean {
        return EqualsBuilder.reflectionEquals(this, obj)
    }

    override fun hashCode(): Int {
        return HashCodeBuilder.reflectionHashCode(this)
    }

}
