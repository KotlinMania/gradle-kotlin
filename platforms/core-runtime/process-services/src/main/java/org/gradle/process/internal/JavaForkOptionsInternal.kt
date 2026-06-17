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

import org.gradle.process.CommandLineArgumentProvider
import org.gradle.process.ProcessForkOptions
import org.gradle.process.internal.streams.StreamsHandler
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Deprecated. Use [ClientExecHandleBuilder] instead. Kept for now since it's used by the Kotlin plugin.
 *
 * Can be merged with [ClientExecHandleBuilder] in Gradle 10.
 */
@Deprecated("")
class DefaultExecHandleBuilder(delegate: ClientExecHandleBuilder?) : AbstractExecHandleBuilder(delegate), ExecHandleBuilder, ProcessArgumentsSpec.HasExecutable {
    override fun getExecutable(): String {
        return delegate.getExecutable()
    }

    override fun setExecutable(executable: String) {
        delegate.setExecutable(executable)
    }

    override fun setExecutable(executable: Any) {
        delegate.setExecutable(executable)
    }

    override fun executable(executable: Any): DefaultExecHandleBuilder {
        delegate.setExecutable(executable)
        return this
    }

    override fun getWorkingDir(): File? {
        return delegate.getWorkingDir()
    }

    override fun setWorkingDir(dir: File?) {
        delegate.setWorkingDir(dir)
    }

    override fun setWorkingDir(dir: Any?) {
        delegate.setWorkingDir(dir)
    }

    override fun commandLine(vararg arguments: Any?): DefaultExecHandleBuilder {
        delegate.commandLine(*arguments)
        return this
    }

    override fun commandLine(args: Iterable<*>): DefaultExecHandleBuilder {
        delegate.commandLine(args)
        return this
    }

    override fun setCommandLine(args: MutableList<String?>) {
        delegate.commandLine(args)
    }

    override fun setCommandLine(vararg args: Any?) {
        delegate.commandLine(*args)
    }

    override fun setCommandLine(args: Iterable<*>) {
        delegate.commandLine(args)
    }

    override fun args(vararg args: Any?): DefaultExecHandleBuilder {
        delegate.args(*args)
        return this
    }

    override fun args(args: Iterable<*>): DefaultExecHandleBuilder {
        delegate.args(args)
        return this
    }

    override fun setArgs(arguments: MutableList<String?>): DefaultExecHandleBuilder {
        delegate.setArgs(arguments)
        return this
    }

    override fun setArgs(arguments: Iterable<*>): DefaultExecHandleBuilder {
        delegate.setArgs(arguments)
        return this
    }

    override fun getArgs(): MutableList<String?> {
        return delegate.getArgs()
    }

    override fun getArgumentProviders(): MutableList<CommandLineArgumentProvider?> {
        return delegate.getArgumentProviders()
    }

    override fun getAllArguments(): MutableList<String?> {
        return delegate.getAllArguments()
    }

    override fun setIgnoreExitValue(ignoreExitValue: Boolean): DefaultExecHandleBuilder {
        super.setIgnoreExitValue(ignoreExitValue)
        return this
    }

    override fun workingDir(dir: Any?): DefaultExecHandleBuilder {
        delegate.setWorkingDir(dir)
        return this
    }

    override fun getEnvironment(): MutableMap<String?, Any?> {
        return delegate.getEnvironment()
    }

    override fun setEnvironment(environmentVariables: MutableMap<String?, *>) {
        delegate.setEnvironment(environmentVariables)
    }

    override fun environment(environmentVariables: MutableMap<String?, *>): ProcessForkOptions {
        delegate.environment(environmentVariables)
        return this
    }

    override fun environment(name: String, value: Any): ProcessForkOptions {
        delegate.environment(name, value)
        return this
    }

    override fun setDisplayName(displayName: String?): DefaultExecHandleBuilder {
        super.setDisplayName(displayName)
        return this
    }

    override fun redirectErrorStream(): DefaultExecHandleBuilder {
        super.redirectErrorStream()
        return this
    }

    override fun setStandardOutput(outputStream: OutputStream?): DefaultExecHandleBuilder {
        super.setStandardOutput(outputStream)
        return this
    }

    override fun setStandardInput(inputStream: InputStream?): DefaultExecHandleBuilder {
        super.setStandardInput(inputStream)
        return this
    }

    override fun streamsHandler(streamsHandler: StreamsHandler?): DefaultExecHandleBuilder {
        super.streamsHandler(streamsHandler)
        return this
    }

    override fun listener(listener: ExecHandleListener?): DefaultExecHandleBuilder {
        super.listener(listener)
        return this
    }

    override fun setTimeout(timeoutMillis: Int): DefaultExecHandleBuilder {
        super.setTimeout(timeoutMillis)
        return this
    }

    override fun setDaemon(daemon: Boolean): ExecHandleBuilder {
        delegate.setDaemon(daemon)
        return this
    }

    override fun copyTo(options: ProcessForkOptions): ProcessForkOptions {
        options.setExecutable(delegate.getExecutable())
        options.setWorkingDir(delegate.getWorkingDir())
        options.setEnvironment(delegate.getEnvironment())
        return this
    }
}
