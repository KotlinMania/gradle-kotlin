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
package org.gradle.process.internal

import org.gradle.internal.file.PathToFileResolver
import org.gradle.process.BaseExecSpec
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.process.ExecSpec
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

class DefaultExecSpec @Inject constructor(resolver: PathToFileResolver?) : DefaultProcessForkOptions(resolver), ExecSpec, ProcessArgumentsSpec.HasExecutable {
    private var ignoreExitValue = false
    private val streamsSpec = ProcessStreamsSpec()
    private val argumentsSpec = ProcessArgumentsSpec(this)

    fun copyTo(targetSpec: ExecSpec) {
        // Fork options
        super.copyTo(targetSpec)
        // BaseExecSpec
        copyBaseExecSpecTo(this, targetSpec)
        // ExecSpec
        targetSpec.setArgs(getArgs())
        targetSpec.getArgumentProviders().addAll(getArgumentProviders()!!)
    }

    override fun getCommandLine(): MutableList<String?>? {
        return argumentsSpec.getCommandLine()
    }

    override fun commandLine(vararg arguments: Any?): ExecSpec {
        argumentsSpec.commandLine(*arguments)
        return this
    }

    override fun commandLine(args: Iterable<*>?): ExecSpec {
        argumentsSpec.commandLine(args)
        return this
    }

    override fun setCommandLine(args: MutableList<String?>?) {
        argumentsSpec.commandLine(args)
    }

    override fun setCommandLine(vararg args: Any?) {
        argumentsSpec.commandLine(*args)
    }

    override fun setCommandLine(args: Iterable<*>?) {
        argumentsSpec.commandLine(args)
    }

    override fun args(vararg args: Any?): ExecSpec {
        argumentsSpec.args(*args)
        return this
    }

    override fun args(args: Iterable<*>?): ExecSpec {
        argumentsSpec.args(args)
        return this
    }

    override fun setArgs(arguments: MutableList<String?>?): ExecSpec {
        argumentsSpec.setArgs(arguments)
        return this
    }

    override fun setArgs(arguments: Iterable<*>?): ExecSpec {
        argumentsSpec.setArgs(arguments)
        return this
    }

    override fun getArgs(): MutableList<String?>? {
        return argumentsSpec.getArgs()
    }

    override fun getArgumentProviders(): MutableList<CommandLineArgumentProvider?>? {
        return argumentsSpec.getArgumentProviders()
    }

    override fun setIgnoreExitValue(ignoreExitValue: Boolean): ExecSpec {
        this.ignoreExitValue = ignoreExitValue
        return this
    }

    override fun isIgnoreExitValue(): Boolean {
        return ignoreExitValue
    }

    override fun setStandardInput(inputStream: InputStream?): BaseExecSpec {
        streamsSpec.setStandardInput(inputStream)
        return this
    }

    override fun getStandardInput(): InputStream? {
        return streamsSpec.getStandardInput()
    }

    override fun setStandardOutput(outputStream: OutputStream?): BaseExecSpec {
        streamsSpec.setStandardOutput(outputStream)
        return this
    }

    override fun getStandardOutput(): OutputStream? {
        return streamsSpec.getStandardOutput()
    }

    override fun setErrorOutput(outputStream: OutputStream?): BaseExecSpec {
        streamsSpec.setErrorOutput(outputStream)
        return this
    }

    override fun getErrorOutput(): OutputStream? {
        return streamsSpec.getErrorOutput()
    }

    companion object {
        fun copyBaseExecSpecTo(source: BaseExecSpec, target: BaseExecSpec) {
            target.setIgnoreExitValue(source.isIgnoreExitValue())
            if (source.getStandardInput() != null) {
                target.setStandardInput(source.getStandardInput())
            }
            if (source.getStandardOutput() != null) {
                target.setStandardOutput(source.getStandardOutput())
            }
            if (source.getErrorOutput() != null) {
                target.setErrorOutput(source.getErrorOutput())
            }
        }
    }
}
