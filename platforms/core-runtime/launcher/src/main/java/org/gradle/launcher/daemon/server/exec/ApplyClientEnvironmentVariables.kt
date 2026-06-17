/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.launcher.daemon.server.exec

import com.google.common.annotations.VisibleForTesting
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.internal.nativeintegration.ProcessEnvironment
import org.gradle.launcher.daemon.protocol.Build
import org.gradle.launcher.daemon.server.api.DaemonCommandExecution
import org.jspecify.annotations.NullMarked

/**
 * Applies the environment variables specified by the client to the daemon JVM, and restores the
 * previous values when the build finishes.
 *
 *
 * This action runs *after* [LogToClient] so that the warning logged when the daemon
 * cannot mutate its process environment (typically because the native integration is unavailable, e.g.
 * when `~/.gradle/native/` is on a `noexec` filesystem) is forwarded to the client and
 * surfaces in the build's standard output rather than being buried in the daemon log.
 *
 *
 * Sibling action [EstablishBuildEnvironment] handles system properties and the working
 * directory and runs *before* `LogToClient` so that properties such as
 * [LogToClient.DISABLE_OUTPUT] are visible to `LogToClient` when it decides whether to
 * forward output.
 */
@NullMarked
class ApplyClientEnvironmentVariables @VisibleForTesting internal constructor(private val processEnvironment: ProcessEnvironment, private val logger: Logger) : BuildCommandOnly() {
    constructor(processEnvironment: ProcessEnvironment) : this(processEnvironment, DEFAULT_LOGGER)

    override fun doBuild(execution: DaemonCommandExecution, build: Build) {
        val originalEnv: MutableMap<String, String> = HashMap<String, String>(System.getenv())

        // Log only the variable names and not their values. Environment variables often contain sensitive data that should not be leaked to log files.
        logger.debug("Configuring env variables: {}", build.getParameters().getEnvVariables().keys)

        val setEnvironmentResult = processEnvironment.maybeSetEnvironment(build.getParameters().getEnvVariables())
        if (!setEnvironmentResult.isSuccess()) {
            logger.warn(
                ("Warning: Unable to set daemon's environment variables to match the client because: "
                        + System.getProperty("line.separator") + "  "
                        + setEnvironmentResult
                        + System.getProperty("line.separator") + "  "
                        + "If the daemon was started with a significantly different environment from the client, and your build "
                        + System.getProperty("line.separator") + "  "
                        + "relies on environment variables, you may experience unexpected behavior.")
            )
        }

        try {
            execution.proceed()
        } finally {
            processEnvironment.maybeSetEnvironment(originalEnv)
        }
    }

    companion object {
        private val DEFAULT_LOGGER: Logger = Logging.getLogger(ApplyClientEnvironmentVariables::class.java)
    }
}
