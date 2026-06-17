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

import org.gradle.process.BaseExecSpec
import org.gradle.process.internal.streams.StreamsHandler
import java.io.InputStream
import java.io.OutputStream

/**
 * Deprecated. Will be removed in Gradle 10. Kept for now it's subclass is used by the Kotlin plugin.
 */
@Deprecated("")
abstract class AbstractExecHandleBuilder internal constructor(protected val delegate: ClientExecHandleBuilder) : BaseExecSpec {
    private var ignoreExitValue = false

    abstract val allArguments: MutableList<String?>?

    override fun getCommandLine(): MutableList<String?> {
        val commandLine: MutableList<String?> = ArrayList<String?>()
        commandLine.add(getExecutable())
        commandLine.addAll(this.allArguments!!)
        return commandLine
    }

    override fun setStandardInput(inputStream: InputStream): AbstractExecHandleBuilder? {
        delegate.setStandardInput(inputStream)
        return this
    }

    override fun getStandardInput(): InputStream {
        return delegate.getStandardInput()
    }

    override fun setStandardOutput(outputStream: OutputStream): AbstractExecHandleBuilder? {
        delegate.setStandardOutput(outputStream)
        return this
    }

    override fun getStandardOutput(): OutputStream {
        return delegate.getStandardOutput()
    }

    override fun setErrorOutput(outputStream: OutputStream): AbstractExecHandleBuilder {
        delegate.setErrorOutput(outputStream)
        return this
    }

    override fun getErrorOutput(): OutputStream {
        return delegate.getErrorOutput()
    }

    override fun isIgnoreExitValue(): Boolean {
        return ignoreExitValue
    }

    override fun setIgnoreExitValue(ignoreExitValue: Boolean): AbstractExecHandleBuilder? {
        this.ignoreExitValue = ignoreExitValue
        return this
    }

    open fun setDisplayName(displayName: String?): AbstractExecHandleBuilder? {
        delegate.setDisplayName(displayName)
        return this
    }

    open fun listener(listener: ExecHandleListener): AbstractExecHandleBuilder? {
        delegate.listener(listener)
        return this
    }

    open fun streamsHandler(streamsHandler: StreamsHandler): AbstractExecHandleBuilder? {
        delegate.streamsHandler(streamsHandler)
        return this
    }

    /**
     * Merge the process' error stream into its output stream
     */
    open fun redirectErrorStream(): AbstractExecHandleBuilder? {
        delegate.redirectErrorStream()
        return this
    }

    open fun setTimeout(timeoutMillis: Int): AbstractExecHandleBuilder? {
        delegate.setTimeout(timeoutMillis)
        return this
    }

    fun build(): ExecHandle {
        return delegate.build()
    }
}
