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
package org.gradle.launcher.cli

import com.google.common.annotations.VisibleForTesting
import org.gradle.api.Action
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.logging.configuration.LoggingConfiguration
import org.gradle.launcher.bootstrap.ExecutionListener
import java.util.Objects

internal class DebugLoggerWarningAction @VisibleForTesting constructor(
    logger: Logger,
    loggingConfiguration: LoggingConfiguration,
    action: Action<ExecutionListener>
) : Action<ExecutionListener> {
    private val logger: Logger
    private val loggingConfiguration: LoggingConfiguration
    private val action: Action<ExecutionListener>

    constructor(
        loggingConfiguration: LoggingConfiguration,
        action: Action<ExecutionListener>
    ) : this(Logging.getLogger(DebugLoggerWarningAction::class.java), loggingConfiguration, action)

    init {
        this.logger = Objects.requireNonNull<Logger>(logger, "logger")
        this.loggingConfiguration = Objects.requireNonNull<LoggingConfiguration>(loggingConfiguration, "loggingConfiguration")
        this.action = Objects.requireNonNull<Action<ExecutionListener>>(action, "action")
    }

    private fun logWarningIfEnabled() {
        if (LogLevel.DEBUG == loggingConfiguration.logLevel) {
            logger.lifecycle(WARNING_MESSAGE_BODY)
        }
    }

    override fun execute(executionListener: ExecutionListener) {
        // Add to the top of the log file.
        logWarningIfEnabled()
        try {
            action.execute(executionListener)
        } finally {
            // Add again to the bottom of the log file.
            logWarningIfEnabled()
        }
    }

    companion object {
        val WARNING_MESSAGE_BODY: String = "\n" +
                "#############################################################################\n" +
                "   WARNING WARNING WARNING WARNING WARNING WARNING WARNING WARNING WARNING\n" +
                "\n" +
                "   Debug level logging will leak security sensitive information!\n" +
                "\n" +
                "   " + DocumentationRegistry().getDocumentationRecommendationFor("details", "logging", "sec:debug_security") + "\n" +
                "#############################################################################\n"
    }
}
