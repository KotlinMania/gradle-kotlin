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
import org.gradle.api.problems.AdditionalData
import org.gradle.api.problems.DocLink
import org.gradle.api.problems.ProblemGroup
import org.gradle.api.problems.ProblemId
import org.gradle.api.problems.ProblemSpec
import org.gradle.api.problems.Severity
import org.gradle.problems.ProblemDiagnostics

interface ProblemSpecInternal : ProblemSpec {
    /**
     * Attaches additional data describing the problem.
     *
     *
     * Only the types listed for [AdditionalData] can be used as arguments, otherwise an invalid problem report will be created.
     *
     *
     * If not additional data was configured for this problem, then a new instance will be created. If additional data was already configured, then the existing instance will be used and the configuration will be applied to it.
     *
     * @param specType the type of the additional data configurer (see the AdditionalDataSpec interface for the list of supported types)
     * @param config  The action configuring the additional data
     * @return this
     * @param <U> The type of the configurator object that will be applied to the additional data
    </U> */
    fun <U : AdditionalDataSpec?> additionalDataInternal(specType: Class<out U>, config: Action<in U?>): ProblemSpecInternal?

    override fun <T : AdditionalData> additionalData(type: Class<T>, config: Action<in T>): ProblemSpecInternal?

    /**
     * Declares that this problem was emitted by a task with the given path.
     *
     * @param buildTreePath the absolute path of the task within the build tree
     * @return this
     */
    fun taskLocation(buildTreePath: String): ProblemSpecInternal?

    /**
     * Declares the documentation for this problem.
     *
     * @return this
     */
    fun documentedAt(doc: DocLink?): ProblemSpecInternal?

    /**
     * Defines the context-independent identifier for this problem.
     *
     *
     * It is a mandatory property to configure when emitting a problem with [ProblemReporter].
     * ProblemId instances can be created via [ProblemId.create].
     *
     * @param problemId the problem id
     * @return this
     */
    fun id(problemId: ProblemId): ProblemSpecInternal?

    /**
     * Defines simple identification for this problem.
     *
     *
     * It is a mandatory property to configure when emitting a problem with [ProblemReporter].
     *
     * @param name the name of the problem. As a convention kebab-case-formatting should be used.
     * @param displayName a human-readable representation of the problem, free of any contextual information.
     * @param parent the container problem group.
     * @return this
     * @since 8.8
     */
    fun id(name: String, displayName: String, parent: ProblemGroup): ProblemSpecInternal?

    override fun contextualLabel(contextualLabel: String): ProblemSpecInternal?

    override fun documentedAt(url: String): ProblemSpecInternal?

    override fun fileLocation(path: String): ProblemSpecInternal?

    override fun lineInFileLocation(path: String, line: Int): ProblemSpecInternal?

    override fun lineInFileLocation(path: String, line: Int, column: Int): ProblemSpecInternal?

    override fun lineInFileLocation(path: String, line: Int, column: Int, length: Int): ProblemSpecInternal?

    override fun offsetInFileLocation(path: String, offset: Int, length: Int): ProblemSpecInternal?

    override fun stackLocation(): ProblemSpecInternal?

    override fun details(details: String): ProblemSpecInternal?

    override fun solution(solution: String): ProblemSpecInternal?

    override fun withException(t: Throwable): ProblemSpecInternal?

    @Deprecated("")
    override fun severity(severity: Severity): ProblemSpecInternal?

    /**
     * The diagnostics to determine the stacktrace and the location of the problem.
     *
     *
     * We pass this in when we already have a diagnostics object, for example for deprecation warnings.
     */
    fun diagnostics(diagnostics: ProblemDiagnostics): ProblemSpecInternal?
}
