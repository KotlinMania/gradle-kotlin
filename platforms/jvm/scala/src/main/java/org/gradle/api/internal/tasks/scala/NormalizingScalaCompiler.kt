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
package org.gradle.api.internal.tasks.scala

import com.google.common.base.Joiner
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Lists
import org.gradle.api.internal.tasks.compile.CompilationFailedException
import org.gradle.api.internal.tasks.compile.DefaultJvmLanguageCompileSpec.setCompileClasspath
import org.gradle.api.internal.tasks.compile.JavaCompileSpec
import org.gradle.api.internal.tasks.compile.JavaCompilerArgumentsBuilder
import org.gradle.api.logging.Logging.getLogger
import org.gradle.api.tasks.WorkResult
import org.gradle.api.tasks.WorkResults
import org.gradle.language.base.internal.compile.Compiler
import org.gradle.util.internal.CollectionUtils
import java.io.File

/**
 * A Scala [Compiler] which does some normalization of the compile configuration and behaviour before delegating to some other compiler.
 */
class NormalizingScalaCompiler(private val delegate: Compiler<ScalaJavaJointCompileSpec?>) : Compiler<ScalaJavaJointCompileSpec?> {
    override fun execute(spec: ScalaJavaJointCompileSpec): WorkResult? {
        resolveSourceFiles(spec)
        resolveClasspath(spec)
        resolveNonStringsInCompilerArgs(spec)
        logSourceFiles(spec)
        logCompilerArguments(spec)
        return delegateAndHandleErrors(spec)
    }

    private fun resolveSourceFiles(spec: JavaCompileSpec) {
        spec.setSourceFiles(ImmutableSet.copyOf<File?>(spec.getSourceFiles()))
    }

    private fun resolveClasspath(spec: ScalaJavaJointCompileSpec) {
        val classPath: MutableList<File?> = Lists.newArrayList(spec.compileClasspath)
        classPath.add(spec.getDestinationDir())
        spec.setCompileClasspath(classPath)

        if (LOGGER!!.isDebugEnabled()) {
            LOGGER.debug("Class path: {}", spec.compileClasspath)
        }
    }

    private fun resolveNonStringsInCompilerArgs(spec: ScalaJavaJointCompileSpec) {
        // in particular, this is about GStrings
        spec.compileOptions.setCompilerArgs(CollectionUtils.toStringList(spec.compileOptions!!.compilerArgs!!))
    }

    private fun logSourceFiles(spec: ScalaJavaJointCompileSpec) {
        if (!spec.scalaCompileOptions.isListFiles()) {
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

    private fun logCompilerArguments(spec: ScalaJavaJointCompileSpec) {
        if (!LOGGER!!.isDebugEnabled()) {
            return
        }

        val compilerArgs = JavaCompilerArgumentsBuilder(spec).includeLauncherOptions(true).includeSourceFiles(true).build()
        val joinedArgs = Joiner.on(' ').join(compilerArgs)
        LOGGER.debug("Java compiler arguments: {}", joinedArgs)
    }

    private fun delegateAndHandleErrors(spec: ScalaJavaJointCompileSpec): WorkResult? {
        try {
            return delegate.execute(spec)
        } catch (e: CompilationFailedException) {
            if (spec.compileOptions.isFailOnError() && spec.scalaCompileOptions.isFailOnError()) {
                throw e
            }
            LOGGER!!.debug("Ignoring compilation failure.")
            return WorkResults.didWork(false)
        }
    }

    companion object {
        private val LOGGER = getLogger(NormalizingScalaCompiler::class.java)
    }
}
