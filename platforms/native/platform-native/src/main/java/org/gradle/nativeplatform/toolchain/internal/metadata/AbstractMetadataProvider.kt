/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.nativeplatform.toolchain.internal.metadata

import com.google.common.base.Joiner
import com.google.common.collect.ImmutableList
import org.gradle.api.Action
import org.gradle.internal.Pair
import org.gradle.internal.io.StreamByteBuffer
import org.gradle.platform.base.internal.toolchain.ComponentFound
import org.gradle.platform.base.internal.toolchain.ComponentNotFound
import org.gradle.platform.base.internal.toolchain.SearchResult
import org.gradle.process.internal.ExecActionFactory
import java.io.File

abstract class AbstractMetadataProvider<T : CompilerMetadata?>(protected val execActionFactory: ExecActionFactory) : CompilerMetaDataProvider<T?> {
    override fun getCompilerMetaData(path: MutableList<File?>?, configureAction: Action<in CompilerMetaDataProvider.CompilerExecSpec?>): SearchResult<T?> {
        val execSpec = DefaultCompilerExecSpec()
        execSpec.environment("LC_MESSAGES", "C")
        configureAction.execute(execSpec)

        val allArgs: MutableList<String?> = ImmutableList.builder<String?>().addAll(execSpec.args).addAll(compilerArgs()!!).build()
        val transform = runCompiler(execSpec.executable!!, allArgs, execSpec.environments)
        if (transform == null) {
            return ComponentNotFound<T?>(
                String.format(
                    "Could not determine %s metadata: failed to execute %s %s.",
                    getCompilerType().getDescription(),
                    execSpec.executable!!.getName(),
                    Joiner.on(' ').join(allArgs)
                )
            )
        }
        val output = transform.left
        val error = transform.right
        try {
            return ComponentFound<T?>(parseCompilerOutput(output, error, execSpec.executable, path))
        } catch (e: BrokenResultException) {
            return ComponentNotFound<T?>(e.message!!)
        }
    }

    @Throws(BrokenResultException::class)
    protected abstract fun parseCompilerOutput(output: String?, error: String?, binary: File?, path: MutableList<File?>?): T?

    private fun runCompiler(gccBinary: File, args: MutableList<String?>?, environmentVariables: MutableMap<String?, *>?): Pair<String?, String?>? {
        val exec = execActionFactory.newExecAction()
        exec!!.executable(gccBinary.getAbsolutePath())
        exec.setWorkingDir(gccBinary.getParentFile())
        exec.args(args)
        exec.environment(environmentVariables)
        val buffer = StreamByteBuffer()
        val errorBuffer = StreamByteBuffer()
        exec.setStandardOutput(buffer.outputStream)
        exec.setErrorOutput(errorBuffer.outputStream)
        exec.setIgnoreExitValue(true)
        val result = exec.execute()

        val exitValue = result!!.exitValue
        if (exitValue == 0) {
            return Pair.of(buffer.readAsString(), errorBuffer.readAsString())
        } else if (exitValue == 69) {
            // After an Xcode upgrade, running clang will frequently fail in a mysterious way.
            // Make the failure very obvious by throwing this back up to the user.
            val errorBufferAsString = errorBuffer.readAsString()
            check(!errorBufferAsString.contains("Agreeing to the Xcode")) { "You will be unable to use Xcode's tool chain until you accept the Xcode license.\n" + errorBufferAsString }
        }
        return null
    }

    protected abstract fun compilerArgs(): MutableList<String?>?

    class BrokenResultException(message: String?) : RuntimeException(message)

    class DefaultCompilerExecSpec : CompilerMetaDataProvider.CompilerExecSpec {
        val environments: MutableMap<String?, String?> = HashMap<String?, String?>()
        val args: MutableList<String?> = ArrayList<String?>()
        var executable: File? = null

        override fun environment(key: String?, value: String?): CompilerMetaDataProvider.CompilerExecSpec {
            environments.put(key, value)
            return this
        }

        override fun executable(executable: File): CompilerMetaDataProvider.CompilerExecSpec {
            this.executable = executable
            return this
        }

        override fun args(args: Iterable<String?>): CompilerMetaDataProvider.CompilerExecSpec {
            this.args.addAll(ImmutableList.copyOf<String?>(args))
            return this
        }
    }
}
