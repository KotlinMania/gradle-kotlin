/*
 * Copyright 2025 the original author or authors.
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
package org.gradle.problems.internal.rendering

import com.google.common.base.Strings
import org.gradle.api.problems.DocLink
import org.gradle.api.problems.FileLocation
import org.gradle.api.problems.LineInFileLocation
import org.gradle.api.problems.internal.DocLinkInternal
import org.gradle.api.problems.internal.ProblemInternal
import org.gradle.util.internal.TextUtil
import java.io.PrintWriter
import kotlin.text.isEmpty
import kotlin.text.split
import kotlin.text.toRegex

/**
 * Writes the 'body' of a problem, i.e., details, solutions, and locations.
 * The remaining rendering is implemented in [ProblemHeaderWriter].
 */
internal class ProblemBodyWriter : PartialProblemWriter {
    override fun write(problem: ProblemInternal, options: RenderOptions, output: PrintWriter) {
        // contextual message, if any
        val problemSubMessage: String? = getContextualMessage(problem)
        if (problemSubMessage != null) {
            output.printf("%n")
            indent(output, problemSubMessage, LEVEL_1_INDENT)
        }

        // indent details further if there was a contextual message
        val details = problem.getDetails()
        if (details != null) {
            output.printf("%n")
            indent(output, details, if (problemSubMessage == null) LEVEL_1_INDENT else LEVEL_2_INDENT)
        }

        // link to documentation
        val documentationLink: DocLink? = problem.getDefinition()?.getDocumentationLink()
        val documentationUrl = documentationLink?.getUrl()
        if (documentationUrl != null) {
            output.printf("%n")
            val message = if (documentationLink is DocLinkInternal)
                documentationLink.getConsultDocumentationMessage()
            else
                kotlin.String.format("For more information, please refer to %s.", documentationUrl)
            indent(output, message, LEVEL_2_INDENT)
        }

        // locations
        val fileLocations = problem.getOriginLocations().orEmpty().filterIsInstance<FileLocation>()
        for (location in fileLocations) {
            output.printf("%n")
            indent(output, "Location: " + location.getPath(), LEVEL_2_INDENT)
            if (location is LineInFileLocation) {
                val lineLocation = location
                output.printf(" line " + lineLocation.getLine())
            }
        }

        // solutions
        val solutions: List<kotlin.String> = problem.getSolutions().orEmpty()
        if (!solutions.isEmpty()) {
            output.printf("%n")
            if (solutions.size == 1) {
                writePrefixedMultiline(output, "Possible solution: ", LEVEL_2_INDENT, normalize(solutions.get(0)))
            } else {
                indent(output, "Possible solutions:", LEVEL_2_INDENT)
                for (i in solutions.indices) {
                    output.printf("%n")
                    writePrefixedMultiline(output, (i + 1).toString() + ". ", LEVEL_3_INDENT, normalize(solutions.get(i)))
                }
            }
        }
    }

    companion object {
        private const val LEVEL_1_INDENT = 2
        private const val LEVEL_2_INDENT = 4
        private const val LEVEL_3_INDENT = 6

        private fun normalize(message: kotlin.String): kotlin.String {
            return TextUtil.capitalize(TextUtil.endLineWithDot(message))!!
        }

        private fun writePrefixedMultiline(output: PrintWriter, firstLinePrefix: kotlin.String, firstLineIndent: Int, text: kotlin.String) {
            val lines = text.split("\\r?\\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            indent(output, firstLinePrefix + lines[0], firstLineIndent)
            val continuationIndent = firstLineIndent + firstLinePrefix.length
            for (i in 1..<lines.size) {
                output.printf("%n")
                indent(output, lines[i], continuationIndent)
            }
        }

        private fun getContextualMessage(problem: ProblemInternal): kotlin.String? {
            val contextualLabel = problem.getContextualLabel()
            if (contextualLabel != null) {
                return contextualLabel
            }
            val exception = problem.getException()
            if (exception != null) {
                return exception.localizedMessage
            }
            return null
        }

        fun indent(output: PrintWriter, message: kotlin.String?, level: Int) {
            if (message == null) {
                return
            }
            val prefix = Strings.repeat(" ", level)
            val formatted = TextUtil.indent(message, prefix)
            output.print(formatted)
        }
    }
}
