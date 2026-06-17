/*
 * Copyright 2025 the original author or authors.
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
package org.gradle.operations.problems

/**
 * A problem from the problems API.
 *
 * @since 9.0.0
 */
interface Problem {
    /**
     * The problem definition, i.e. the data that is independent of the usage.
     *
     * @since 9.0.0
     */
    val definition: ProblemDefinition?

    /**
     * The severity of the problem.
     *
     * @since 9.0.0
     */
    val severity: ProblemSeverity?

    /**
     * Declares a short, but usage-dependent message for this problem.
     *
     * @return the contextual label, or `null` if not specified. Then the display name from the definition should be used.
     * @since 9.0.0
     */
    val contextualLabel: String?

    /**
     * Returns solutions and advice that contain context-sensitive data, e.g. the message contains references to variables, locations, etc.
     *
     * @since 9.0.0
     */
    val solutions: MutableList<String>?

    /**
     * A long description detailing the problem.
     *
     *
     * Details can elaborate on the problem, and provide more information about the problem.
     * They can be multiple lines long, but should not detail solutions; for that, use [.getSolutions].
     *
     *
     * `null` if no details have been specified.
     *
     * @since 9.0.0
     */
    val details: String?

    /**
     * Returns the locations where the problem originated.
     *
     *
     * Might be empty if the origin is not known.
     *
     * @since 9.0.0
     */
    val originLocations: MutableList<ProblemLocation>?

    /**
     * Returns additional locations, which can help to understand the problem further.
     *
     *
     * For example, if a problem was emitted during task execution, the task path will be available in this list.
     *
     *
     * Might be empty if there is no meaningful contextual information.
     *
     * @since 9.0.0
     */
    val contextualLocations: MutableList<ProblemLocation>?
}
