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
package org.gradle.buildinit.plugins.internal

import com.google.common.collect.Sets
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.io.PrintWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.function.Consumer
import java.util.stream.Collectors
import java.util.stream.StreamSupport

class GitIgnoreGenerator : BuildContentGenerator {
    override fun generate(settings: InitSettings, buildContentGenerationContext: BuildContentGenerationContext) {
        val file = settings.getTarget().file(".gitignore").getAsFile()
        val gitignoresToAppend: MutableSet<String> = getGitignoresToAppend(file)
        if (!gitignoresToAppend.isEmpty()) {
            val shouldAppendNewLine = file.exists()
            try {
                PrintWriter(Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)).use { writer ->
                    if (shouldAppendNewLine) {
                        writer.println()
                    }
                    val it = gitignoresToAppend.spliterator()
                    if (it.tryAdvance(Consumer { e: String? -> Companion.withComment(e!!).forEach(Consumer { x: String? -> writer.println(x) }) })) {
                        StreamSupport.stream<String>(it, false).forEach { e: String? -> withSeparator(Companion.withComment(e!!)).forEach(Consumer { x: String? -> writer.println(x) }) }
                    }
                }
            } catch (e: IOException) {
                throw throwAsUncheckedException(e)
            }
        }
    }

    companion object {
        //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
        private fun getGitignoresToAppend(gitignoreFile: File): MutableSet<String> {
            val result: MutableSet<String> = Sets.newLinkedHashSet<String>(mutableListOf<String>(".gradle", "build", ".kotlin"))
            if (gitignoreFile.exists()) {
                try {
                    BufferedReader(FileReader(gitignoreFile)).use { reader ->
                        result.removeAll(reader.lines().filter { it: String? -> result.contains(it) }.collect(Collectors.toSet()))
                    }
                } catch (e: IOException) {
                    throw throwAsUncheckedException(e)
                }
            }
            return result
        }

        private fun withComment(entry: String): MutableList<String> {
            val result: MutableList<String> = ArrayList<String>()
            if (entry.startsWith(".gradle")) {
                result.add("# Ignore Gradle project-specific cache directory")
            } else if (entry.startsWith("build")) {
                result.add("# Ignore Gradle build output directory")
            } else if (entry.startsWith(".kotlin")) {
                result.add("# Ignore Kotlin plugin data")
            }
            result.add(entry)

            return result
        }

        private fun withSeparator(entry: MutableList<String>): MutableList<String> {
            val result: MutableList<String> = ArrayList<String>(1 + entry.size)
            result.add("")
            result.addAll(entry)
            return result
        }
    }
}
