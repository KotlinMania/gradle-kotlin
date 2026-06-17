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

import org.gradle.api.artifacts.capability.CapabilitySelector
import org.gradle.api.internal.attributes.AttributeDescriber
import org.gradle.api.internal.attributes.AttributeDescriberRegistry
import org.gradle.internal.component.model.AttributeDescriberSelector
import org.gradle.internal.component.resolution.failure.ResolutionCandidateAssessor
import org.gradle.internal.component.resolution.failure.exception.VariantSelectionByAttributesException
import org.gradle.internal.component.resolution.failure.formatting.StyledAttributeDescriber
import org.gradle.internal.component.resolution.failure.type.NoCompatibleVariantsFailure
import org.gradle.internal.exceptions.StyledException
import org.gradle.internal.logging.text.StyledTextOutput
import org.gradle.internal.logging.text.TreeFormatter
import java.util.stream.Collectors
import javax.inject.Inject

/**
 * A [ResolutionFailureDescriber] that describes an [NoCompatibleVariantsFailure].
 */
abstract class NoCompatibleVariantsFailureDescriber @Inject constructor(
    private val attributeDescribers: AttributeDescriberRegistry
) : AbstractResolutionFailureDescriber<NoCompatibleVariantsFailure>() {
    override fun describeFailure(failure: NoCompatibleVariantsFailure): VariantSelectionByAttributesException {
        val describer = AttributeDescriberSelector.selectDescriber(failure.getRequestedAttributes(), attributeDescribers.getDescribers())
        val failureSubType: FailureSubType = FailureSubType.Companion.determineFailureSubType(failure)
        val message = buildFailureMsg(StyledAttributeDescriber(describer), failure, failureSubType)
        val resolutions = buildResolutions(failureSubType)
        return VariantSelectionByAttributesException(message, failure, resolutions)
    }

    private fun buildFailureMsg(describer: StyledAttributeDescriber, failure: NoCompatibleVariantsFailure, failureSubType: FailureSubType): String {
        val formatter = TreeFormatter()
        val styedComponentName = StyledException.style(StyledTextOutput.Style.Info, failure.describeRequestTarget())
        if (failure.getRequestedAttributes().isEmpty()) {
            formatter.node("Unable to find a matching variant of " + styedComponentName)
        } else {
            var requestTarget = styedComponentName
            val selectors = failure.getCapabilitySelectors()
            if (selectors.size == 1) {
                val selector = selectors.iterator().next()
                requestTarget += " with capability " + StyledException.style(StyledTextOutput.Style.Info, selector.getDisplayName())
            } else if (selectors.size > 1) {
                val styledSelectors = selectors.stream()
                    .map<String> { it: CapabilitySelector? -> StyledException.style(StyledTextOutput.Style.Info, it!!.getDisplayName()) }
                    .sorted()
                    .collect(Collectors.joining(", "))
                requestTarget += " with capabilities [" + styledSelectors + "]"
            }
            formatter.node(
                "No matching variant of " + requestTarget + " was found. The consumer was configured to find " + describer.describeAttributeSet(
                    failure.getRequestedAttributes().asMap()
                ) + " but:"
            )
        }

        // TODO: We should split the candidate variants into those that match the requested capabilities and those that do not.
        //       The use likely cares about only variants that match the requested capabilities.
        formatter.startChildren()
        when (failureSubType) {
            FailureSubType.NO_VARIANTS_EXIST -> formatter.node("No variants exist.")
            FailureSubType.NO_VARIANTS_HAVE_ATTRIBUTES -> formatter.node("None of the variants have attributes.")
            FailureSubType.NO_VARIANT_MATCHES_REQUESTED_ATTRIBUTES ->                 // We're sorting the names of the configurations and later attributes
                // to make sure the output is consistently the same between invocations
                for (candidate in failure.getCandidates()) {
                    formatUnselectableVariant(candidate, formatter, describer)
                }

            else -> throw IllegalStateException("Unknown failure sub type: " + failureSubType)
        }
        formatter.endChildren()

        return formatter.toString()
    }

    private fun buildResolutions(failureSubType: FailureSubType): MutableList<String> {
        if (failureSubType == FailureSubType.NO_VARIANTS_EXIST) {
            val suggestReviewCreatingConsumableConfigs: String = NO_VARIANTS_EXIST_PREFIX + getDocumentationRegistry().getDocumentationFor("declaring_dependencies", NO_VARIANTS_EXIST_SECTION) + "."
            return buildResolutions(suggestReviewCreatingConsumableConfigs, suggestReviewAlgorithm())
        } else {
            return buildResolutions(suggestSpecificDocumentation(NO_MATCHING_VARIANTS_PREFIX, NO_MATCHING_VARIANTS_SECTION), suggestReviewAlgorithm())
        }
    }

    private fun formatUnselectableVariant(
        assessedCandidate: ResolutionCandidateAssessor.AssessedCandidate,
        formatter: TreeFormatter,
        describer: AttributeDescriber
    ) {
        formatter.node("Variant '")
        formatter.append(assessedCandidate.getDisplayName())
        formatter.append("'")
        formatAttributeMatchesForIncompatibility(assessedCandidate, formatter, describer)
    }

    private enum class FailureSubType {
        NO_VARIANTS_EXIST,
        NO_VARIANTS_HAVE_ATTRIBUTES,
        NO_VARIANT_MATCHES_REQUESTED_ATTRIBUTES;

        companion object {
            fun determineFailureSubType(failure: NoCompatibleVariantsFailure): FailureSubType {
                if (failure.getCandidates().isEmpty()) {
                    return FailureSubType.NO_VARIANTS_EXIST
                }
                if (failure.noCandidatesHaveAttributes()) {
                    return FailureSubType.NO_VARIANTS_HAVE_ATTRIBUTES
                } else {
                    return FailureSubType.NO_VARIANT_MATCHES_REQUESTED_ATTRIBUTES
                }
            }
        }
    }

    companion object {
        private const val NO_MATCHING_VARIANTS_PREFIX = "No matching variant errors are explained in more detail at "
        private const val NO_MATCHING_VARIANTS_SECTION = "sub:variant-no-match"
        private const val NO_VARIANTS_EXIST_PREFIX = "Creating consumable variants is explained in more detail at "
        private const val NO_VARIANTS_EXIST_SECTION = "sec:resolvable-consumable-configs"
    }
}
