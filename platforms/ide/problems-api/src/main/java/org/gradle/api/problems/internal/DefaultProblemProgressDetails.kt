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

import org.gradle.operations.problems.ProblemUsageProgressDetails

class DefaultProblemProgressDetails(@JvmField val problem: ProblemInternal) : ProblemProgressDetails, ProblemUsageProgressDetails {
    private val buildOperationProblem: BuildOperationProblem

    init {
        this.buildOperationProblem = BuildOperationProblem(problem)
    }

    val definition: ProblemDefinition
        get() = buildOperationProblem.definition

    val severity: ProblemSeverity
        get() = buildOperationProblem.severity

    val contextualLabel: String?
        get() = buildOperationProblem.contextualLabel

    val solutions: MutableList<String>
        get() = buildOperationProblem.solutions

    val details: String?
        get() = buildOperationProblem.details

    val originLocations: MutableList<ProblemLocation>
        get() = buildOperationProblem.originLocations

    val contextualLocations: MutableList<ProblemLocation>
        get() = buildOperationProblem.contextualLocations

    val failure: Throwable?
        get() = if (problem.getException() == null) null else problem.getException()
}
