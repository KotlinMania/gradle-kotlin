/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.docs.asciidoctor

import org.asciidoctor.ast.Document
import org.asciidoctor.extension.IncludeProcessor
import org.asciidoctor.extension.PreprocessorReader
import java.io.BufferedReader
import java.io.IOException
import java.io.StringReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Arrays
import java.util.Collections
import java.util.regex.Pattern
import java.util.stream.Collectors
import kotlin.math.min

class SampleIncludeProcessor : IncludeProcessor() {
    override fun handles(target: String): Boolean {
        return target == SAMPLE
    }

    override fun process(document: Document, reader: PreprocessorReader, target: String?, attributes: MutableMap<String?, Any?>) {
        check(!(!attributes.containsKey("dir") || !attributes.containsKey("files"))) { "Both the 'dir' and 'files' attributes are required to include a sample" }

        val sampleBaseDir: String? = document.getAttribute("samples-dir", ".").toString()
        val sampleDir: String? = attributes.get("dir").toString()
        val files = Arrays.asList<String?>(*attributes.get("files").toString().split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())

        val sampleContent: String = getSampleContent(sampleBaseDir, sampleDir, files)
        reader.pushInclude(sampleContent, target, target, 1, attributes)
    }

    companion object {
        private const val SAMPLE = "sample"
        private val FILE_SUFFIX_TO_SYNTAX: MutableMap<String?, String> = initializeSyntaxMap()

        private const val DOUBLE_WILDCARD_TAG = "**"

        private val HTML_XML_SAMPLE_TAG: Pattern = Pattern.compile("\\s*<!--\\s*(tag|end)::(\\S+)\\[]\\s*-->")
        private val GENERAL_SAMPLE_TAG: Pattern = Pattern.compile(".*(tag|end)::(\\S+)\\[]\\s*")

        // Map file suffixes to syntax highlighting where they differ
        private fun initializeSyntaxMap(): MutableMap<String?, String> {
            val map: MutableMap<String?, String?> = HashMap<String?, String?>()
            map.put("gradle", "groovy")
            map.put("kt", "kotlin")
            map.put("kts", "kotlin")
            map.put("py", "python")
            map.put("sh", "bash")
            map.put("rb", "ruby")
            return Collections.unmodifiableMap<String?, String?>(map)
        }

        private fun getSourceSyntax(fileName: String): String {
            var syntax = "txt"
            val i = fileName.lastIndexOf('.')
            if (i > 0) {
                val substring = fileName.substring(i + 1)
                syntax = FILE_SUFFIX_TO_SYNTAX.getOrDefault(substring, substring)
            }
            return syntax
        }

        private fun getSampleContent(sampleBaseDir: String?, sampleDir: String?, files: MutableList<String>): String {
            val builder = StringBuilder(String.format("%n[.testable-sample.multi-language-sample,dir=\"%s\"]%n=====%n", sampleDir))
            for (fileDeclaration in files) {
                val sourceRelativeLocation: String = parseSourceFilePath(fileDeclaration)
                val tags: MutableList<String?> = parseTags(fileDeclaration)
                val sourceSyntax: String = getSourceSyntax(sourceRelativeLocation)
                val sourcePath = String.format("%s/%s/%s", sampleBaseDir, sampleDir, sourceRelativeLocation)
                var source: String = getContent(sourcePath)
                source = filterByTags(source, sourceSyntax, tags)
                source = trimIndent(source)
                builder.append(String.format(".%s%n[source,%s]%n----%n%s%n----%n", sourceRelativeLocation, sourceSyntax, source))
            }

            builder.append(String.format("=====%n"))
            return builder.toString()
        }

        private fun getContent(filePath: String): String {
            try {
                return String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8)
            } catch (e: IOException) {
                throw IllegalStateException("Unable to read source file " + Paths.get(filePath).toAbsolutePath().toFile().getAbsolutePath())
            }
        }

        private fun parseSourceFilePath(fileDeclaration: String): String {
            return fileDeclaration.replace("\\[[^]]*]".toRegex(), "")
        }

        private fun parseTags(fileDeclaration: String): MutableList<String?> {
            val tags: MutableList<String?> = ArrayList<String?>()
            val pattern = Pattern.compile(".*\\[tags?=(.*)].*")
            val matcher = pattern.matcher(fileDeclaration)
            if (matcher.matches()) {
                tags.addAll(Arrays.asList<String>(*matcher.group(1).split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()))
            }
            return tags
        }

        /**
         * When tags are empty or contain a single wildcard tag, the whole sample is returned (with all tag lines removed).
         *
         * @see "https://docs.asciidoctor.org/asciidoc/latest/directives/include-tagged-regions/.tag-filtering"
         */
        private fun filterByTags(source: String, syntax: String, tags: MutableList<String?>): String {
            val sampleTagRegex: Pattern = if (syntax == "html" || syntax == "xml") HTML_XML_SAMPLE_TAG else GENERAL_SAMPLE_TAG

            val result = StringBuilder(source.length)

            val fullSample = tags.isEmpty() || (tags.size == 1 && DOUBLE_WILDCARD_TAG == tags.get(0))

            if (fullSample) {
                // filter out lines matching the tagging regex
                val sampleWithoutTags = Pattern.compile("\\R").splitAsStream(source)
                    .filter { line: String? -> !sampleTagRegex.matcher(line).matches() }
                    .collect(Collectors.joining("\n"))
                result.append(sampleWithoutTags)
            } else {
                var activeTag: String? = null
                try {
                    BufferedReader(StringReader(source)).use { reader ->
                        var line: String?
                        while ((reader.readLine().also { line = it }) != null) {
                            if (activeTag != null) {
                                if (line!!.contains("end::" + activeTag + "[]")) {
                                    activeTag = null
                                } else if (!sampleTagRegex.matcher(line).matches()) {
                                    result.append(line).append("\n")
                                }
                            } else {
                                activeTag = Companion.determineActiveTag(line!!, tags)
                            }
                        }
                    }
                } catch (e: IOException) {
                    throw RuntimeException("Unexpected exception while filtering tagged content")
                }
            }

            return result.toString()
        }

        private fun determineActiveTag(line: String, tags: MutableList<String?>): String? {
            for (tag in tags) {
                if (line.contains("tag::" + tag + "[]")) {
                    return tag
                }
            }
            return null
        }

        private fun trimIndent(source: String): String {
            val lines = source.split("\r\n|\r|\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

            val minIndent: Int = getMinIndent(lines)
            if (minIndent == 0) {
                return source
            }

            val sb = StringBuilder()
            val newline = String.format("%n")
            for (line in lines) {
                if (!line.trim { it <= ' ' }.isEmpty()) {
                    sb.append(line.substring(minIndent))
                }
                sb.append(newline)
            }
            return sb.toString()
        }

        private fun getMinIndent(lines: Array<String>): Int {
            var minIndent = Int.MAX_VALUE
            for (line in lines) {
                if (line.trim { it <= ' ' }.isEmpty()) {
                    continue
                }

                var indent = 0
                while (indent < line.length && Character.isWhitespace(line.get(indent))) {
                    indent++
                }
                if (indent < line.length) {
                    minIndent = min(minIndent, indent)
                }
            }
            return if (minIndent == Int.MAX_VALUE) 0 else minIndent
        }
    }
}
