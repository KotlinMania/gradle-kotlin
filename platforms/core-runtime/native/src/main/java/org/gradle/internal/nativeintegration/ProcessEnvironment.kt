/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.internal.nativeintegration

import java.io.File

/**
 * Provides access to information about the current process.
 *
 *
 * Implementations are not thread-safe.
 */
interface ProcessEnvironment {
    /**
     * Sets the environment of this process, if possible.
     *
     * @param source The environment
     * @return true if environment changed, false if not possible.
     */
    fun maybeSetEnvironment(source: MutableMap<String?, String?>?): EnvironmentModificationResult?

    /**
     * Removes the given environment variable.
     *
     * @param name The name of the environment variable.
     * @throws NativeIntegrationException If the environment variable cannot be removed.
     */
    @Throws(NativeIntegrationException::class)
    fun removeEnvironmentVariable(name: String?)

    /**
     * Removes the given environment variable, if possible.
     *
     * @param name The name of the environment variable.
     * @return true if removed, false if not possible.
     */
    fun maybeRemoveEnvironmentVariable(name: String?): EnvironmentModificationResult?

    /**
     * Sets the given environment variable.
     *
     * @param name The name
     * @param value The value. Can be null, which removes the environment variable.
     * @throws NativeIntegrationException If the environment variable cannot be set.
     */
    @Throws(NativeIntegrationException::class)
    fun setEnvironmentVariable(name: String?, value: String?)

    /**
     * Sets the given environment variable, if possible.
     *
     * @param name The name
     * @param value The value
     * @return true if set, false if not possible.
     */
    fun maybeSetEnvironmentVariable(name: String?, value: String?): EnvironmentModificationResult?

    @get:Throws(NativeIntegrationException::class)
    @set:Throws(NativeIntegrationException::class)
    var processDir: File?

    /**
     * Sets the process working directory, if possible
     *
     * @param processDir The directory.
     * @return true if the directory can be set, false if not possible.
     */
    fun maybeSetProcessDir(processDir: File?): Boolean

    @get:Throws(NativeIntegrationException::class)
    val pid: Long?

    /**
     * Returns the OS level PID for the current process, or null if not available.
     */
    fun maybeGetPid(): Long?

    /**
     * Detaches the current process from its terminal/console to properly put it in the background, if possible.
     *
     * @return true if the process was successfully detached.
     */
    fun maybeDetachProcess(): Boolean

    /**
     * Detaches the current process from its terminal/console to properly put it in the background.
     *
     * @throws NativeIntegrationException If the process could not be detached.
     */
    fun detachProcess()
}
