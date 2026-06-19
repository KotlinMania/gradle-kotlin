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
package org.gradle.api.problems

import org.gradle.api.Action
import org.gradle.api.Incubating

/**
 * Defines different ways to report problems.
 *
 * @since 8.6
 */
@Incubating
interface ProblemReporter {
    /**
     * Creates a new problem without reporting it immediately.
     * The created problem can be later reported with [.report].
     *
     * @param problemId The problem id
     * @param action The problem configuration.
     * @return The new problem.
     * @since 8.13
     */
    fun create(problemId: ProblemId, action: Action<in ProblemSpec>): Problem?

    /**
     * Configures and reports a new problem.
     *
     * @param problemId the problem id
     * @param spec the problem configuration
     * @since 8.13
     */
    fun report(problemId: ProblemId, spec: Action<in ProblemSpec>)

    /**
     * Reports the target problem.
     *
     * @param problem The problem to report.
     * @since 8.13
     */
    fun report(problem: Problem)

    /**
     * Reports the target problems.
     *
     * @param problems The problems to report.
     * @since 8.13
     */
    fun report(problems: MutableCollection<out Problem>)

    /**
     * Configures a new problem, reports it, and uses it to throw a new exception.
     *
     * @param exception the exception to throw after reporting the problems
     * @param problemId the problem id
     * @param spec the problem configuration
     * @return never returns by throwing the exception, but using `throw` statement at the call site is encouraged to indicate the intent and benefit from local control flow.
     * @since 8.13
     */
    fun throwing(exception: Throwable, problemId: ProblemId, spec: Action<in ProblemSpec>): RuntimeException

    /**
     * Configures a new problem, reports it, and uses it to throw a new exception.
     *
     * @param exception the exception to throw after reporting the problems
     * @param problem the problem to report
     * @return never returns by throwing the exception, but using `throw` statement at the call site is encouraged to indicate the intent and benefit from local control flow.
     * @since 8.13
     */
    fun throwing(exception: Throwable, problem: Problem): RuntimeException

    /**
     * Reports the target problems and throws a runtime exception. When this method is used, all reported problems will be associated with the thrown exception.
     *
     * @param exception the exception to throw after reporting the problems
     * @param problems the problems to report
     * @return nothing, the method throws an exception
     * @since 8.13
     */
    fun throwing(exception: Throwable, problems: MutableCollection<out Problem>): RuntimeException
}
