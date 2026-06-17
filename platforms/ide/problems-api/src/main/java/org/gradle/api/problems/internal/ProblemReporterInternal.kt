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
import org.gradle.api.problems.ProblemReporter
import org.gradle.internal.operations.OperationIdentifier

interface ProblemReporterInternal : ProblemReporter {
    /**
     * Reports the target problem with an explicit operation identifier.
     *
     *
     * This method is used to report problems from workers, where the operation identifier is not available.
     *
     * @param problem The problem to report.
     * @param id The operation identifier.
     */
    fun report(problem: Problem, id: OperationIdentifier)

    /**
     * Reports the target problem as it will cause the build to fail. Unlike [.throwing], this method does not throw an exception.
     * This is a temporary workaround as not all fatal problems can be reported via [.throwing] without significant refactoring. The goal is to eventually remove this method.
     *
     * @param problem The problem to report.
     */
    fun reportError(problem: Problem)

    /**
     * Reports the target problems as they will cause the build to fail. Unlike  [.throwing], this method does not throw an exception.
     * This is a temporary workaround as not all fatal problems can be reported via [.throwing] without significant refactoring. The goal is to eventually remove this method.
     *
     * @param problems The problems to report.
     */
    fun reportError(problems: MutableCollection<out Problem>)

    fun internalCreate(action: Action<in ProblemSpecInternal>): ProblemInternal?
}
