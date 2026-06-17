/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.api.internal.tasks.compile

import org.gradle.api.problems.internal.ProblemInternal
import org.gradle.internal.exceptions.CompilationFailedIndicator
import org.gradle.problems.internal.rendering.ProblemWriter.Companion.simple
import java.io.StringWriter
import java.util.Optional

class CompilationFailedException : RuntimeException, CompilationFailedIndicator {
    private val compilerPartialResult: ApiCompilerResult?
    private val diagnosticCounts: String?
    private val shortMessage: String?

    constructor(exitCode: Int) : super(String.format("Compilation failed with exit code %d; see the compiler error output for details.", exitCode)) {
        this.compilerPartialResult = null
        this.diagnosticCounts = null
        shortMessage = message
    }

    constructor(cause: Throwable?) : super(cause) {
        this.compilerPartialResult = null
        this.diagnosticCounts = null
        shortMessage = message
    }

    @JvmOverloads
    constructor(result: ApiCompilerResult? = null as ApiCompilerResult?) : super(COMPILATION_FAILED_DETAILS_ABOVE) {
        this.compilerPartialResult = result
        this.diagnosticCounts = null
        this.shortMessage = message
    }

    internal constructor(
        result: ApiCompilerResult?,
        reportedProblems: MutableList<ProblemInternal?>,
        diagnosticCounts: String?
    ) : super(exceptionMessage(COMPILATION_FAILED_DETAILS_BELOW + System.lineSeparator(), reportedProblems, diagnosticCounts)) {
        this.compilerPartialResult = result
        this.diagnosticCounts = diagnosticCounts
        this.shortMessage = COMPILATION_FAILED_DETAILS_BELOW
    }

    fun getCompilerPartialResult(): Optional<ApiCompilerResult?> {
        return Optional.ofNullable<ApiCompilerResult?>(compilerPartialResult)
    }

    override fun getDiagnosticCounts(): String? {
        return diagnosticCounts
    }

    override fun getShortMessage(): String? {
        return shortMessage
    }

    companion object {
        const val RESOLUTION_MESSAGE: String = "Check your code and dependencies to fix the compilation error(s)"
        const val COMPILATION_FAILED_DETAILS_ABOVE: String = "Compilation failed; see the compiler error output for details."
        const val COMPILATION_FAILED_DETAILS_BELOW: String = "Compilation failed; see the compiler output below."

        /*
     * A Build Scan does not consume Problems API reports to render compilation errors yet. To keep the error message in scans consistent with the console, we need to render the problems in the exception message.
     */
        private fun exceptionMessage(prefix: String?, problems: MutableList<ProblemInternal?>, diagnosticCounts: String?): String? {
            val result = StringWriter()
            result.append(prefix)
            simple().write(problems, result)
            result.append(System.lineSeparator())
            result.append(diagnosticCounts)
            return result.toString()
        }
    }
}
