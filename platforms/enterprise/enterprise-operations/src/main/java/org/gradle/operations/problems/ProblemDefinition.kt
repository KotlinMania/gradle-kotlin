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
 * Describes a specific problem without a concrete usage.
 *
 *
 * For example, in the domain of Java compilation problems, an unused variable warning could be described as such:
 *
 *  * group: compilation:java
 *  * unused variable
 *  * severity: WARNING
 *  * ...
 *
 *
 *
 * The group and the name uniquely identify the problem definition, the remaining fields only supply additional information.
 *
 * @since 8.14
 */
interface ProblemDefinition {
    /**
     * The name of the problem.
     *
     *
     * The name should be used to categorize a set of problems.
     * The name itself does not need to be unique, the uniqueness is determined the name and the group hierarchy.
     *
     * @since 8.14
     */
    val name: String?

    /**
     * A human-readable label describing the problem ID.
     *
     *
     * The display name should be used to present the problem to the user.
     *
     * @since 8.14
     */
    val displayName: String?

    /**
     * The group of the problem.
     *
     * @since 8.14
     */
    val group: ProblemGroup?

    /**
     * A link to the documentation for this problem.
     *
     * @since 8.14
     */
    val documentationLink: DocumentationLink?
}
