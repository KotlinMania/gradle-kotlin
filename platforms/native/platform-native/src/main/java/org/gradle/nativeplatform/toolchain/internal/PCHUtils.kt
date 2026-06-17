/*
 * Copyright 2014 the original author or authors.
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

import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import org.gradle.api.Transformer
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.nativeplatform.toolchain.internal.compilespec.CPCHCompileSpec
import org.gradle.nativeplatform.toolchain.internal.compilespec.CppPCHCompileSpec
import org.gradle.util.internal.CollectionUtils.collect
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.function.Function

object PCHUtils {
    fun generatePCHObjectDirectory(tempDir: File?, prefixHeaderFile: File, preCompiledHeaderObjectFile: File): File {
        val generatedDir = File(tempDir, "preCompiledHeaders")
        generatedDir.mkdirs()
        val generatedHeader = File(generatedDir, prefixHeaderFile.getName())
        val generatedPCH = File(generatedDir, preCompiledHeaderObjectFile.getName())
        try {
            FileUtils.copyFile(prefixHeaderFile, generatedHeader)
            FileUtils.copyFile(preCompiledHeaderObjectFile, generatedPCH)
            return generatedDir
        } catch (e: IOException) {
            throw throwAsUncheckedException(e)
        }
    }

    fun generatePrefixHeaderFile(headers: MutableList<String?>, headerFile: File) {
        if (!headerFile.getParentFile().exists()) {
            headerFile.getParentFile().mkdirs()
        }

        try {
            FileUtils.writeLines(headerFile, collect<String?, String?>(headers, Function { header: String? ->
                if (header!!.startsWith("<")) {
                    return@collect "#include " + header
                } else {
                    return@collect "#include \"" + header + "\""
                }
            }))
        } catch (e: IOException) {
            throw throwAsUncheckedException(e)
        }
    }

    fun <T : NativeCompileSpec?> generatePCHSourceFile(original: T?, sourceFile: File): File {
        val generatedSourceDir = File(original!!.getTempDir(), "pchGenerated")
        generatedSourceDir.mkdirs()
        val generatedSource = File(generatedSourceDir, FilenameUtils.removeExtension(sourceFile.getName()) + getSourceFileExtension(original.javaClass))
        val headerFileCopy = File(generatedSourceDir, sourceFile.getName())
        try {
            FileUtils.copyFile(sourceFile, headerFileCopy)
            FileUtils.writeStringToFile(generatedSource, "#include \"" + headerFileCopy.getName() + "\"", StandardCharsets.UTF_8)
            return generatedSource
        } catch (e: IOException) {
            throw throwAsUncheckedException(e)
        }
    }

    fun <T : NativeCompileSpec?> getHeaderToSourceFileTransformer(type: Class<T?>?): Transformer<T?, T?> {
        return object : Transformer<T?, T?> {
            override fun transform(original: T?): T? {
                val newSourceFiles: MutableList<File?> = ArrayList<File?>()
                for (sourceFile in original!!.getSourceFiles()) {
                    newSourceFiles.add(TODO("Cannot convert element"))<T> org . gradle . nativeplatform . toolchain . internal . PCHUtils . generatePCHSourceFile < T ? > (original, sourceFile)
                }
                original.setSourceFiles(newSourceFiles)
                return original
            }
        }
    }

    private fun getSourceFileExtension(specClass: Class<out NativeCompileSpec?>): String {
        if (CPCHCompileSpec::class.java.isAssignableFrom(specClass)) {
            return ".c"
        }

        if (CppPCHCompileSpec::class.java.isAssignableFrom(specClass)) {
            return ".cpp"
        }

        throw IllegalArgumentException("Cannot determine source file extension for spec with type " + specClass.getSimpleName())
    }
}
