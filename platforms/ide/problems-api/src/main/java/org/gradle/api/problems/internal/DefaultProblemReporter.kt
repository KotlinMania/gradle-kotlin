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
package org.gradle.api.problems.internal

import org.gradle.api.Action
import org.gradle.api.problems.Problem
import org.gradle.api.problems.ProblemId
import org.gradle.api.problems.ProblemSpec
import org.gradle.api.problems.Severity
import org.gradle.internal.exception.ExceptionAnalyser
import org.gradle.internal.operations.CurrentBuildOperationRef
import org.gradle.internal.operations.OperationIdentifier

class DefaultProblemReporter(
    private val problemSummarizer: ProblemSummarizer,
    private val currentBuildOperationRef: CurrentBuildOperationRef,
    private val exceptionProblemRegistry: ExceptionProblemRegistry,
    private val exceptionAnalyser: ExceptionAnalyser,
    private val infrastructure: ProblemsInfrastructure
) : ProblemReporterInternal {
    override fun report(problemId: ProblemId, spec: Action<in ProblemSpec>) {
        val problemBuilder = createProblemBuilder()
        problemBuilder.id(problemId)
        spec.execute(problemBuilder)
        report(problemBuilder.build()!!)
    }

    private fun createProblemBuilder(): DefaultProblemBuilder {
        return DefaultProblemBuilder(infrastructure)
    }

    override fun reportError(problem: Problem) {
        var problem = problem
        problem = getBuilder(problem).internalSeverity(Severity.ERROR)!!.build()!!
        report(problem)
    }

    override fun reportError(problems: MutableCollection<out Problem>) {
        for (problem in problems) {
            reportError(problem)
        }
    }

    override fun throwing(exception: Throwable, problemId: ProblemId, spec: Action<in ProblemSpec>): RuntimeException {
        val problemBuilder = createProblemBuilder()
        problemBuilder.id(problemId)
        spec.execute(problemBuilder)
        problemBuilder.withException(exception)
        report(addExceptionToProblem(exception, problemBuilder.build()!!))
        throw runtimeException(exception)
    }

    override fun throwing(exception: Throwable, problem: Problem): RuntimeException {
        var problem = problem
        problem = addExceptionToProblem(exception, problem)
        report(problem)
        throw runtimeException(exception)
    }

    override fun throwing(exception: Throwable, problems: MutableCollection<out Problem>): RuntimeException {
        for (problem in problems) {
            report(addExceptionToProblem(exception, problem))
        }
        throw runtimeException(exception)
    }

    private fun addExceptionToProblem(exception: Throwable, problem: Problem): ProblemInternal {
        return getBuilder(problem).internalSeverity(Severity.ERROR)!!.withException(transform(exception))!!.build()!!
    }

    override fun create(problemId: ProblemId, action: Action<in ProblemSpec>): Problem {
        val defaultProblemBuilder = createProblemBuilder()
        defaultProblemBuilder.id(problemId)
        action.execute(defaultProblemBuilder)
        return defaultProblemBuilder.build()!!
    }

    override fun internalCreate(action: Action<in ProblemSpecInternal>): ProblemInternal {
        val defaultProblemBuilder = createProblemBuilder()
        action.execute(defaultProblemBuilder)
        return defaultProblemBuilder.build()!!
    }

    /**
     * Reports a problem.
     *
     *
     * The current build operation is used as the operation identifier.
     * If there is no current build operation, the problem is not reported.
     *
     * @param problem The problem to report.
     */
    override fun report(problem: Problem) {
        val id = currentBuildOperationRef.getId()
        if (id != null) {
            report(problem, id)
        }
    }

    override fun report(problems: MutableCollection<out Problem>) {
        for (problem in problems) {
            report(problem)
        }
    }

    /**
     * Reports a problem with an explicit operation identifier.
     *
     *
     * The operation identifier should not be null,
     * otherwise the behavior will be defined by the used [ProblemEmitter].
     *
     * @param problem The problem to report.
     * @param id The operation identifier to associate with the problem.
     */
    override fun report(problem: Problem, id: OperationIdentifier) {
        val problemInternal = problem as ProblemInternal
        val exception = problemInternal.getException()
        if (exception != null) {
            exceptionProblemRegistry.onProblem(transform(exception), problemInternal)
        }
        problemSummarizer.emit(problemInternal, id)
    }

    private fun getBuilder(problem: Problem): ProblemBuilderInternal {
        return (problem as ProblemInternal).toBuilder(infrastructure)!!
    }

    private fun transform(failure: Throwable): Throwable {
        if (exceptionAnalyser == null) {
            return failure
        }
        try {
            return exceptionAnalyser.transform(failure)!!.cause!!
        } catch (e: Throwable) {
            throw RuntimeException(e)
        }
    }

    companion object {
        private fun runtimeException(exception: Throwable): RuntimeException {
            if (exception is RuntimeException) {
                return exception
            } else {
                return RuntimeException(exception)
            }
        }
    }
}
