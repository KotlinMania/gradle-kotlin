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

import org.gradle.api.problems.ProblemId
import org.gradle.api.problems.internal.ProblemInternal
import java.io.PrintWriter
import java.io.Writer
import java.util.function.Function
import java.util.stream.Collectors


/**
 * Renders one or more problems to a writer.
 */
abstract class ProblemWriter private constructor() {
    /**
     * Renders a single problem to the given writer.
     *
     * @param problem the problem to write
     * @param writer the writer to write to
     */
    abstract fun write(problem: ProblemInternal, writer: Writer)

    /**
     * Renders multiple problems to the given writer.
     *
     * @param problems the problems to write
     * @param writer the writer to write to
     */
    abstract fun write(problems: MutableCollection<ProblemInternal>, writer: Writer)

    /**
     * Writes a single problem on the console.
     */
    private class SimpleProblemWriter(private val writerRegistry: ProblemWriterRegistry, private val options: RenderOptions) : ProblemWriter() {
        override fun write(problem: ProblemInternal, writer: Writer) {
            val output = PrintWriter(writer)
            writerRegistry.problemWriterFor(problem.getDefinition()!!.getId()!!).write(problem, options, output)
        }

        override fun write(problems: MutableCollection<ProblemInternal>, writer: Writer) {
            val output = PrintWriter(writer)
            var sep = ""
            for (problem in problems) {
                output.printf(sep)
                sep = "%n"
                writerRegistry.problemWriterFor(problem.getDefinition()!!.getId()!!).write(problem, options, output)
            }
        }
    }

    /**
     * Writes a collection of problems, grouping them by problem id.
     */
    private class GroupingProblemWriter(private val problemWriterRegistry: ProblemWriterRegistry, private val options: RenderOptions) : ProblemWriter() {
        override fun write(problem: ProblemInternal, writer: Writer) {
            write(mutableListOf<ProblemInternal>(problem), writer)
        }

        override fun write(problems: MutableCollection<ProblemInternal>, writer: Writer) {
            write(problems, PrintWriter(writer))
        }

        fun write(problems: MutableCollection<ProblemInternal>, output: PrintWriter) {
            // Group problems by problem id
            // When generic rendering is addressed, maybe we also group by the whole problem group hierarchy
            val problemIdListMap: MutableMap<ProblemId, MutableList<ProblemInternal>> =
                problems.stream().collect(Collectors.groupingBy(Function { internalProblem: ProblemInternal? -> internalProblem!!.getDefinition()!!.getId()!! }))
            var separator = ""
            for (problemIdListEntry in problemIdListMap.entries) {
                renderProblemsById(output, problemIdListEntry.key, problemIdListEntry.value, separator)
                separator = "%n"
            }
        }

        fun renderProblemsById(output: PrintWriter, problemId: ProblemId, problems: MutableList<ProblemInternal>, separator: String) {
            var sep = separator
            val renderer = problemWriterRegistry.problemWriterFor(problemId)
            for (problem in problems) {
                output.printf(sep)
                renderer.write(problem, options, output)
                sep = "%n"
            }
        }
    }

    companion object {
        /**
         * Creates a simple problem writer that renders each problem individually without grouping.
         * @return the problem writer
         */
        @JvmStatic
        fun simple(): ProblemWriter {
            return SimpleProblemWriter(
                ProblemWriterRegistry.Companion.INSTANCE,
                RenderOptions("Problem found: ", true)
            )
        }

        /**
         * Creates a problem writer that writes problems in groups based on their problem id.
         * @return the problem writer
         */
        @JvmStatic
        fun grouping(): ProblemWriter {
            return GroupingProblemWriter(
                ProblemWriterRegistry.Companion.INSTANCE,
                RenderOptions("", false)
            )
        }
    }
}
