/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.nativeplatform.toolchain.internal

import com.google.common.base.Joiner
import org.gradle.internal.io.StreamByteBuffer
import org.gradle.internal.operations.BuildOperationContext
import org.gradle.internal.os.OperatingSystem
import org.gradle.process.ProcessExecutionException
import org.gradle.process.internal.ExecActionFactory
import org.gradle.util.internal.GFileUtils
import org.jspecify.annotations.NullMarked
import java.io.File
import java.nio.charset.Charset

@NullMarked
class DefaultCommandLineToolInvocationWorker(private val name: String, private val executable: File, private val execActionFactory: ExecActionFactory) : CommandLineToolInvocationWorker {
    override fun getDisplayName(): String {
        return "command line tool '" + name + "'"
    }

    override fun toString(): String {
        return getDisplayName()
    }

    override fun execute(invocation: CommandLineToolInvocation, context: BuildOperationContext) {
        val description = invocation.description().build()
        val toolExec = execActionFactory.newExecAction()

        toolExec!!.executable(executable)
        if (invocation.getWorkDirectory() != null) {
            GFileUtils.mkdirs(invocation.getWorkDirectory())
            toolExec.workingDir(invocation.getWorkDirectory())
        }

        toolExec.args(invocation.getArgs())

        if (!invocation.getPath().isEmpty()) {
            val pathVar = OperatingSystem.current()!!.pathVar
            var toolPath = Joiner.on(File.pathSeparator).join(invocation.getPath())
            toolPath = toolPath + File.pathSeparator + System.getenv(pathVar)
            toolExec.environment(pathVar, toolPath)
            if (OperatingSystem.current()!!.isWindows && toolExec.getEnvironment().containsKey(pathVar.uppercase())) {
                toolExec.getEnvironment().remove(pathVar.uppercase())
            }
        }

        toolExec.environment(invocation.getEnvironment())

        val errOutput = StreamByteBuffer()
        val stdOutput = StreamByteBuffer()
        toolExec.setErrorOutput(errOutput.outputStream)
        toolExec.setStandardOutput(stdOutput.outputStream)

        try {
            toolExec.execute()
            invocation.getLogger().operationSuccess(description.getDisplayName(), combineOutput(stdOutput, errOutput))
        } catch (e: ProcessExecutionException) {
            invocation.getLogger().operationFailed(description.getDisplayName(), combineOutput(stdOutput, errOutput))
            throw CommandLineToolInvocationFailure(invocation, String.format("%s failed while %s.", name, description.getDisplayName()))
        }
    }

    private fun combineOutput(stdOutput: StreamByteBuffer, errOutput: StreamByteBuffer): String {
        return stdOutput.readAsString(Charset.defaultCharset()) + errOutput.readAsString(Charset.defaultCharset())
    }
}
