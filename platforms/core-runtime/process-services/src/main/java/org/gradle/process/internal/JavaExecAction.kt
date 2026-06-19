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

import org.gradle.process.BaseExecSpec
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.process.ExecResult
import org.gradle.process.ProcessForkOptions
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Use [ExecActionFactory] (for core code) or [org.gradle.process.ExecOperations] (for plugin code) instead.
 *
 * TODO: We should remove setters and have abstract getters in Gradle 10 and configure builder in execute() method.
 */
class DefaultExecAction(private val execHandleBuilder: ClientExecHandleBuilder) : ExecAction {
    private var ignoreExitValue = false

    override fun execute(): ExecResult {
        val execHandle = execHandleBuilder.build()!!
        val execResult = execHandle.start().waitForFinish()
        if (!isIgnoreExitValue()) {
            execResult.assertNormalExitValue()
        }
        return execResult
    }

    override fun getExecutable(): String {
        return execHandleBuilder.executable!!
    }

    override fun setExecutable(executable: String) {
        execHandleBuilder.setExecutable(executable)
    }

    override fun setExecutable(executable: Any) {
        execHandleBuilder.setExecutable(executable)
    }

    override fun executable(executable: Any): ProcessForkOptions {
        execHandleBuilder.setExecutable(executable)
        return this
    }

    override fun getWorkingDir(): File? {
        return execHandleBuilder.getWorkingDir()
    }

    override fun setWorkingDir(dir: File?) {
        execHandleBuilder.setWorkingDir(dir)
    }

    override fun setWorkingDir(dir: Any?) {
        execHandleBuilder.setWorkingDir(dir)
    }

    override fun commandLine(vararg arguments: Any): ExecAction {
        execHandleBuilder.commandLine(*arguments)
        return this
    }

    override fun commandLine(args: Iterable<*>): ExecAction {
        execHandleBuilder.commandLine(args)
        return this
    }

    override fun setCommandLine(args: MutableList<String>) {
        execHandleBuilder.commandLine(args)
    }

    override fun setCommandLine(vararg args: Any) {
        execHandleBuilder.commandLine(*args)
    }

    override fun setCommandLine(args: Iterable<*>) {
        execHandleBuilder.commandLine(args)
    }

    override fun args(vararg args: Any): ExecAction {
        execHandleBuilder.args(*args)
        return this
    }

    override fun args(args: Iterable<*>): ExecAction {
        execHandleBuilder.args(args)
        return this
    }

    override fun setArgs(arguments: MutableList<String>): ExecAction {
        execHandleBuilder.setArgs(arguments)
        return this
    }

    override fun setArgs(arguments: Iterable<*>): ExecAction {
        execHandleBuilder.setArgs(arguments)
        return this
    }

    override fun getArgs(): MutableList<String> {
        return execHandleBuilder.getArgs()
    }

    override fun getArgumentProviders(): MutableList<CommandLineArgumentProvider> {
        return execHandleBuilder.getArgumentProviders()
    }

    override fun setIgnoreExitValue(ignoreExitValue: Boolean): ExecAction {
        this.ignoreExitValue = ignoreExitValue
        return this
    }

    override fun isIgnoreExitValue(): Boolean {
        return ignoreExitValue
    }

    override fun setStandardInput(inputStream: InputStream): ExecAction {
        execHandleBuilder.setStandardInput(inputStream)
        return this
    }

    override fun workingDir(dir: Any?): ExecAction {
        execHandleBuilder.setWorkingDir(dir)
        return this
    }

    override fun getEnvironment(): MutableMap<String, Any> {
        return execHandleBuilder.getEnvironment()
    }

    override fun setEnvironment(environmentVariables: MutableMap<String, *>) {
        execHandleBuilder.setEnvironment(environmentVariables)
    }

    override fun environment(environmentVariables: MutableMap<String, *>): ExecAction {
        execHandleBuilder.environment(environmentVariables)
        return this
    }

    override fun environment(name: String, value: Any): ExecAction {
        execHandleBuilder.environment(name, value)
        return this
    }

    override fun getStandardOutput(): OutputStream {
        return execHandleBuilder.getStandardOutput()
    }

    override fun setErrorOutput(outputStream: OutputStream): BaseExecSpec {
        execHandleBuilder.setErrorOutput(outputStream)
        return this
    }

    override fun getErrorOutput(): OutputStream {
        return execHandleBuilder.getErrorOutput()
    }

    override fun getCommandLine(): MutableList<String> {
        return execHandleBuilder.getCommandLine()
    }

    override fun getStandardInput(): InputStream {
        return execHandleBuilder.getStandardInput()
    }

    override fun setStandardOutput(outputStream: OutputStream): ExecAction {
        execHandleBuilder.setStandardOutput(outputStream)
        return this
    }

    override fun listener(listener: ExecHandleListener?): ExecAction {
        if (listener != null) {
            execHandleBuilder.listener(listener)
        }
        return this
    }

    override fun copyTo(options: ProcessForkOptions): ExecAction {
        throw UnsupportedOperationException("Copy to ProcessForkOptions is not supported for ExecAction")
    }
}
