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

/**
 * Describes a problem event.
 *
 *
 *
 * Provides all details provided for problem events. The events are generated via the Problems API.
 *
 * @since 8.8
 */
@Incubating
interface ProblemContext {
    /**
     * Returns the details string.
     *
     * @return the problem details
     * @since 8.8
     */
    val details: Details?

    /**
     * Returns the locations where the problem originated.
     *
     *
     * Might be empty if the origin is not known.
     *
     * @return the origin locations
     * @since 8.12
     */
    val originLocations: MutableList<Location>?

    /**
     * Returns additional locations, which can help to understand the problem further.
     *
     *
     * Might be empty if there is no meaningful contextual information.
     *
     * @return the contextual locations
     * @since 8.12
     */
    val contextualLocations: MutableList<Location>?

    /**
     * Returns the list of solutions.
     *
     * @return the solutions
     * @since 8.8
     */
    val solutions: MutableList<Solution>?

    /**
     * Returns the failure associated with this problem.
     * <br></br>
     * `null` if run against a Gradle version prior to 8.7
     *
     * @return the failure
     * @since 8.8
     */
    val failure: Failure?
}
