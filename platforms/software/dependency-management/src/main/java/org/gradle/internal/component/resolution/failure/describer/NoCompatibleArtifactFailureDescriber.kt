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
import org.gradle.internal.component.resolution.failure.exception.ArtifactSelectionException
import org.gradle.internal.component.resolution.failure.type.NoCompatibleArtifactFailure
import org.gradle.internal.exceptions.StyledException
import org.gradle.internal.logging.text.StyledTextOutput
import org.gradle.internal.logging.text.TreeFormatter
import javax.inject.Inject

/**
 * A [ResolutionFailureDescriber] that describes an [NoCompatibleArtifactFailure].
 */
abstract class NoCompatibleArtifactFailureDescriber @Inject constructor(
    private val attributeDescribers: AttributeDescriberRegistry
) : AbstractResolutionFailureDescriber<NoCompatibleArtifactFailure?>() {
    override fun describeFailure(failure: NoCompatibleArtifactFailure): ArtifactSelectionException {
        val message = buildFailureMsg(failure, attributeDescribers.getDescribers())
        val resolutions = buildResolutions(suggestSpecificDocumentation(NO_MATCHING_VARIANTS_PREFIX, NO_MATCHING_VARIANTS_SECTION), suggestReviewAlgorithm())
        return ArtifactSelectionException(message, failure, resolutions)
    }

    private fun buildFailureMsg(failure: NoCompatibleArtifactFailure, attributeDescribers: MutableList<AttributeDescriber>): String {
        val describer = AttributeDescriberSelector.selectDescriber(failure.getRequestedAttributes(), attributeDescribers)
        val formatter = TreeFormatter()
        formatter.node("No variants of " + StyledException.style(StyledTextOutput.Style.Info, failure.describeRequestTarget()) + " match the consumer attributes")
        formatter.startChildren()
        for (assessedCandidate in failure.getCandidates()) {
            formatter.node(assessedCandidate.getDisplayName())
            formatAttributeMatchesForIncompatibility(assessedCandidate, formatter, describer)
        }
        formatter.endChildren()
        return formatter.toString()
    }

    companion object {
        private const val NO_MATCHING_VARIANTS_PREFIX = "No matching variant errors are explained in more detail at "
        private const val NO_MATCHING_VARIANTS_SECTION = "sub:variant-no-match"
    }
}
