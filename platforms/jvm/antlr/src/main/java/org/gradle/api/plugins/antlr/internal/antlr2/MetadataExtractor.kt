/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.api.plugins.antlr.internal.antlr2

import antlr.Tool
import antlr.preprocessor.Hierarchy
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import java.io.BufferedReader
import java.io.File
import java.io.FileNotFoundException
import java.io.FileReader
import java.io.IOException
import java.io.Reader
import java.io.UncheckedIOException
import java.util.regex.Pattern

/**
 * Preprocess an Antlr grammar file so that dependencies between grammars can be properly determined such that they can
 * be processed for generation in proper order later.
 */
object MetadataExtractor {
    fun extractMetadata(sources: MutableSet<File>): XRef {
        val hierarchy = Hierarchy(Tool()) // extracting into methods will break test somehow.
        // first let antlr preprocess the grammars...
        for (grammarFile in sources) {
            try {
                hierarchy.readGrammarFile(grammarFile.getPath())
            } catch (e: FileNotFoundException) {
                // should never happen here
                throw IllegalStateException("Received FileNotFoundException on already read file", e)
            }
        }
        // now, do our processing using the antlr preprocessor results whenever possible.
        val xref = XRef(hierarchy)
        for (grammarFile in sources) {
            xref.addGrammarFile(
                GrammarFileMetadata(
                    grammarFile,
                    hierarchy.getFile(grammarFile.getPath()),
                    getPackageName(grammarFile)
                )
            )
        }
        return xref
    }

    private fun getPackageName(grammarFile: File): String? {
        try {
            // Note: source files can have non-UTF8 encoding. FileReader uses default Charset and also handles invalid characters.
            return getPackageName(FileReader(grammarFile))
        } catch (e: IOException) {
            throw UncheckedIOException("Cannot read antlr grammar file", e)
        }
    }

    @Throws(IOException::class)
    fun getPackageName(reader: Reader): String? {
        var grammarPackageName: String? = null
        try {
            BufferedReader(reader).use { `in` ->
                var line: String?
                while ((`in`.readLine().also { line = it }) != null) {
                    line = line!!.trim { it <= ' ' }
                    if (line.startsWith("package") && line.endsWith(";")) {
                        grammarPackageName = line.substring(8, line.length - 1)
                    } else if (line.startsWith("header")) {
                        val p = Pattern.compile("header \\{\\s*package\\s+(.+);\\s+}")
                        val m = p.matcher(line)
                        if (m.matches()) {
                            grammarPackageName = m.group(1)
                        }
                    }
                }
            }
        } catch (e: IOException) {
            throw throwAsUncheckedException(e)
        }
        return grammarPackageName
    }
}
