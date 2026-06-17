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
package org.gradle.tooling.events.problems.internal

import org.gradle.tooling.Failure
import org.gradle.tooling.events.problems.AdditionalData
import org.gradle.tooling.events.problems.ContextualLabel
import org.gradle.tooling.events.problems.Details
import org.gradle.tooling.events.problems.Location
import org.gradle.tooling.events.problems.Problem
import org.gradle.tooling.events.problems.ProblemDefinition
import org.gradle.tooling.events.problems.Solution
import org.jspecify.annotations.NullMarked

@NullMarked
class DefaultProblem(
    private val problemDefinition: ProblemDefinition,
    private val contextualLabel: ContextualLabel,
    private val details: Details,
    private val originLocations: MutableList<Location>,
    private val contextualLocations: MutableList<Location>,
    private val solutions: MutableList<Solution>,
    private val additionalData: AdditionalData,
    private val failure: Failure?
) : Problem {
    override fun getDefinition(): ProblemDefinition {
        return problemDefinition
    }

    override fun getContextualLabel(): ContextualLabel {
        return contextualLabel
    }

    override fun getDetails(): Details {
        return details
    }

    override fun getOriginLocations(): MutableList<Location> {
        return originLocations
    }

    override fun getContextualLocations(): MutableList<Location> {
        return contextualLocations
    }

    override fun getSolutions(): MutableList<Solution> {
        return solutions
    }

    override fun getFailure(): Failure? {
        return failure
    }

    override fun getAdditionalData(): AdditionalData {
        return additionalData
    }
}
