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
package org.gradle.internal.component.resolution.failure.type

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import org.gradle.api.internal.catalog.problems.ResolutionFailureProblemId
import org.gradle.internal.component.model.ComponentGraphResolveMetadata
import org.gradle.internal.component.model.VariantGraphResolveMetadata
import org.gradle.internal.component.resolution.failure.ResolutionCandidateAssessor
import org.gradle.internal.component.resolution.failure.interfaces.GraphNodesValidationFailure

/**
 * A [GraphNodesValidationFailure] that represents the situation when multiple incompatible variants of a single component
 * are selected during a request.
 */
class IncompatibleMultipleNodesValidationFailure(
    private val selectedComponent: ComponentGraphResolveMetadata,
    incompatibleNodes: MutableSet<VariantGraphResolveMetadata>,
    assessedCandidates: MutableList<ResolutionCandidateAssessor.AssessedCandidate>
) : AbstractResolutionFailure(ResolutionFailureProblemId.INCOMPATIBLE_MULTIPLE_NODES), GraphNodesValidationFailure {
    private val incompatibleNodes: MutableSet<VariantGraphResolveMetadata>
    private val assessedCandidates: ImmutableList<ResolutionCandidateAssessor.AssessedCandidate>

    init {
        this.incompatibleNodes = ImmutableSet.copyOf<VariantGraphResolveMetadata>(incompatibleNodes)
        this.assessedCandidates = ImmutableList.copyOf<ResolutionCandidateAssessor.AssessedCandidate>(assessedCandidates)
    }

    override fun describeRequestTarget(): String {
        return selectedComponent.getModuleVersionId().toString()
    }

    override fun getFailingComponent(): ComponentGraphResolveMetadata {
        return selectedComponent
    }

    override fun getFailingNodes(): MutableSet<VariantGraphResolveMetadata> {
        return incompatibleNodes
    }

    fun getAssessedCandidates(): ImmutableList<ResolutionCandidateAssessor.AssessedCandidate> {
        return assessedCandidates
    }
}
