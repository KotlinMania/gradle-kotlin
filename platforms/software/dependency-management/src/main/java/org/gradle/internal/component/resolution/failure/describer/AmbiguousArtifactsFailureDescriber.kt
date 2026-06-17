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
import org.gradle.internal.component.resolution.failure.type.AmbiguousArtifactsFailure
import org.gradle.internal.logging.text.TreeFormatter
import javax.inject.Inject

/**
 * A [ResolutionFailureDescriber] that describes an [AmbiguousVariantsFailure].
 */
abstract class AmbiguousArtifactsFailureDescriber @Inject constructor(
    private val attributeDescribers: AttributeDescriberRegistry
) : AbstractResolutionFailureDescriber<AmbiguousArtifactsFailure?>() {
    override fun describeFailure(failure: AmbiguousArtifactsFailure): ArtifactSelectionException {
        val describer = AttributeDescriberSelector.selectDescriber(failure.getRequestedAttributes(), attributeDescribers.getDescribers())
        val message = buildFailureMsg(failure, describer)
        val resolutions = buildResolutions(suggestSpecificDocumentation(AMBIGUOUS_VARIANTS_PREFIX, AMBIGUOUS_VARIANTS_SECTION), suggestReviewAlgorithm())
        return ArtifactSelectionException(message, failure, resolutions)
    }

    private fun buildFailureMsg(failure: AmbiguousArtifactsFailure, describer: AttributeDescriber): String {
        val formatter = TreeFormatter()
        if (failure.getRequestedAttributes().isEmpty()) {
            formatter.node("More than one variant of " + failure.describeRequestTarget() + " matches the consumer attributes")
        } else {
            formatter.node(
                "The consumer was configured to find " + describer.describeAttributeSet(
                    failure.getRequestedAttributes().asMap()
                ) + ". However we cannot choose between the following variants of " + failure.describeRequestTarget()
            )
        }
        formatter.startChildren()
        for (assessedCandidate in failure.getCandidates()) {
            val candidateName = assessedCandidate.getDisplayName()
            formatter.node(candidateName)
            formatAttributeMatchesForAmbiguity(assessedCandidate, formatter, describer)
        }
        formatter.endChildren()
        return formatter.toString()
    }

    companion object {
        private const val AMBIGUOUS_VARIANTS_PREFIX = "Ambiguity errors are explained in more detail at "
        private const val AMBIGUOUS_VARIANTS_SECTION = "sub:variant-ambiguity"
    }
}
