/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.internal.artifacts.transform

import org.gradle.api.InvalidUserDataException
import org.gradle.api.internal.file.FileLookup
import org.gradle.internal.file.PathToFileResolver
import org.gradle.util.internal.GFileUtils
import java.io.File
import java.util.function.Consumer

class DefaultTransformOutputs(private val inputArtifact: File, private val outputDir: File, fileLookup: FileLookup) : TransformOutputsInternal {
    private val resultBuilder: TransformExecutionResult.OutputTypeInferringBuilder
    private val outputDirectories: MutableSet<File> = HashSet<File>()
    private val outputFiles: MutableSet<File> = HashSet<File>()
    private val resolver: PathToFileResolver

    init {
        this.resolver = fileLookup.getPathToFileResolver(outputDir)
        this.resultBuilder = TransformExecutionResult.Companion.builderFor(inputArtifact, outputDir)
    }

    override fun getRegisteredOutputs(): TransformExecutionResult {
        val result = resultBuilder.build()
        result.visitOutputs(object : TransformExecutionResult.OutputVisitor {
            override fun visitEntireInputArtifact() {
                validate(inputArtifact)
            }

            override fun visitPartOfInputArtifact(relativePath: String) {
                validate(File(inputArtifact, relativePath))
            }

            override fun visitProducedOutput(relativePath: String) {
                validate(File(outputDir, relativePath))
            }

            fun validate(output: File) {
                validateOutputExists(outputDir, output)
                if (outputFiles.contains(output) && !output.isFile()) {
                    throw InvalidUserDataException("Transform output file " + output.getPath() + " must be a file, but is not.")
                }
                if (outputDirectories.contains(output) && !output.isDirectory()) {
                    throw InvalidUserDataException("Transform output directory " + output.getPath() + " must be a directory, but is not.")
                }
            }
        })

        return result
    }

    override fun dir(path: Any): File {
        val outputDir = resolveAndRegister(path, Consumer { dir: File -> GFileUtils.mkdirs(dir) })
        outputDirectories.add(outputDir)
        return outputDir
    }

    override fun file(path: Any): File {
        val outputFile = resolveAndRegister(path, Consumer { location: File -> GFileUtils.mkdirs(location.getParentFile()) })
        outputFiles.add(outputFile)
        return outputFile
    }

    private fun resolveAndRegister(path: Any, prepareOutputLocation: Consumer<File>): File {
        val output = resolver.resolve(path)
        resultBuilder.addOutput(output, prepareOutputLocation)
        return output
    }

    companion object {
        private fun validateOutputExists(outputDir: File, output: File) {
            if (!output.exists()) {
                val outputAbsolutePath = output.getAbsolutePath()
                val outputDirPrefix = outputDir.getAbsolutePath() + File.separator
                val reportedPath = if (outputAbsolutePath.startsWith(outputDirPrefix))
                    outputAbsolutePath.substring(outputDirPrefix.length)
                else
                    outputAbsolutePath
                throw InvalidUserDataException("Transform output " + reportedPath + " must exist.")
            }
        }
    }
}
