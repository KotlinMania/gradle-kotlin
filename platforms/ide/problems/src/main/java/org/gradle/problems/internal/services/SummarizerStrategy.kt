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
package org.gradle.problems.internal.services

import com.google.common.collect.ImmutableList
import org.gradle.api.problems.ProblemId
import org.gradle.api.problems.internal.ProblemInternal
import org.gradle.api.problems.internal.ProblemSummaryData

class SummarizerStrategy(private val threshold: Int) {
    private val seenProblemsWithCounts: MutableMap<ProblemId, ProblemSummaryInfo> = HashMap<ProblemId, ProblemSummaryInfo>()

    @get:Synchronized
    val cutOffProblems: MutableList<ProblemSummaryData>
        get() = seenProblemsWithCounts.entries.stream()
            .filter { entry: MutableMap.MutableEntry<ProblemId, ProblemSummaryInfo> -> entry.value.count > threshold }
            .map<ProblemSummaryData> { entry: MutableMap.MutableEntry<ProblemId, ProblemSummaryInfo> ->
                ProblemSummaryData(
                    entry.key,
                    entry.value.count - threshold
                )
            }
            .collect(ImmutableList.toImmutableList<ProblemSummaryData>())

    @Synchronized
    fun shouldEmit(problem: ProblemInternal): Boolean {
        val summaryInfo = seenProblemsWithCounts.computeIfAbsent(
            problem.getDefinition()!!.getId()!!
        ) { ProblemSummaryInfo() }
        return summaryInfo.shouldEmit(problem.hashCode(), threshold)
    }
}
