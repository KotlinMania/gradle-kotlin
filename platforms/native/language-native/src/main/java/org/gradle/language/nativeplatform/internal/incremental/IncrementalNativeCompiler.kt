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
package org.gradle.language.nativeplatform.internal.incremental

import com.google.common.collect.ImmutableList
import org.gradle.api.internal.TaskOutputsInternal
import org.gradle.api.tasks.WorkResult
import org.gradle.api.tasks.WorkResults
import org.gradle.cache.ObjectHolder
import org.gradle.internal.file.Deleter
import org.gradle.language.base.internal.compile.Compiler
import org.gradle.language.base.internal.tasks.StaleOutputCleaner
import org.gradle.nativeplatform.toolchain.internal.NativeCompileSpec
import org.jspecify.annotations.NullMarked
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

@NullMarked
class IncrementalNativeCompiler<T : NativeCompileSpec?>(
    private val outputs: TaskOutputsInternal,
    private val delegateCompiler: Compiler<T?>,
    private val deleter: Deleter,
    private val compileStateCache: ObjectHolder<CompilationState>,
    private val incrementalCompilation: IncrementalCompilation
) : Compiler<T?> {
    private val logger: Logger = LoggerFactory.getLogger(IncrementalNativeCompiler::class.java)

    override fun execute(spec: T?): WorkResult {
        val workResult: WorkResult
        if (spec!!.isIncrementalCompile()) {
            workResult = doIncrementalCompile(incrementalCompilation, spec)
        } else {
            workResult = doCleanIncrementalCompile(spec)
        }

        compileStateCache.set(incrementalCompilation.getFinalState())

        return workResult
    }

    private fun getSourceFilesForPch(spec: T?): MutableList<File> {
        // When the component defines a precompiled header, we need to check if the precompiled header is the _first_ header in the source file.
        // For source files that do not include the precompiled header as the first file, we emit a warning
        // For source files that do include the precompiled header, we mark them as a "source file for pch"
        // The native compiler then adds the appropriate compiler arguments for those source files that can use PCH
        if (spec!!.getPreCompiledHeader() != null) {
            val sourceFiles = ImmutableList.builder<File>()
            for (sourceFile in spec.getSourceFiles()) {
                val state = incrementalCompilation.getFinalState().getState(sourceFile)
                val hash = state.getHash()
                val headers: MutableList<String> = ArrayList<String>()
                for (edge in state.getEdges()) {
                    if (hash == edge.getIncludedBy()) {
                        headers.add(edge.getIncludePath())
                    }
                }
                val header = spec.getPreCompiledHeader()
                val usePCH = !headers.isEmpty() && header == headers.get(0)
                if (usePCH) {
                    sourceFiles.add(sourceFile)
                } else {
                    val containsHeader = headers.contains(header)
                    if (containsHeader) {
                        logger.warn(getCantUsePCHMessage(spec.getPreCompiledHeader(), sourceFile))
                    }
                }
            }
            return sourceFiles.build()
        }

        return mutableListOf<File>()
    }

    protected fun doIncrementalCompile(compilation: IncrementalCompilation, spec: T?): WorkResult {
        // Determine the actual sources to clean/compile
        spec!!.setSourceFiles(compilation.getRecompile())
        spec.setRemovedSourceFiles(compilation.getRemoved())
        spec.setSourceFilesForPch(getSourceFilesForPch(spec))
        return delegateCompiler.execute(spec)
    }

    protected fun doCleanIncrementalCompile(spec: T?): WorkResult {
        val deleted = cleanPreviousOutputs(spec!!)
        spec.setSourceFilesForPch(getSourceFilesForPch(spec))
        val compileResult = delegateCompiler.execute(spec)
        if (deleted && !compileResult.getDidWork()) {
            return WorkResults.didWork(true)
        }
        return compileResult
    }

    private fun cleanPreviousOutputs(spec: NativeCompileSpec): Boolean {
        return StaleOutputCleaner.cleanOutputs(deleter, outputs.getPreviousOutputFiles(), spec.getObjectFileDir())
    }

    companion object {
        private fun getCantUsePCHMessage(pchHeader: String, sourceFile: File): String {
            return "The source file " + sourceFile.getName() + " includes the header " + pchHeader + " but it is not the first declared header, so the pre-compiled header will not be used."
        }
    }
}
