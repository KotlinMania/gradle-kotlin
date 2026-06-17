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

import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.internal.artifacts.ProjectComponentIdentifierInternal
import org.gradle.internal.component.resolution.failure.exception.VariantSelectionByNameException
import org.gradle.internal.component.resolution.failure.type.ConfigurationDoesNotExistFailure
import org.gradle.util.Path

/**
 * A [ResolutionFailureDescriber] that describes a [ConfigurationDoesNotExistFailure].
 */
abstract class ConfigurationDoesNotExistFailureDescriber : AbstractResolutionFailureDescriber<ConfigurationDoesNotExistFailure>() {
    override fun describeFailure(failure: ConfigurationDoesNotExistFailure): VariantSelectionByNameException {
        val message = buildFailureMsg(failure)

        val resolutions = ImmutableList.builder<String>()
        val isLocalComponent = failure.getTargetComponent() is ProjectComponentIdentifier
        if (isLocalComponent) {
            val id = failure.getTargetComponent() as ProjectComponentIdentifierInternal
            val outgoingVariantsPath = id.getIdentityPath().append(Path.path("outgoingVariants"))
            resolutions.add("To determine which configurations are available in the target " + failure.getTargetComponent().getDisplayName() + ", run " + outgoingVariantsPath.asString() + ".")
        }

        resolutions.addAll(buildResolutions(suggestReviewAlgorithm()))
        return VariantSelectionByNameException(message, failure, resolutions.build())
    }

    private fun buildFailureMsg(failure: ConfigurationDoesNotExistFailure): String {
        return String.format(
            "A dependency was declared on configuration '%s' of '%s' but no variant with that configuration name exists.",
            failure.getRequestedConfigurationName(),
            failure.getTargetComponent().getDisplayName()
        )
    }
}
