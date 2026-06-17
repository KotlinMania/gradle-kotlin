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

import org.gradle.api.problems.ProblemReporter
import org.gradle.internal.exception.ExceptionAnalyser
import org.gradle.internal.isolation.IsolatableFactory
import org.gradle.internal.operations.CurrentBuildOperationRef
import org.gradle.internal.reflect.Instantiator
import org.gradle.problems.buildtree.ProblemStream
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer

class DefaultProblems(
    private val problemSummarizer: ProblemSummarizer,
    problemStream: ProblemStream,
    private val currentBuildOperationRef: CurrentBuildOperationRef,
    private val exceptionProblemRegistry: ExceptionProblemRegistry,
    private val exceptionAnalyser: ExceptionAnalyser,
    instantiator: Instantiator,
    payloadSerializer: PayloadSerializer,
    isolatableFactory: IsolatableFactory,
    isolatableSerializer: IsolatableToBytesSerializer
) : ProblemsInternal {
    private val reporterInternal: ProblemReporterInternal
    private val infrastructure: ProblemsInfrastructure

    init {
        this.infrastructure = ProblemsInfrastructure(AdditionalDataBuilderFactory(), instantiator, payloadSerializer, isolatableFactory, isolatableSerializer, problemStream)
        this.reporterInternal = createReporter()
    }

    override fun getReporter(): ProblemReporter {
        return createReporter()
    }

    private fun createReporter(): DefaultProblemReporter {
        return DefaultProblemReporter(
            problemSummarizer,
            currentBuildOperationRef,
            exceptionProblemRegistry,
            exceptionAnalyser,
            infrastructure
        )
    }

    override fun getInternalReporter(): ProblemReporterInternal {
        return reporterInternal
    }

    override fun getInfrastructure(): ProblemsInfrastructure {
        return infrastructure
    }

    override fun getProblemBuilder(): ProblemBuilderInternal {
        return DefaultProblemBuilder(infrastructure)
    }
}
