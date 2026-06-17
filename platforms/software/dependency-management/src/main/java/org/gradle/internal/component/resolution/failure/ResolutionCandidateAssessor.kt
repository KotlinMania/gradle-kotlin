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
package org.gradle.internal.component.resolution.failure

import com.google.common.collect.ImmutableList
import com.google.common.collect.Sets
import org.gradle.api.Describable
import org.gradle.api.attributes.Attribute
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.attributes.matching.AttributeMatcher
import org.gradle.api.internal.capabilities.ImmutableCapability
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.internal.component.model.GraphSelectionCandidates
import org.gradle.internal.component.model.VariantGraphResolveMetadata
import org.gradle.internal.component.model.VariantGraphResolveState
import java.util.Objects
import java.util.function.Function
import java.util.stream.Collectors

/**
 * A utility class used by [ResolutionFailureHandler] to assess and classify
 * how the attributes of candidate variants during a single attempt at dependency resolution
 * align with the requested attributes.
 */
class ResolutionCandidateAssessor(requestedAttributes: AttributeContainerInternal, private val attributeMatcher: AttributeMatcher) {
    private val requestedAttributes: ImmutableAttributes

    init {
        this.requestedAttributes = requestedAttributes.asImmutable()
    }

    fun getRequestedAttributes(): ImmutableAttributes {
        return requestedAttributes
    }

    fun assessResolvedVariants(resolvedVariants: MutableList<out ResolvedVariant>): MutableList<AssessedCandidate> {
        return resolvedVariants.stream()
            .map<AssessedCandidate> { variant: ResolvedVariant ->
                assessCandidate(
                    variant.asDescribable().getCapitalizedDisplayName(),
                    variant.getCapabilities(),
                    variant.getAttributes().asImmutable()
                )
            }
            .sorted(Comparator.comparing<AssessedCandidate, String>(Function { obj: AssessedCandidate -> obj.getDisplayName() }))
            .collect(Collectors.toList())
    }

    fun assessResolvedVariantStates(variantStates: MutableList<out VariantGraphResolveState>, defaultCapabilityForComponent: ImmutableCapability): MutableList<AssessedCandidate> {
        return variantStates.stream()
            .map<VariantGraphResolveMetadata> { obj: VariantGraphResolveState? -> obj!!.getMetadata() }
            .map<AssessedCandidate> { variant: VariantGraphResolveMetadata? ->
                val capabilities = variant!!.getCapabilities().orElse(defaultCapabilityForComponent)
                assessCandidate(variant.getDisplayName(), capabilities, variant.getAttributes().asImmutable())
            }.sorted(Comparator.comparing<AssessedCandidate, String>(Function { obj: AssessedCandidate -> obj.getDisplayName() }))
            .collect(Collectors.toList())
    }

    fun assessNodeMetadatas(nodes: MutableSet<VariantGraphResolveMetadata>): MutableList<AssessedCandidate> {
        return nodes.stream()
            .map<AssessedCandidate> { variant: VariantGraphResolveMetadata? -> assessCandidate(variant!!.getDisplayName(), variant.getCapabilities(), variant.getAttributes().asImmutable()) }
            .sorted(Comparator.comparing<AssessedCandidate, String>(Function { obj: AssessedCandidate -> obj.getDisplayName() }))
            .collect(Collectors.toList())
    }

    fun assessGraphSelectionCandidates(candidates: GraphSelectionCandidates): MutableList<AssessedCandidate> {
        return candidates.getVariantsForAttributeMatching().stream()
            .map<VariantGraphResolveMetadata> { obj: VariantGraphResolveState? -> obj!!.getMetadata() }
            .map<AssessedCandidate> { variantMetadata: VariantGraphResolveMetadata? ->
                assessCandidate(
                    variantMetadata!!.getDisplayName(),
                    variantMetadata.getCapabilities(),
                    variantMetadata.getAttributes()
                )
            }
            .sorted(Comparator.comparing<AssessedCandidate, String>(Function { obj: AssessedCandidate -> obj.getDisplayName() }))
            .collect(Collectors.toList())
    }

    fun assessCandidate(
        candidateDisplayName: String,
        candidateCapabilities: ImmutableCapabilities,
        candidateAttributes: ImmutableAttributes
    ): AssessedCandidate {
        val alreadyAssessed: MutableSet<String> = HashSet<String>(candidateAttributes.keySet().size)
        val compatible = ImmutableList.builder<AssessedAttribute<*>>()
        val incompatible = ImmutableList.builder<AssessedAttribute<*>>()
        val onlyOnConsumer = ImmutableList.builder<AssessedAttribute<*>>()
        val onlyOnProducer = ImmutableList.builder<AssessedAttribute<*>>()

        Sets.union<Attribute<*>>(requestedAttributes.getAttributes().keySet(), candidateAttributes.getAttributes().keySet()).stream()
            .sorted(Comparator.comparing<Attribute<*>, String>(Function { obj: Attribute<*> -> obj.getName() }))
            .forEach { attribute: Attribute<*>? ->
                Companion.classifyAttribute(
                    requestedAttributes,
                    candidateAttributes,
                    attributeMatcher,
                    attribute,
                    alreadyAssessed,
                    compatible,
                    incompatible,
                    onlyOnConsumer,
                    onlyOnProducer
                )
            }

        return ResolutionCandidateAssessor.AssessedCandidate(
            candidateDisplayName,
            candidateAttributes,
            candidateCapabilities,
            compatible.build(),
            incompatible.build(),
            onlyOnConsumer.build(),
            onlyOnProducer.build()
        )
    }

