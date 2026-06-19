/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.api.problems.AdditionalData
import org.gradle.api.problems.Problem
import org.gradle.api.problems.ProblemDefinition
import org.gradle.api.problems.ProblemLocation

interface ProblemInternal : Problem {
    /**
     * Returns a problem builder with fields initialized with values from this instance.
     */
    fun toBuilder(infrastructure: ProblemsInfrastructure): ProblemBuilderInternal?

    /**
     * Returns the problem definition, i.e. the data that is independent of the report context.
     */
    fun getDefinition(): ProblemDefinition?

    /**
     * Declares a short, but context-dependent message for this problem.
     *
     */
    fun getContextualLabel(): String?

    /**
     * Returns solutions and advice that contain context-sensitive data, e.g. the message contains references to variables, locations, etc.
     */
    fun getSolutions(): MutableList<String>?

    /**
     * A long description detailing the problem.
     *
     *
     * Details can elaborate on the problem, and provide more information about the problem.
     * They can be multiple lines long, but should not detail solutions; for that, use [.getSolutions].
     */
    fun getDetails(): String?

    /**
     * Returns the locations where the problem originated.
     *
     *
     * Might be empty if the origin is not known.
     */
    fun getOriginLocations(): MutableList<ProblemLocation>?

    /**
     * Returns additional locations, which can help to understand the problem further.
     *
     *
     * For example, if a problem was emitted during task execution, the task path will be available in this list.
     *
     *
     * Might be empty if there is no meaningful contextual information.
     */
    fun getContextualLocations(): MutableList<ProblemLocation>?

    /**
     * The exception that caused the problem.
     */
    fun getException(): Throwable?

    /**
     * Additional data attached to the problem.
     *
     *
     * The supported types are listed on [AdditionalData].
     */
    fun getAdditionalData(): AdditionalData?
}
