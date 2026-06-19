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

import com.google.common.base.Objects
import org.gradle.api.problems.AdditionalData
import org.gradle.api.problems.ProblemDefinition
import org.gradle.api.problems.ProblemLocation
import org.jspecify.annotations.NullMarked
import java.io.Serializable

@NullMarked
class DefaultProblem(
    private val problemDefinition: ProblemDefinition,
    private val contextualLabel: String?,
    private val solutions: MutableList<String>,
    private val originLocations: MutableList<ProblemLocation>,
    private val contextualLocations: MutableList<ProblemLocation>,
    private val details: String?,
    private val exception: Throwable?,
    private val additionalData: AdditionalData?
) : Serializable, ProblemInternal {
    override fun getDefinition(): ProblemDefinition {
        return problemDefinition
    }

    override fun getContextualLabel(): String? {
        return contextualLabel
    }

    override fun getSolutions(): MutableList<String> {
        return solutions
    }

    override fun getDetails(): String? {
        return details
    }

    override fun getOriginLocations(): MutableList<ProblemLocation> {
        return originLocations
    }

    override fun getContextualLocations(): MutableList<ProblemLocation> {
        return contextualLocations
    }

    override fun getException(): Throwable? {
        return exception
    }

    override fun getAdditionalData(): AdditionalData? {
        return additionalData
    }

    override fun toBuilder(infrastructure: ProblemsInfrastructure): ProblemBuilderInternal {
        return DefaultProblemBuilder(this, infrastructure)
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val that = o as DefaultProblem
        return Objects.equal(problemDefinition, that.problemDefinition) &&
                Objects.equal(contextualLabel, that.contextualLabel) &&
                Objects.equal(solutions, that.solutions) &&
                Objects.equal(originLocations, that.originLocations) &&
                Objects.equal(details, that.details) &&
                Objects.equal(exception, that.exception) &&
                Objects.equal(additionalData, that.additionalData)
    }

    override fun hashCode(): Int {
        return Objects.hashCode(problemDefinition, contextualLabel, solutions, originLocations, details, exception, additionalData)
    }

    override fun toString(): String {
        return "DefaultProblem{" +
                "problemDefinition=" + problemDefinition +
                ", contextualLabel='" + contextualLabel + '\'' +
                ", solutions=" + solutions +
                ", originLocations=" + originLocations +
                ", contextualLocations=" + contextualLocations +
                ", details='" + details + '\'' +
                ", exception=" + (if (exception != null) exception.toString() else "null") +
                ", additionalData=" + additionalData +
                '}'
    }
}
