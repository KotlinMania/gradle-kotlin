/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.nativeplatform.toolchain.internal.swift

import org.gradle.api.internal.TaskOutputsInternal
import org.gradle.api.tasks.WorkResult
import org.gradle.api.tasks.WorkResults
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.file.Deleter
import org.gradle.language.base.internal.compile.Compiler
import org.gradle.language.base.internal.tasks.StaleOutputCleaner
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory
import org.gradle.nativeplatform.toolchain.internal.compilespec.SwiftCompileSpec
import java.io.File
import java.io.IOException

class IncrementalSwiftCompiler(
    private val compiler: Compiler<SwiftCompileSpec?>,
    private val outputs: TaskOutputsInternal,
    private val compilerOutputFileNamingSchemeFactory: CompilerOutputFileNamingSchemeFactory,
    private val deleter: Deleter
) : Compiler<SwiftCompileSpec?> {
    override fun execute(spec: SwiftCompileSpec): WorkResult {
        val didRemove: Boolean
        if (spec.isIncrementalCompile) {
            didRemove = deleteOutputsForRemovedSources(spec)
        } else {
            didRemove = cleanPreviousOutputs(spec)
        }

        val compileResult = compile(spec)
        return WorkResults.didWork(didRemove || compileResult.getDidWork())
    }

    protected fun compile(spec: SwiftCompileSpec?): WorkResult {
        return compiler.execute(spec)
    }

    private fun deleteOutputsForRemovedSources(spec: SwiftCompileSpec): Boolean {
        var didRemove = false
        for (removedSource in spec.removedSourceFiles!!) {
            val objectFile = getObjectFile(spec.objectFileDir, removedSource!!)

            try {
                if (deleter.deleteRecursively(objectFile.getParentFile())) {
                    didRemove = true
                }
            } catch (ex: IOException) {
                throw throwAsUncheckedException(ex)
            }
        }
        return didRemove
    }

    private fun getObjectFile(objectFileRoot: File?, sourceFile: File): File {
        return compilerOutputFileNamingSchemeFactory.create()
            .withObjectFileNameSuffix(".o") // TODO: Get this from somewhere else?
            .withOutputBaseFolder(objectFileRoot)
            .map(sourceFile)
    }

    private fun cleanPreviousOutputs(spec: SwiftCompileSpec): Boolean {
        return StaleOutputCleaner.cleanOutputs(deleter, outputs.getPreviousOutputFiles(), spec.objectFileDir)
    }
}
