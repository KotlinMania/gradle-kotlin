/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.internal.artifacts.transform

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.BrokenResolvedArtifactSet
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariantSet
import org.gradle.api.internal.attributes.AttributeSchemaServices
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema
import org.gradle.internal.component.resolution.failure.ResolutionFailureHandler

/**
 * A [ArtifactVariantSelector] that uses attribute matching to select a matching set of artifacts.
 *
 *
 * If no producer variant is compatible with the requested attributes, this selector will attempt to construct a chain of artifact
 * transforms that can produce a variant compatible with the requested attributes.
 *
 *
 * An instance of [ResolutionFailureHandler] is injected in the constructor
 * to allow the caller to handle failures in a consistent manner as during graph variant selection.
 */
class AttributeMatchingArtifactVariantSelector(
    private val consumerSchema: ImmutableAttributesSchema,
    transformationChainBuilder: ConsumerProvidedVariantFinder,
    private val attributesFactory: AttributesFactory,
    private val attributeSchemaServices: AttributeSchemaServices,
    private val failureHandler: ResolutionFailureHandler
) : ArtifactVariantSelector {
    private val transformationChainSelector: TransformationChainSelector

    init {
        this.transformationChainSelector = TransformationChainSelector(transformationChainBuilder, failureHandler)
    }

    override fun select(
        producer: ResolvedVariantSet,
        requestAttributes: ImmutableAttributes,
        allowNoMatchingVariants: Boolean
    ): ResolvedArtifactSet {
        try {
            return doSelect(producer, requestAttributes, allowNoMatchingVariants)
        } catch (t: Exception) {
            return BrokenResolvedArtifactSet(failureHandler.unknownArtifactVariantSelectionFailure(producer, requestAttributes, t))
        }
    }

    private fun doSelect(
        producer: ResolvedVariantSet,
        requestAttributes: ImmutableAttributes,
        allowNoMatchingVariants: Boolean
    ): ResolvedArtifactSet {
        val matcher = attributeSchemaServices.getMatcher(consumerSchema, producer.producerSchema)
        val targetAttributes = attributesFactory.concat(requestAttributes, producer.overriddenAttributes)

        // Check for matching variant without using artifact transforms.  If we found only one match, return it.
        // If we found multiple matches, there is ambiguity.
        val matchingVariants: MutableList<ResolvedVariant>? = matcher.matchMultipleCandidates<ResolvedVariant?>(producer.candidates, targetAttributes)
        if (matchingVariants!!.size == 1) {
            return matchingVariants.get(0).artifacts
        } else if (matchingVariants.size > 1) {
            throw failureHandler.ambiguousArtifactsFailure(matcher, producer, targetAttributes, matchingVariants)
        }

        // We found no matching variant.  Attempt to select a chain of transformations that produces a suitable virtual variant.
        val selectedTransformationChain = transformationChainSelector.selectTransformationChain(producer, targetAttributes, matcher)
        if (selectedTransformationChain.isPresent()) {
            return producer.transformCandidate(selectedTransformationChain.get().getRoot(), selectedTransformationChain.get().getTransformedVariantDefinition())
        }

        // At this point, there is no possibility of a match for the request.  That could be okay if allowed, else it's a failure.
        if (allowNoMatchingVariants) {
            return ResolvedArtifactSet.EMPTY
        } else {
            throw failureHandler.noCompatibleArtifactFailure(matcher, producer, targetAttributes)
        }
    }
}
