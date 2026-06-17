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
import org.gradle.internal.component.resolution.failure.exception.VariantSelectionByAttributesException
import org.gradle.internal.component.resolution.failure.formatting.CapabilitiesDescriber
import org.gradle.internal.component.resolution.failure.type.NoVariantsWithMatchingCapabilitiesFailure
import java.util.stream.Collectors

/**
 * A [ResolutionFailureDescriber] that describes a [NoVariantsWithMatchingCapabilitiesFailure].
 */
abstract class NoVariantsWithMatchingCapabilitiesFailureDescriber : AbstractResolutionFailureDescriber<NoVariantsWithMatchingCapabilitiesFailure>() {
    override fun describeFailure(failure: NoVariantsWithMatchingCapabilitiesFailure): VariantSelectionByAttributesException {
        val message: String = buildFailureMsg(failure)
        val resolutions = buildResolutions(suggestReviewAlgorithm())
        return VariantSelectionByAttributesException(message, failure, resolutions)
    }

    companion object {
        private fun buildFailureMsg(failure: NoVariantsWithMatchingCapabilitiesFailure): String {
            val sb = StringBuilder()

            val capabilities = failure.getCapabilitySelectors()
            if (capabilities.size == 1) {
                sb.append("Unable to find a variant of '")
                    .append(failure.getTargetComponent())
                    .append("' with the requested capability: ")
                    .append(capabilities.iterator().next().getDisplayName())
            } else {
                sb.append("Unable to find a variant of '")
                    .append(failure.getTargetComponent())
                    .append("' with the requested capabilities: ")
                    .append("[").append(capabilities.stream().map<String> { obj: CapabilitySelector? -> obj!!.getDisplayName() }.collect(Collectors.joining(", "))).append("]")
            }

            sb.append(":\n")
            for (candidate in failure.getCandidates()) {
                sb.append("   - Variant '").append(candidate.getDisplayName()).append("' provides ")
                sb.append(CapabilitiesDescriber.describeCapabilities(candidate.getCandidateCapabilities().asSet())).append("\n")
            }

            return sb.toString()
        }
    }
}
