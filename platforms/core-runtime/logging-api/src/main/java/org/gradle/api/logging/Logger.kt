/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.logging

import org.slf4j.Logger

/**
 *
 * An extension to the SLF4J `Logger` interface, which adds the `quiet` and `lifecycle` log
 * levels.
 *
 *
 * You can obtain a `Logger` instance using [Logging.getLogger] or [ ][Logging.getLogger]. A `Logger` instance is also available through [ ][org.gradle.api.Project.getLogger], [org.gradle.api.Task.getLogger] and [ ][org.gradle.api.Script.getLogger].
 * <br></br>
 *
 * **CAUTION!**
 * Logging sensitive information (credentials, tokens, certain environment variables) above [debug] level is a security vulnerability.
 * See [our recommendations](https://docs.gradle.org/current/userguide/logging.html#sec:debug_security) for more information.
 *
 */
interface Logger : Logger {
    /**
     * Returns true if lifecycle log level is enabled for this logger.
     */
    val isLifecycleEnabled: Boolean

    /**
     * Multiple-parameters friendly debug method
     *
     * @param message the log message
     * @param objects the log message parameters
     */
    override fun debug(message: String?, vararg objects: Any?)

    /**
     * Logs the given message at lifecycle log level.
     *
     * @param message the log message.
     */
    fun lifecycle(message: String?)

    /**
     * Logs the given message at lifecycle log level.
     *
     * @param message the log message.
     * @param objects the log message parameters.
     */
    fun lifecycle(message: String?, vararg objects: Any?)

    /**
     * Logs the given message at lifecycle log level.
     *
     * @param message the log message.
     * @param throwable the exception to log.
     */
    fun lifecycle(message: String?, throwable: Throwable?)

    /**
     * Returns true if quiet log level is enabled for this logger.
     */
    val isQuietEnabled: Boolean

    /**
     * Logs the given message at quiet log level.
     *
     * @param message the log message.
     */
    fun quiet(message: String?)

    /**
     * Logs the given message at quiet log level.
     *
     * @param message the log message.
     * @param objects the log message parameters.
     */
    fun quiet(message: String?, vararg objects: Any?)

    /**
     * Logs the given message at info log level.
     *
     * @param message the log message.
     * @param objects the log message parameters.
     */
    override fun info(message: String?, vararg objects: Any?)

    /**
     * Logs the given message at quiet log level.
     *
     * @param message the log message.
     * @param throwable the exception to log.
     */
    fun quiet(message: String?, throwable: Throwable?)

    /**
     * Returns true if the given log level is enabled for this logger.
     */
    fun isEnabled(level: LogLevel?): Boolean

    /**
     * Logs the given message at the given log level.
     *
     * @param level the log level.
     * @param message the log message.
     */
    fun log(level: LogLevel?, message: String?)

    /**
     * Logs the given message at the given log level.
     *
     * @param level the log level.
     * @param message the log message.
     * @param objects the log message parameters.
     */
    fun log(level: LogLevel?, message: String?, vararg objects: Any?)

    /**
     * Logs the given message at the given log level.
     *
     * @param level the log level.
     * @param message the log message.
     * @param throwable the exception to log.
     */
    fun log(level: LogLevel?, message: String?, throwable: Throwable?)
}
