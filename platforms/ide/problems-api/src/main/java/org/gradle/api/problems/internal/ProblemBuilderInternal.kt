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
import org.gradle.api.problems.Severity

interface ProblemBuilderInternal : ProblemSpecInternal {
    /**
     * Creates the new problem. Calling this method won't report the problem via build operations, it can be done separately by calling [ProblemReporterInternal.report].
     *
     * @return the new problem
     */
    fun build(): ProblemInternal?

    override fun id(problemId: ProblemId): ProblemBuilderInternal?

    override fun id(name: String, displayName: String, parent: ProblemGroup): ProblemBuilderInternal?

    override fun taskLocation(buildTreePath: String): ProblemBuilderInternal?

    override fun documentedAt(doc: DocLink?): ProblemBuilderInternal?

    override fun contextualLabel(contextualLabel: String): ProblemBuilderInternal?

    override fun documentedAt(url: String): ProblemBuilderInternal?

    override fun fileLocation(path: String): ProblemBuilderInternal?

    override fun lineInFileLocation(path: String, line: Int): ProblemBuilderInternal?

    override fun lineInFileLocation(path: String, line: Int, column: Int, length: Int): ProblemBuilderInternal?

    override fun offsetInFileLocation(path: String, offset: Int, length: Int): ProblemBuilderInternal?

    override fun stackLocation(): ProblemBuilderInternal?

    override fun details(details: String): ProblemBuilderInternal?

    override fun solution(solution: String): ProblemBuilderInternal?

    override fun <U : AdditionalDataSpec?> additionalDataInternal(specType: Class<out U>, config: Action<in U?>): ProblemBuilderInternal?

    // interface should be public <T> void additionalData(Class<T> type, Action<? super T> config)
    override fun <T : AdditionalData> additionalData(type: Class<T>, config: Action<in T>): ProblemBuilderInternal?

    override fun withException(t: Throwable): ProblemBuilderInternal?

    @Deprecated("")
    override fun severity(severity: Severity): ProblemBuilderInternal?

    fun internalSeverity(severity: Severity): ProblemBuilderInternal?
    fun getInfrastructure(): ProblemsInfrastructure?
}
