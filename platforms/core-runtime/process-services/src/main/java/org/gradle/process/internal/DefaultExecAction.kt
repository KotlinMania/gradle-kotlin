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
package org.gradle.process.internal

import org.gradle.process.BaseExecSpec
import org.gradle.process.ExecSpec
import org.gradle.process.ProcessForkOptions
import org.gradle.process.internal.streams.StreamsHandler

/**
 * Deprecated. Use [ClientExecHandleBuilder] instead. Kept for now since it's used by the Kotlin plugin.
 *
 * Can be merged with [ClientExecHandleBuilder] in Gradle 10.
 */
@Deprecated("")
interface ExecHandleBuilder : ProcessForkOptions, BaseExecSpec, ExecSpec {
    /**
     * Merge the process' error stream into its output stream
     */
    fun redirectErrorStream(): ExecHandleBuilder?

    fun setDisplayName(displayName: String?): ExecHandleBuilder?

    /**
     * When true, spawn the process. That is, start the process and leave it running once successfully started. When false, fork the process (the default). That is, start the process and wait for it to complete.
     */
    fun setDaemon(daemon: Boolean): ExecHandleBuilder?

    /**
     * Sets a handler for the *output* streams of the process.
     */
    fun streamsHandler(streamsHandler: StreamsHandler?): ExecHandleBuilder?

    /**
     * Sets the start-up timeout, when spawning a process. Not used when forking a process (the default).
     */
    fun setTimeout(timeoutMillis: Int): ExecHandleBuilder?

    fun build(): ExecHandle?
}
