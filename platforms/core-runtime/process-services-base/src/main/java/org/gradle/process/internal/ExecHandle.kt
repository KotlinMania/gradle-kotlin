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
package org.gradle.process.internal

import org.gradle.api.Describable
import org.gradle.process.ExecResult
import java.io.File

interface ExecHandle : Describable {
    fun getDirectory(): File

    fun getCommand(): String

    fun getArguments(): MutableList<String>

    fun getEnvironment(): MutableMap<String, String>

    /**
     * Starts this process, blocking until the process has started.
     *
     * @return this
     */
    fun start(): ExecHandle

    fun removeStartupContext()

    fun getState(): ExecHandleState

    /**
     * Sends the given signal to the process.
     *
     * @throws UnsupportedOperationException if called on Windows
     * @throws IllegalStateException if the process has not started yet
     */
    fun sendSignal(signal: Int)

    /**
     * Aborts the process, blocking until the process has exited. Does nothing if the process has already completed.
     */
    fun abort()

    /**
     * Waits for the process to finish. Returns immediately if the process has already completed.
     *
     * @return result
     */
    fun waitForFinish(): ExecResult

    /**
     * Returns the result of the process execution, if it has finished.  If the process has not finished, returns null.
     */
    fun getExecResult(): ExecResult?

    fun addListener(listener: ExecHandleListener)

    fun removeListener(listener: ExecHandleListener)
}
