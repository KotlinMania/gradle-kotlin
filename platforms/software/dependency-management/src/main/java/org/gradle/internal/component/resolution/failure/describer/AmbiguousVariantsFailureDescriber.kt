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
package org.gradle.internal.component.resolution.failure.describer

import org.gradle.api.internal.attributes.AttributeDescriber
import org.gradle.api.internal.attributes.AttributeDescriberRegistry
import org.gradle.internal.component.model.AttributeDescriberSelector
import org.gradle.internal.component.resolution.failure.ResolutionCandidateAssessor
import org.gradle.internal.component.resolution.failure.exception.VariantSelectionByAttributesException
import org.gradle.internal.component.resolution.failure.formatting.CapabilitiesDescriber
import org.gradle.internal.component.resolution.failure.type.AmbiguousVariantsFailure
import org.gradle.internal.exceptions.StyledException
import org.gradle.internal.logging.text.StyledTextOutput
import org.gradle.internal.logging.text.TreeFormatter
import java.util.TreeMap
import javax.inject.Inject

/**
 * A [ResolutionFailureDescriber] that describes a generic [AmbiguousVariantsFailure].
 */
abstract class AmbiguousVariantsFailureDescriber @Inject constructor(
    private val attributeDescribers: AttributeDescriberRegistry
) : AbstractResolutionFailureDescriber<AmbiguousVariantsFailure>() {
    override fun describeFailure(failure: AmbiguousVariantsFailure): VariantSelectionByAttributesException {
        val message = buildFailureMsg(failure, attributeDescribers.getDescribers())
        val resolutions = buildResolutions(suggestSpecificDocumentation(AMBIGUOUS_VARIANTS_PREFIX, AMBIGUOUS_VARIANTS_SECTION), suggestReviewAlgorithm())
        return VariantSelectionByAttributesException(message, failure, resolutions)
    }

    protected open fun buildFailureMsg(failure: AmbiguousVariantsFailure, attributeDescribers: MutableList<AttributeDescriber>): String {
        val describer = AttributeDescriberSelector.selectDescriber(failure.getRequestedAttributes(), attributeDescribers)
        val formatter = TreeFormatter()
        val ambiguousVariants = summarizeAmbiguousVariants(failure, describer, formatter, true)

        // We're sorting the names of the variants and later attributes
        // to make sure the output is consistently the same between invocations
        formatter.startChildren()
        for (assessedCandidate in ambiguousVariants.values) {
            formatUnselectable(assessedCandidate, formatter, describer)
        }
        formatter.endChildren()

        return formatter.toString()
    }

    protected fun summarizeAmbiguousVariants(
        failure: AmbiguousVariantsFailure,
        describer: AttributeDescriber,
        formatter: TreeFormatter,
        listAvailableVariants: Boolean
    ): MutableMap<String, ResolutionCandidateAssessor.AssessedCandidate> {
        val ambiguousVariants: MutableMap<String, ResolutionCandidateAssessor.AssessedCandidate> = TreeMap<String, ResolutionCandidateAssessor.AssessedCandidate>()
        for (candidate in failure.getCandidates()) {
            ambiguousVariants.put(candidate.getDisplayName(), candidate)
        }
        if (failure.getRequestedAttributes().isEmpty()) {
            formatter.node("Cannot choose between the available variants of ")
        } else {
            var node = "The consumer was configured to find " + describer.describeAttributeSet(failure.getRequestedAttributes().asMap())
            if (listAvailableVariants) {
                node = node + ". However we cannot choose between the following variants of "
            } else {
                node = node + ". There are several available matching variants of "
            }
            formatter.node(node)
        }
        formatter.append(StyledException.style(StyledTextOutput.Style.Info, failure.describeRequestTarget()))
        if (listAvailableVariants) {
            formatter.startChildren()
            for (configuration in ambiguousVariants.keys) {
                formatter.node(configuration)
            }
            formatter.endChildren()
            formatter.node("All of them match the consumer attributes")
        }
        return ambiguousVariants
    }

    private fun formatUnselectable(
        assessedCandidate: ResolutionCandidateAssessor.AssessedCandidate,
        formatter: TreeFormatter,
        describer: AttributeDescriber
    ) {
        formatter.node("Variant '")
        formatter.append(assessedCandidate.getDisplayName())
        formatter.append("'")
        formatter.append(" " + CapabilitiesDescriber.describeCapabilitiesWithTitle(assessedCandidate.getCandidateCapabilities().asSet()))

        formatAttributeMatchesForAmbiguity(assessedCandidate, formatter, describer)
    }

    companion object {
        private const val AMBIGUOUS_VARIANTS_PREFIX = "Ambiguity errors are explained in more detail at "
        private const val AMBIGUOUS_VARIANTS_SECTION = "sub:variant-ambiguity"
    }
}
