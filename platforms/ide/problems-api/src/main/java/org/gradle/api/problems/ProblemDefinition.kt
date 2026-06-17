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
package org.gradle.api.problems

import org.gradle.api.Incubating

/**
 * Describes a specific problem without context.
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
 * The category and the label uniquely identify the problem definition, the remaining fields only supply additional information.
 *
 * @since 8.13
 */
@Incubating
interface ProblemDefinition {
    /**
     * The problem id.
     *
     * @since 8.13
     */
    @JvmField
    val id: ProblemId?

    /**
     * Problem severity.
     *
     *
     * The severity of a problem is a hint to the user about how important the problem is.
     * ERROR will fail the build, WARNING will not.
     *
     * @since 8.13
     */
    @JvmField
    val severity: Severity?

    /**
     * A link to the documentation for this problem.
     *
     * @since 8.13
     */
    @JvmField
    val documentationLink: DocLink?
}