    /**
     * An immutable data class that holds information about a single variant which was a candidate for matching during resolution.
     *
     * This includes classifying its attributes into lists of compatible, incompatible, and absent attributes.  Each candidate
     * is assessed within the context of a resolution, but must not reference the assessor
     * that produced it, in order to remain configuration cache compatible - the assessor is not serializable.
     */
    class AssessedCandidate private constructor(
        private val displayName: String,
        attributes: AttributeContainerInternal,
        private val candidateCapabilities: ImmutableCapabilities,
        private val compatible: ImmutableList<AssessedAttribute<*>>,
        @JvmField private val incompatible: ImmutableList<AssessedAttribute<*>>,
        private val onlyOnRequest: ImmutableList<AssessedAttribute<*>>,
        private val onlyOnCandidate: ImmutableList<AssessedAttribute<*>>
    ) : Describable {
        @JvmField
        private val candidateAttributes: ImmutableAttributes

        init {
            this.candidateAttributes = attributes.asImmutable()
        }

        override fun getDisplayName(): String {
            return displayName
        }

        fun getAllCandidateAttributes(): ImmutableAttributes {
            return candidateAttributes
        }

        fun getCandidateCapabilities(): ImmutableCapabilities {
            return candidateCapabilities
        }

        fun getCompatibleAttributes(): ImmutableList<AssessedAttribute<*>> {
            return compatible
        }

        fun getIncompatibleAttributes(): ImmutableList<AssessedAttribute<*>> {
            return incompatible
        }

        fun getOnlyOnRequestAttributes(): ImmutableList<AssessedAttribute<*>> {
            return onlyOnRequest
        }

        fun getOnlyOnCandidateAttributes(): ImmutableList<AssessedAttribute<*>> {
            return onlyOnCandidate
        }

        fun hasNoAttributes(): Boolean {
            return getAllCandidateAttributes().isEmpty()
        }
    }

    /**
     * An immutable data class that records a single attribute, its requested value, and its provided value
     * for a given resolution attempt.
     */
    class AssessedAttribute<T> private constructor(@JvmField private val attribute: Attribute<T?>, requested: T?, provided: T?) {
        private val requested: String?
        @JvmField
        private val provided: String?

        init {
            this.requested = Objects.toString(requested)
            this.provided = Objects.toString(provided)
        }

        fun getAttribute(): Attribute<T?> {
            return attribute
        }

        fun getRequested(): String? {
            return requested
        }

        fun getProvided(): String? {
            return provided
        }

        override fun toString(): String {
            return "{name=" + attribute.getName() +
                    ", type=" + attribute.getType() +
                    ", requested=" + requested +
                    ", provided=" + provided +
                    '}'
        }
    }

    companion object {
        private fun <T> classifyAttribute(
            requestedAttributes: ImmutableAttributes, candidateAttributes: ImmutableAttributes, attributeMatcher: AttributeMatcher,
            attribute: Attribute<T?>, alreadyAssessed: MutableSet<String>,
            compatible: ImmutableList.Builder<AssessedAttribute<*>>, incompatible: ImmutableList.Builder<AssessedAttribute<*>>,
            onlyOnConsumer: ImmutableList.Builder<AssessedAttribute<*>>, onlyOnProducer: ImmutableList.Builder<AssessedAttribute<*>>
        ) {
            if (alreadyAssessed.add(attribute.getName())) {
                val attributeName = attribute.getName()
                val consumerEntry = requestedAttributes.findEntry(attributeName)
                val producerEntry = candidateAttributes.findEntry(attributeName)

                if (consumerEntry != null && producerEntry != null) {
                    val coercedProducer = producerEntry.coerce<T?>(attribute)
                    val coercedConsumer = consumerEntry.coerce<T?>(attribute)
                    val assessedAttribute = ResolutionCandidateAssessor.AssessedAttribute<T?>(attribute, coercedConsumer, coercedProducer)

                    if (attributeMatcher.isMatchingValue<T?>(attribute, coercedProducer, coercedConsumer)) {
                        compatible.add(assessedAttribute)
                    } else {
                        incompatible.add(assessedAttribute)
                    }
                } else if (consumerEntry != null) {
                    onlyOnConsumer.add(ResolutionCandidateAssessor.AssessedAttribute<T?>(attribute, uncheckedCast<T?>(consumerEntry.getIsolatedValue()), null))
                } else if (producerEntry != null) {
                    onlyOnProducer.add(ResolutionCandidateAssessor.AssessedAttribute<T?>(attribute, null, uncheckedCast<T?>(producerEntry.getIsolatedValue())))
                }
            }
        }
    }
}
