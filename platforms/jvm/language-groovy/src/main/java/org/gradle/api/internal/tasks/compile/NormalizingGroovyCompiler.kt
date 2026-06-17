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

import com.google.common.base.Joiner
import com.google.common.base.Predicate
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Iterables
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging.getLogger
import org.gradle.api.tasks.WorkResult
import org.gradle.api.tasks.WorkResults
import org.gradle.internal.FileUtils
import org.gradle.language.base.internal.compile.Compiler
import org.gradle.util.internal.CollectionUtils
import java.io.File

/**
 * A Groovy [Compiler] which does some normalization of the compile configuration and behaviour before delegating to some other compiler.
 */
class NormalizingGroovyCompiler(private val delegate: Compiler<GroovyJavaJointCompileSpec>) : Compiler<GroovyJavaJointCompileSpec> {
    override fun execute(spec: GroovyJavaJointCompileSpec): WorkResult {
        resolveAndFilterSourceFiles(spec)
        resolveNonStringsInCompilerArgs(spec)
        logSourceFiles(spec)
        logCompilerArguments(spec)
        return delegateAndHandleErrors(spec)
    }

    private fun resolveAndFilterSourceFiles(spec: GroovyJavaJointCompileSpec) {
        val fileExtensions: MutableList<String> = CollectionUtils.collect(spec.groovyCompileOptions!!.fileExtensions, { extension: Any? -> '.' + extension })
        val filtered: Iterable<File> = Iterables.filter<File?>(spec.getSourceFiles(), object : Predicate<File> {
            override fun apply(element: File): Boolean {
                for (fileExtension in fileExtensions) {
                    if (FileUtils.hasExtension(element, fileExtension)) {
                        return true
                    }
                }
                return false
            }
        })

        spec.setSourceFiles(ImmutableSet.copyOf<File>(filtered))
    }

    private fun resolveNonStringsInCompilerArgs(spec: GroovyJavaJointCompileSpec) {
        // in particular, this is about GStrings
        spec.compileOptions.setCompilerArgs(CollectionUtils.toStringList(spec.compileOptions!!.compilerArgs!!))
    }

    private fun logSourceFiles(spec: GroovyJavaJointCompileSpec) {
        if (!spec.groovyCompileOptions.isListFiles()) {
            return
        }

        val builder = StringBuilder()
        builder.append("Source files to be compiled:")
        for (file in spec.getSourceFiles()!!) {
            builder.append('\n')
            builder.append(file)
        }

        LOGGER.quiet(builder.toString())
    }

    private fun logCompilerArguments(spec: GroovyJavaJointCompileSpec) {
        if (!LOGGER.isDebugEnabled()) {
            return
        }

        val compilerArgs: MutableList<String> = JavaCompilerArgumentsBuilder(spec).includeLauncherOptions(true).includeSourceFiles(true).build()
        val joinedArgs = Joiner.on(' ').join(compilerArgs)
        LOGGER.debug("Java compiler arguments: {}", joinedArgs)
    }

    private fun delegateAndHandleErrors(spec: GroovyJavaJointCompileSpec): WorkResult {
        try {
            return delegate.execute(spec)
        } catch (e: RuntimeException) {
            // in-process Groovy compilation throws a CompilationFailedException from another classloader, hence testing class name equality
            // TODO:pm Prefer class over class name for equality check once using WorkerExecutor for in-process groovy compilation
            if ((spec.compileOptions.isFailOnError() && spec.groovyCompileOptions.isFailOnError())
                || CompilationFailedException::class.java.getName() != e.javaClass.getName()
            ) {
                throw e
            }
            LOGGER.debug("Ignoring compilation failure.")
            return WorkResults.didWork(false)
        }
    }

    companion object {
        private val LOGGER: Logger = getLogger(NormalizingGroovyCompiler::class.java)!!
    }
}
