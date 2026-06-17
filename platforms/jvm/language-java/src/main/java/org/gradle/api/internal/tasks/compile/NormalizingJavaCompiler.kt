/*
 * Copyright 2009 the original author or authors.
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

import com.google.common.base.Predicate
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Iterables
import org.apache.commons.lang3.StringUtils
import org.gradle.api.logging.Logging.getLogger
import org.gradle.api.tasks.WorkResult
import org.gradle.api.tasks.WorkResults
import org.gradle.internal.FileUtils
import org.gradle.language.base.internal.compile.Compiler
import org.gradle.util.internal.CollectionUtils
import java.io.File
import java.util.stream.Collectors

/**
 * A Java [Compiler] which does some normalization of the compile configuration and behaviour before delegating to some other compiler.
 */
class NormalizingJavaCompiler(private val delegate: Compiler<JavaCompileSpec?>) : Compiler<JavaCompileSpec?> {
    override fun execute(spec: JavaCompileSpec): WorkResult? {
        resolveAndFilterSourceFiles(spec)
        resolveNonStringsInCompilerArgs(spec)
        logSourceFiles(spec)
        logCompilerArguments(spec)
        return delegateAndHandleErrors(spec)
    }

    private fun resolveAndFilterSourceFiles(spec: JavaCompileSpec) {
        // this mimics the behavior of the Ant javac task (and therefore AntJavaCompiler),
        // which silently excludes files not ending in .java
        val javaOnly: Iterable<File?> = Iterables.filter<File?>(spec.getSourceFiles(), Predicate { input: File? -> hasJavaExtension(input) })
        spec.setSourceFiles(ImmutableSet.copyOf<File?>(javaOnly))
    }

    private fun resolveNonStringsInCompilerArgs(spec: JavaCompileSpec) {
        // in particular, this is about GStrings
        spec.compileOptions.setCompilerArgs(CollectionUtils.toStringList(spec.compileOptions!!.compilerArgs!!))
    }

    private fun logSourceFiles(spec: JavaCompileSpec) {
        if (!spec.compileOptions.isListFiles()) {
            return
        }

        val builder = StringBuilder()
        builder.append("Source files to be compiled:")
        for (file in spec.getSourceFiles()!!) {
            builder.append('\n')
            builder.append(file)
        }

        LOGGER!!.quiet(builder.toString())
    }

    private fun logCompilerArguments(spec: JavaCompileSpec) {
        if (!LOGGER!!.isDebugEnabled()) {
            return
        }

        val compilerArgs = JavaCompilerArgumentsBuilder(spec).includeLauncherOptions(true).includeSourceFiles(true).build()
        val joinedArgs = compilerArgs.stream().map<String?> { it: String? -> if (StringUtils.isBlank(it)) ('"'.toString() + it + '"') else it }.collect(Collectors.joining(" "))
        LOGGER.debug("Compiler arguments: {}", joinedArgs)
    }

    private fun delegateAndHandleErrors(spec: JavaCompileSpec): WorkResult? {
        try {
            return delegate.execute(spec)
        } catch (e: CompilationFailedException) {
            if (spec.compileOptions.isFailOnError()) {
                throw e
            }
            LOGGER!!.debug("Ignoring compilation failure.")
            return WorkResults.didWork(false)
        }
    }

    companion object {
        private val LOGGER = getLogger(NormalizingJavaCompiler::class.java)
        private fun hasJavaExtension(input: File?): Boolean {
            return FileUtils.hasExtension(input!!, ".java")
        }
    }
}
