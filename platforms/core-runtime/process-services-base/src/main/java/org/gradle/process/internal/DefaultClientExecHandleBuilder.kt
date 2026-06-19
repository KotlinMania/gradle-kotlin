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

import com.google.common.collect.Maps
import org.gradle.initialization.BuildCancellationToken
import org.gradle.internal.file.PathToFileResolver
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.process.internal.streams.EmptyStdInStreamsHandler
import org.gradle.process.internal.streams.ForwardStdinStreamsHandler
import org.gradle.process.internal.streams.OutputStreamsForwarder
import org.gradle.process.internal.streams.SafeStreams
import org.gradle.process.internal.streams.StreamsHandler
import org.jspecify.annotations.NullMarked
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.Objects
import java.util.concurrent.Executor

@NullMarked
open class DefaultClientExecHandleBuilder(private val fileResolver: PathToFileResolver, private val executor: Executor, private val buildCancellationToken: BuildCancellationToken) :
    ClientExecHandleBuilder, ProcessArgumentsSpec.HasExecutable {
    private val listeners: MutableList<ExecHandleListener>
    private val streamsSpec: ProcessStreamsSpec
    private val argumentsSpec: ProcessArgumentsSpec

    private var environment: MutableMap<String, Any>? = null
    private var inputHandler: StreamsHandler = DEFAULT_STDIN
    private var displayName: String? = null
    private var redirectErrorStream = false
    private var streamsHandler: StreamsHandler? = null
    private var timeoutMillis = Int.MAX_VALUE
    protected var daemon: Boolean = false
    override var executable: String? = null
    private var workingDir: File? = null

    init {
        this.listeners = ArrayList<ExecHandleListener>()
        this.argumentsSpec = ProcessArgumentsSpec(this)
        this.streamsSpec = ProcessStreamsSpec()
        streamsSpec.setStandardOutput(SafeStreams.systemOut())
        streamsSpec.setErrorOutput(SafeStreams.systemErr())
        streamsSpec.setStandardInput(SafeStreams.emptyInput())
    }

    override fun commandLine(args: Iterable<*>): ClientExecHandleBuilder {
        argumentsSpec.commandLine(args)
        return this
    }

    override fun commandLine(vararg args: Any): ClientExecHandleBuilder {
        argumentsSpec.commandLine(*args)
        return this
    }

    override fun setStandardInput(inputStream: InputStream): ClientExecHandleBuilder {
        streamsSpec.setStandardInput(inputStream)
        this.inputHandler = ForwardStdinStreamsHandler(inputStream)
        return this
    }

    override fun setStandardOutput(outputStream: OutputStream): ClientExecHandleBuilder {
        streamsSpec.setStandardOutput(outputStream)
        return this
    }

    override fun setErrorOutput(outputStream: OutputStream): ClientExecHandleBuilder {
        streamsSpec.setErrorOutput(outputStream)
        return this
    }

    override fun redirectErrorStream(): ClientExecHandleBuilder {
        this.redirectErrorStream = true
        return this
    }

    override fun setDisplayName(displayName: String?): ClientExecHandleBuilder {
        this.displayName = displayName
        return this
    }

    override fun setDaemon(daemon: Boolean): ClientExecHandleBuilder {
        this.daemon = daemon
        return this
    }

    override fun streamsHandler(streamsHandler: StreamsHandler): ClientExecHandleBuilder {
        this.streamsHandler = streamsHandler
        return this
    }

    override fun setTimeout(timeoutMillis: Int): ClientExecHandleBuilder {
        this.timeoutMillis = timeoutMillis
        return this
    }

    override fun args(vararg args: Any): ClientExecHandleBuilder {
        argumentsSpec.args(*args)
        return this
    }

    override fun args(args: Iterable<*>): ClientExecHandleBuilder {
        argumentsSpec.args(args)
        return this
    }

    override fun getArgs(): MutableList<String> {
        return argumentsSpec.args
    }

    override fun setArgs(args: Iterable<*>): ClientExecHandleBuilder {
        argumentsSpec.setArgs(args)
        return this
    }

    override fun setExecutable(executable: Any) {
        setExecutable(Objects.toString(executable))
    }

    override fun setExecutable(executable: String): ClientExecHandleBuilder {
        this.executable = executable
        return this
    }

    override fun listener(listener: ExecHandleListener): ClientExecHandleBuilder {
        listeners.add(listener)
        return this
    }

    override fun getErrorOutput(): OutputStream {
        return streamsSpec.errorOutput!!
    }

    override fun getCommandLine(): MutableList<String> {
        return argumentsSpec.commandLine
    }

    override fun getStandardOutput(): OutputStream {
        return streamsSpec.standardOutput!!
    }

    override fun getAllArguments(): MutableList<String> {
        return argumentsSpec.allArguments
    }

    override fun getArgumentProviders(): MutableList<CommandLineArgumentProvider> {
        return argumentsSpec.argumentProviders
    }

    override fun getEnvironment(): MutableMap<String, Any> {
        if (environment == null) {
            setEnvironment(System.getenv())
        }
        return environment!!
    }

    override fun environment(key: String, value: Any): ClientExecHandleBuilder {
        getEnvironment().put(key, value)
        return this
    }

    override fun setEnvironment(environmentVariables: MutableMap<String, *>) {
        val newEnvironment = Maps.newHashMap<String, Any>()
        for (entry in environmentVariables.entries) {
            newEnvironment[entry.key] = entry.value as Any
        }
        environment = newEnvironment
    }

    override fun environment(environmentVariables: MutableMap<String, *>) {
        for (entry in environmentVariables.entries) {
            getEnvironment()[entry.key] = entry.value as Any
        }
    }

    override fun getStandardInput(): InputStream {
        return streamsSpec.standardInput!!
    }

    override fun getWorkingDir(): File {
        if (workingDir == null) {
            workingDir = fileResolver.resolve(".")
        }
        return workingDir!!
    }

    override fun setWorkingDir(dir: File?): ClientExecHandleBuilder {
        this.workingDir = if (dir == null) null else fileResolver.resolve(dir)
        return this
    }

    override fun setWorkingDir(dir: Any?): ClientExecHandleBuilder {
        this.workingDir = if (dir == null) null else fileResolver.resolve(dir)
        return this
    }

    override fun build(): ExecHandle {
        return buildWithEffectiveArguments(argumentsSpec.allArguments)
    }

    override fun buildWithEffectiveArguments(effectiveArguments: MutableList<String>): ExecHandle {
        val displayName: String? = if (this.displayName == null) String.format("command '%s'", executable) else this.displayName
        val effectiveEnvironment: MutableMap<String, String> = getEffectiveEnvironment(getEnvironment())
        val effectiveOutputHandler: StreamsHandler = getEffectiveStreamsHandler(streamsHandler, streamsSpec, redirectErrorStream)
        return DefaultExecHandle(
            displayName,
            getWorkingDir(),
            executable!!,
            effectiveArguments,
            effectiveEnvironment,
            effectiveOutputHandler,
            inputHandler,
            listeners,
            redirectErrorStream,
            timeoutMillis,
            daemon,
            executor,
            buildCancellationToken
        )
    }

    companion object {
        private val DEFAULT_STDIN = EmptyStdInStreamsHandler()

        private fun getEffectiveEnvironment(environment: MutableMap<String, Any>): MutableMap<String, String> {
            val effectiveEnvironment: MutableMap<String, String> = Maps.newLinkedHashMapWithExpectedSize<String, String>(environment.size)
            for (entry in environment.entries) {
                effectiveEnvironment.put(entry.key, entry.value.toString())
            }
            return effectiveEnvironment
        }

        private fun getEffectiveStreamsHandler(streamsHandler: StreamsHandler?, streamsSpec: ProcessStreamsSpec, redirectErrorStream: Boolean): StreamsHandler {
            if (streamsHandler != null) {
                return streamsHandler
            }
            val shouldReadErrorStream = !redirectErrorStream
            return OutputStreamsForwarder(
                streamsSpec.standardOutput!!,
                streamsSpec.errorOutput!!,
                shouldReadErrorStream
            )
        }
    }
}
