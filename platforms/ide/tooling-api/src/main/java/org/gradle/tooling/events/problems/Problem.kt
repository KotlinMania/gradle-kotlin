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
package org.gradle.tooling.events.problems

import org.gradle.api.Incubating
import org.gradle.tooling.Failure


/**
 * A problem report
 *
 * @since 8.12
 */
@Incubating
interface Problem {
    /**
     * Returns the problem definition.
     *
     * @return the definition
     * @since 8.12
     */
    val definition: ProblemDefinition?

    /**
     * Returns the contextual label.
     *
     * @return the problem label
     * @since 8.12
     */
    val contextualLabel: ContextualLabel?

    /**
     * Returns the details string.
     *
     * @return the problem details
     * @since 8.12
     */
    val details: Details?

    /**
     * Returns the locations where the problem originated.
     *
     * @return the locations
     * @since 8.13
     */
    val originLocations: MutableList<Location>?

    /**
     * Returns additional locations, which can help to understand the problem further.
     *
     *
     * For example, if a problem was emitted during task execution, the task path will be available in this list.
     *
     *
     * Might be empty if there is no meaningful contextual information.
     *
     * @return the locations
     * @since 8.13
     */
    val contextualLocations: MutableList<Location>?

    /**
     * Returns the list of solutions.
     *
     * @return the solutions
     * @since 8.12
     */
    val solutions: MutableList<Solution>?

    /**
     * Returns the failure associated with this problem.
     *
     * @return the failure
     * @since 8.12
     */
    val failure: Failure?

    /**
     * Returns the additional data associated with this problem.
     *
     * There are 2 possible types for additional data:
     *
     *  * [CustomAdditionalData] - custom additional data attached to a problem by using [org.gradle.api.problems.ProblemSpec.additionalData].
     *  * [AdditionalData] - additional data attached to the problem.
     *
     *
     * to determine the type of additional data `instanceof` can be used.
     *
     * @return the additional data
     * @since 8.12
     */
    val additionalData: AdditionalData?
}
