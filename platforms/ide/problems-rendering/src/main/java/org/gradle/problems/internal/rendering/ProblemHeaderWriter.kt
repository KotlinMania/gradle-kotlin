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

import org.gradle.api.problems.internal.ProblemInternal
import java.io.PrintWriter

/**
 * Writes the header line for a problem.
 */
internal class ProblemHeaderWriter : PartialProblemWriter {
    override fun write(problem: ProblemInternal, options: RenderOptions, output: PrintWriter) {
        output.print(headerFor(options, problem))
    }

    private fun headerFor(options: RenderOptions, problem: ProblemInternal): String {
        val problemId = problem.getDefinition()!!.getId()!!
        val result = StringBuilder(options.prefix)
        result.append(problemId.getDisplayName())
        if (options.isRenderId) {
            result.append(" (id: ")
            result.append(problemId)
            result.append(")")
        }
        return result.toString()
    }
}
