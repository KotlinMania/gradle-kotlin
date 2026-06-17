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

import com.google.common.collect.Lists
import org.gradle.api.attributes.Attribute
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.component.resolution.failure.exception.GraphValidationException
import org.gradle.internal.component.resolution.failure.type.IncompatibleMultipleNodesValidationFailure
import java.util.function.Function

/**
 * A [ResolutionFailureDescriber] that describes an [IncompatibleMultipleNodesValidationFailure].
 */
abstract class IncompatibleMultipleNodesValidationFailureDescriber : AbstractResolutionFailureDescriber<IncompatibleMultipleNodesValidationFailure>() {
    override fun describeFailure(failure: IncompatibleMultipleNodesValidationFailure): GraphValidationException {
        val msg = buildIncompatibleArtifactVariantsFailureMsg(failure)
        val resolutions = buildResolutions(suggestSpecificDocumentation(INCOMPATIBLE_VARIANTS_PREFIX, INCOMPATIBLE_VARIANTS_SECTION), suggestReviewAlgorithm())
        return GraphValidationException(msg, failure, resolutions)
    }

    private fun buildIncompatibleArtifactVariantsFailureMsg(failure: IncompatibleMultipleNodesValidationFailure): String {
        val sb = StringBuilder("Multiple incompatible variants of ")
            .append(failure.describeRequestTarget())
            .append(" were selected:\n")
        for (assessedCandidate in failure.getAssessedCandidates()) {
            sb.append("   - Variant ").append(assessedCandidate.getDisplayName()).append(" has attributes ")
            formatAttributes(sb, assessedCandidate.getAllCandidateAttributes())
            sb.append("\n")
        }
        return sb.toString()
    }

    private fun formatAttributes(sb: StringBuilder, attributes: ImmutableAttributes) {
        val keySet = attributes.keySet()
        val sorted: MutableList<Attribute<*>> = Lists.newArrayList<Attribute<*>>(keySet)
        sorted.sort(Comparator.comparing<Attribute<*>, String>(Function { obj: Attribute<*> -> obj.getName() }))
        var space = false
        sb.append("{")
        for (attribute in sorted) {
            if (space) {
                sb.append(", ")
            }
            sb.append(attribute.getName()).append("=").append(attributes.getAttribute(attribute))
            space = true
        }
        sb.append("}")
    }

    companion object {
        private const val INCOMPATIBLE_VARIANTS_PREFIX = "Incompatible variant errors are explained in more detail at "
        private const val INCOMPATIBLE_VARIANTS_SECTION = "sub:variant-incompatible"
    }
}
