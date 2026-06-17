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

import com.google.common.collect.ImmutableSet
import org.gradle.api.artifacts.capability.CapabilitySelector
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.catalog.problems.ResolutionFailureProblemId
import org.gradle.internal.component.resolution.failure.interfaces.VariantSelectionByAttributesFailure

/**
 * An abstract [VariantSelectionByAttributesFailure] that represents the situation when a variant
 * was requested via variant-aware matching and that matching failed.
 */
abstract class AbstractVariantSelectionByAttributesFailure(
    problemId: ResolutionFailureProblemId,
    private val targetComponent: ComponentIdentifier,
    requestedAttributes: AttributeContainerInternal,
    private val capabilitySelectors: ImmutableSet<CapabilitySelector>
) : AbstractResolutionFailure(problemId), VariantSelectionByAttributesFailure {
    private val requestedAttributes: ImmutableAttributes

    init {
        this.requestedAttributes = requestedAttributes.asImmutable()
    }

    override fun describeRequestTarget(): String {
        return targetComponent.getDisplayName()
    }

    override fun getTargetComponent(): ComponentIdentifier {
        return targetComponent
    }

    override fun getRequestedAttributes(): ImmutableAttributes {
        return requestedAttributes
    }

    override fun getCapabilitySelectors(): MutableSet<CapabilitySelector> {
        return capabilitySelectors
    }
}
