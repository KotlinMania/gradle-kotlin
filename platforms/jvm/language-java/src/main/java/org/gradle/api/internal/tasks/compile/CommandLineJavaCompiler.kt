/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.api.internal.tasks.compile

import org.gradle.api.tasks.WorkResult
import org.gradle.api.tasks.WorkResults
import org.gradle.internal.process.ArgCollector
import org.gradle.language.base.internal.compile.Compiler
import org.gradle.process.internal.ClientExecHandleBuilderFactory
import org.gradle.process.internal.ExecHandle
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.Serializable

/**
 * Executes the Java command line compiler executable.
 */
class CommandLineJavaCompiler(private val execHandleFactory: ClientExecHandleBuilderFactory) : Compiler<JavaCompileSpec?>, Serializable {
    private val argumentsGenerator: CompileSpecToArguments<JavaCompileSpec?> = CommandLineJavaCompilerArgumentsGenerator()

    override fun execute(spec: JavaCompileSpec): WorkResult {
        require(spec is CommandLineJavaCompileSpec) { String.format("Expected a %s, but got %s", CommandLineJavaCompileSpec::class.java.getSimpleName(), spec.javaClass.getSimpleName()) }

        val executable: String? = (spec as CommandLineJavaCompileSpec).executable.toString()
        LOGGER.info("Compiling with Java command line compiler '{}'.", executable)

        val handle = createCompilerHandle(executable, spec)
        executeCompiler(handle)

        return WorkResults.didWork(true)
    }

    private fun createCompilerHandle(executable: String?, spec: JavaCompileSpec): ExecHandle {
        val builder = execHandleFactory.newExecHandleBuilder()
        builder!!.setWorkingDir(spec.workingDir)
        builder.executable = executable
        argumentsGenerator.collectArguments(spec, object : ArgCollector {
            override fun args(vararg args: Any?): ArgCollector {
                builder.args(*args)
                return this
            }

            override fun args(args: Iterable<*>): ArgCollector {
                builder.args(args)
                return this
            }
        })
        return builder.build()!!
    }

    private fun executeCompiler(handle: ExecHandle) {
        handle.start()
        val result = handle.waitForFinish()
        if (result!!.exitValue !== 0) {
            throw CompilationFailedException(result.exitValue)
        }
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(CommandLineJavaCompiler::class.java)
    }
}
