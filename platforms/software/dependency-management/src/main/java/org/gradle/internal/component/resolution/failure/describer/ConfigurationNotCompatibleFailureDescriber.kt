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
import org.gradle.internal.component.resolution.failure.exception.VariantSelectionByNameException
import org.gradle.internal.component.resolution.failure.type.ConfigurationNotCompatibleFailure
import org.gradle.internal.exceptions.StyledException
import org.gradle.internal.logging.text.StyledTextOutput
import org.gradle.internal.logging.text.TreeFormatter
import javax.inject.Inject

/**
 * A [ResolutionFailureDescriber] that describes an [ConfigurationNotCompatibleFailure].
 */
abstract class ConfigurationNotCompatibleFailureDescriber @Inject constructor(
    private val attributeDescribers: AttributeDescriberRegistry
) : AbstractResolutionFailureDescriber<ConfigurationNotCompatibleFailure?>() {
    override fun describeFailure(failure: ConfigurationNotCompatibleFailure): VariantSelectionByNameException {
        val describer = AttributeDescriberSelector.selectDescriber(failure.getRequestedAttributes(), attributeDescribers.getDescribers())
        val message = buildFailureMsg(failure, describer)
        val resolutions = buildResolutions(suggestSpecificDocumentation(INCOMPATIBLE_VARIANTS_PREFIX, INCOMPATIBLE_VARIANTS_SECTION), suggestReviewAlgorithm())
        return VariantSelectionByNameException(message, failure, resolutions)
    }

    private fun buildFailureMsg(
        failure: ConfigurationNotCompatibleFailure,
        describer: AttributeDescriber
    ): String {
        val assessedCandidate = failure.getCandidates().get(0)
        val formatter = TreeFormatter()
        val candidateName = assessedCandidate.getDisplayName()
        formatter.node(
            "Configuration '" + candidateName + "' in " + StyledException.style(
                StyledTextOutput.Style.Info,
                failure.getTargetComponent().getDisplayName()
            ) + " does not match the consumer attributes"
        )
        formatUnselectable(assessedCandidate, formatter, describer)
        return formatter.toString()
    }

    private fun formatUnselectable(
        assessedCandidate: ResolutionCandidateAssessor.AssessedCandidate,
        formatter: TreeFormatter,
        describer: AttributeDescriber
    ) {
        formatter.node("Configuration '")
        formatter.append(assessedCandidate.getDisplayName())
        formatter.append("'")

        formatAttributeMatchesForIncompatibility(assessedCandidate, formatter, describer)
    }

    companion object {
        private const val INCOMPATIBLE_VARIANTS_PREFIX = "Incompatible variant errors are explained in more detail at "
        private const val INCOMPATIBLE_VARIANTS_SECTION = "sub:variant-incompatible"
    }
}
