/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.process.internal.streams.StreamsHandler
import org.gradle.process.CommandLineArgumentProvider
import org.jspecify.annotations.NullMarked
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * TODO: Rename to ExecHandleBuilder and remove current ExecHandleBuilder in Gradle 10
 */
@NullMarked
interface ClientExecHandleBuilder : BaseExecHandleBuilder {
    fun commandLine(args: Iterable<*>): ClientExecHandleBuilder?

    fun commandLine(vararg args: Any): ClientExecHandleBuilder?

    fun setStandardInput(inputStream: InputStream): ClientExecHandleBuilder?

    override fun setStandardOutput(outputStream: OutputStream): ClientExecHandleBuilder?

    override fun setErrorOutput(outputStream: OutputStream): ClientExecHandleBuilder?

    fun redirectErrorStream(): ClientExecHandleBuilder?

    override fun setDisplayName(displayName: String?): ClientExecHandleBuilder?

    fun setDaemon(daemon: Boolean): ClientExecHandleBuilder?

    fun streamsHandler(streamsHandler: StreamsHandler): ClientExecHandleBuilder?

    fun setTimeout(timeoutMillis: Int): ClientExecHandleBuilder?

    fun getEnvironment(): MutableMap<String, Any>

    fun setEnvironment(environmentVariables: MutableMap<String, *>)

    fun environment(key: String, value: Any): ClientExecHandleBuilder?

    fun args(vararg args: Any): ClientExecHandleBuilder?

    fun args(args: Iterable<*>): ClientExecHandleBuilder?

    fun getArgs(): MutableList<String>

    fun setArgs(args: Iterable<*>): ClientExecHandleBuilder?

    fun setExecutable(executable: String): ClientExecHandleBuilder?

    fun setExecutable(executable: Any)

    var executable: String?

    fun getWorkingDir(): File

    fun setWorkingDir(dir: Any?): ClientExecHandleBuilder?

    fun setWorkingDir(dir: File?): ClientExecHandleBuilder?

    fun getErrorOutput(): OutputStream

    fun getCommandLine(): MutableList<String>

    fun getStandardOutput(): OutputStream

    fun getAllArguments(): MutableList<String>

    fun getArgumentProviders(): MutableList<CommandLineArgumentProvider>

    fun environment(environmentVariables: MutableMap<String, *>)

    fun getStandardInput(): InputStream

    fun buildWithEffectiveArguments(effectiveArguments: MutableList<String>): ExecHandle
}
