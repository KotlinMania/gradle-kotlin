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
package org.gradle.problems.internal.services

import org.gradle.api.problems.internal.DefaultProblemsSummaryProgressDetails
import org.gradle.api.problems.internal.ProblemEmitter
import org.gradle.api.problems.internal.ProblemInternal
import org.gradle.api.problems.internal.ProblemReportCreator
import org.gradle.api.problems.internal.ProblemSummarizer
import org.gradle.api.problems.internal.ProblemsInfrastructure
import org.gradle.api.problems.internal.TaskIdentityProvider
import org.gradle.internal.buildoption.InternalOption
import org.gradle.internal.buildoption.InternalOptions
import org.gradle.internal.operations.BuildOperationProgressEventEmitter
import org.gradle.internal.operations.CurrentBuildOperationRef
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.problems.buildtree.ProblemReporter
import java.io.File

class DefaultProblemSummarizer(
    private val eventEmitter: BuildOperationProgressEventEmitter,
    private val currentBuildOperationRef: CurrentBuildOperationRef,
    private val problemEmitters: MutableCollection<ProblemEmitter>,
    internalOptions: InternalOptions,
    private val problemReportCreator: ProblemReportCreator,
    private val taskProvider: TaskIdentityProvider
) : ProblemSummarizer {
    private val summarizerStrategy: SummarizerStrategy

    init {
        this.summarizerStrategy = SummarizerStrategy(internalOptions.getInt(THRESHOLD_OPTION))
    }

    override fun getId(): String {
        return "problem summarizer"
    }

    override fun report(reportDir: File, validationFailures: ProblemReporter.ProblemConsumer) {
        val cutOffProblems = summarizerStrategy.cutOffProblems
        problemReportCreator.createReportFile(reportDir, cutOffProblems)
        eventEmitter.emitNow(currentBuildOperationRef.getId(), DefaultProblemsSummaryProgressDetails(cutOffProblems))
    }

    override fun emit(problem: ProblemInternal, id: OperationIdentifier?) {
        var problem = problem
        if (summarizerStrategy.shouldEmit(problem)) {
            problem = maybeAddTaskLocation(problem, id)
            problemReportCreator.addProblem(problem)
            for (problemEmitter in problemEmitters) {
                problemEmitter.emit(problem, id)
            }
        }
    }

    private fun maybeAddTaskLocation(problem: ProblemInternal, id: OperationIdentifier?): ProblemInternal {
        var problem = problem
        val taskIdentity = taskProvider.taskIdentityFor(id!!)
        if (taskIdentity != null) {
            problem = problem.toBuilder(ProblemsInfrastructure(null, null, null, null, null, null))!!.taskLocation(taskIdentity.taskPath)!!.build()!!
        }
        return problem
    }

    companion object {
        val THRESHOLD_OPTION: InternalOption<Int> = InternalOptions.ofInt("org.gradle.internal.problem.summary.threshold", 15)
        val THRESHOLD_DEFAULT_VALUE: Int = THRESHOLD_OPTION.getDefaultValue()
    }
}
