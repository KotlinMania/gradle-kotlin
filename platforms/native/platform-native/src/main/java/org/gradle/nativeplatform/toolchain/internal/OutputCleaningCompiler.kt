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
package org.gradle.nativeplatform.toolchain.internal

import org.gradle.api.tasks.WorkResult
import org.gradle.api.tasks.WorkResults
import org.gradle.language.base.internal.compile.Compiler
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory
import java.io.File

class OutputCleaningCompiler<T : NativeCompileSpec?>(
    private val compiler: Compiler<T?>,
    private val compilerOutputFileNamingSchemeFactory: CompilerOutputFileNamingSchemeFactory,
    private val outputFileSuffix: String?
) : Compiler<T?> {
    override fun execute(spec: T?): WorkResult {
        val didRemove = deleteOutputsForRemovedSources(spec!!)
        val didCompile = compileSources(spec)
        return WorkResults.didWork(didRemove || didCompile)
    }

    private fun compileSources(spec: T?): Boolean {
        if (spec!!.getSourceFiles().isEmpty()) {
            return false
        }
        return compiler.execute(spec).getDidWork()
    }

    private fun deleteOutputsForRemovedSources(spec: NativeCompileSpec): Boolean {
        var didRemove = false
        for (removedSource in spec.getRemovedSourceFiles()) {
            val objectFile = getObjectFile(spec.getObjectFileDir(), removedSource)

            // Remove .pdb file if present
            File(objectFile.getParentFile(), objectFile.getName() + ".pdb").delete()

            if (objectFile.delete()) {
                didRemove = true
                objectFile.getParentFile().delete()
            }
        }
        return didRemove
    }

    private fun getObjectFile(objectFileRoot: File?, sourceFile: File): File {
        return compilerOutputFileNamingSchemeFactory.create()
            .withObjectFileNameSuffix(outputFileSuffix)
            .withOutputBaseFolder(objectFileRoot)
            .map(sourceFile)
    }
}
