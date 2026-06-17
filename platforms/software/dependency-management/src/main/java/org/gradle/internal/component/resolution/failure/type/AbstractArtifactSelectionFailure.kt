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

import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.catalog.problems.ResolutionFailureProblemId
import org.gradle.internal.component.resolution.failure.interfaces.ArtifactSelectionFailure

/**
 * An abstract [ArtifactSelectionFailure] that represents the situation when an artifact is requested
 * for a variant and this request fails.
 */
abstract class AbstractArtifactSelectionFailure(
    problemId: ResolutionFailureProblemId,
    private val targetComponent: ComponentIdentifier,
    private val targetVariant: String,
    requestedAttributes: AttributeContainerInternal
) : AbstractResolutionFailure(problemId), ArtifactSelectionFailure {
    private val requestedAttributes: ImmutableAttributes

    init {
        this.requestedAttributes = requestedAttributes.asImmutable()
    }

    override fun describeRequestTarget(): String {
        return targetVariant
    }

    override fun getTargetComponent(): ComponentIdentifier {
        return targetComponent
    }

    override fun getTargetVariant(): String {
        return targetVariant
    }

    override fun getRequestedAttributes(): ImmutableAttributes {
        return requestedAttributes
    }
}
