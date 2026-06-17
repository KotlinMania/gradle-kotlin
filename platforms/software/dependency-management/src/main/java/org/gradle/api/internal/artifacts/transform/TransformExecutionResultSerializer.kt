/*
 * Copyright 2021 the original author or authors.
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

import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files

class TransformExecutionResultSerializer {
    fun writeToFile(target: File, result: TransformExecutionResult) {
        val resultFileContents: MutableList<String> = ArrayList<String>(result.size())

        result.visitOutputs(object : TransformExecutionResult.OutputVisitor {
            override fun visitEntireInputArtifact() {
                resultFileContents.add(INPUT_FILE_PATH_PREFIX)
            }

            override fun visitPartOfInputArtifact(relativePath: String) {
                resultFileContents.add(INPUT_FILE_PATH_PREFIX + relativePath)
            }

            override fun visitProducedOutput(relativePath: String) {
                resultFileContents.add(OUTPUT_FILE_PATH_PREFIX + relativePath)
            }
        })
        unchecked({ Files.write(target.toPath(), resultFileContents) })
    }

    fun readResultsFile(resultsFile: File): TransformExecutionResult {
        val transformerResultsPath = resultsFile.toPath()
        try {
            val builder: TransformExecutionResult.Builder = TransformExecutionResult.Companion.builder()
            val paths = Files.readAllLines(transformerResultsPath, StandardCharsets.UTF_8)
            for (path in paths) {
                if (path.startsWith(OUTPUT_FILE_PATH_PREFIX)) {
                    builder.addProducedOutput(path.substring(2))
                } else if (path.startsWith(INPUT_FILE_PATH_PREFIX)) {
                    val relativePathString = path.substring(2)
                    if (relativePathString.isEmpty()) {
                        builder.addEntireInputArtifact()
                    } else {
                        builder.addPartOfInputArtifact(relativePathString)
                    }
                } else {
                    throw IllegalStateException("Cannot parse result path string: " + path)
                }
            }
            return builder.build()
        } catch (e: IOException) {
            throw throwAsUncheckedException(e)
        }
    }

    companion object {
        private const val INPUT_FILE_PATH_PREFIX = "i/"
        private const val OUTPUT_FILE_PATH_PREFIX = "o/"
    }
}
