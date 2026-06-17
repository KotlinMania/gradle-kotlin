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

import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.LoggingManager
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope

/**
 * Provides access to and control over the logging system. Log manager represents some 'scope', and log managers can be nested in a stack.
 */
@ServiceScope(Scope.Global::class, Scope.Project::class)
interface LoggingManagerInternal : LoggingManager, StandardOutputCapture, LoggingOutputInternal {
    /**
     * Makes this log manager active, replacing the currently active log manager, if any. Applies any settings defined by this log manager. Initialises the logging system when there is no log manager currently active.
     *
     *
     * While a log manager is active, any changes made to the settings will take effect immediately. When a log manager is not active, changes to its settings will apply only once it is made active by calling [.start].
     */
    override fun start(): LoggingManagerInternal?

    /**
     * Stops logging, restoring the log manger that was active when [.start] was called on this manager. Shuts down the logging system when there was no log manager active prior to starting this one.
     */
    override fun stop(): LoggingManagerInternal?

    /**
     * Consumes logging from System.out and System.err and Java util logging.
     */
    fun captureSystemSources(): LoggingManagerInternal?

    /**
     * Sets the log level to capture stdout at. Does not enable capture.
     */
    override fun captureStandardOutput(level: LogLevel?): LoggingManagerInternal?

    /**
     * Sets the log level to capture stderr at. Does not enable capture.
     */
    override fun captureStandardError(level: LogLevel?): LoggingManagerInternal?

    fun setLevelInternal(logLevel: LogLevel?): LoggingManagerInternal?

    /**
     * Allows [org.gradle.api.logging.LoggingOutput.addStandardOutputListener] and [org.gradle.api.logging.LoggingOutput.addStandardErrorListener] to be used.
     *
     *
     * This should be used only when custom user listeners are required, i.e. only in the build JVM around the build execution.
     */
    fun enableUserStandardOutputListeners(): LoggingManagerInternal?
}
