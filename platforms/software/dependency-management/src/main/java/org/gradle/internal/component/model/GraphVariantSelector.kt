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
package org.gradle.internal.component.model

import com.google.common.collect.ImmutableList
import com.google.common.collect.Lists
import org.gradle.api.artifacts.capability.CapabilitySelector
import org.gradle.api.capabilities.Capability
import org.gradle.api.internal.artifacts.capability.CapabilitySelectorInternal
import org.gradle.api.internal.attributes.AttributeSchemaServices
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema
import org.gradle.api.internal.capabilities.CapabilityInternal
import org.gradle.api.internal.capabilities.ImmutableCapability
import org.gradle.api.internal.capabilities.ShadowedCapability
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata
import org.gradle.internal.component.external.model.ivy.IvyComponentGraphResolveState
import org.gradle.internal.component.local.model.LocalComponentGraphResolveState
import org.gradle.internal.component.resolution.failure.ResolutionFailureHandler
import org.gradle.internal.deprecation.DeprecationLogger.deprecateConfiguration
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import java.util.Collections

/**
 * Uses attribute matching to select a list of one or more variants for a component in a graph
 * (in practice, this should be only contain single variant).
 *
 * This class is intentionally named similarly to [ArtifactVariantSelector], as it has a
 * similar purpose.  An instance of [ResolutionFailureHandler] is injected in the constructor
 * to allow the caller to handle failures in a consistent way - all matching failures should be reported via
 * calls to that instance.
 */
@ServiceScope(Scope.Project::class)
class GraphVariantSelector(
    private val attributeSchemaServices: AttributeSchemaServices,
    /**
     * Returns the failure processor which must be used to report failures during variant selection.
     *
     * @return the failure processor
     */
    @JvmField val failureHandler: ResolutionFailureHandler
) {
    fun selectByAttributeMatching(
        consumerAttributes: ImmutableAttributes,
        capabilitySelectors: MutableSet<CapabilitySelector>,
        targetComponentState: ComponentGraphResolveState,
        consumerSchema: ImmutableAttributesSchema,
        requestedArtifacts: MutableList<IvyArtifactName>
    ): VariantGraphResolveState {
        val result = selectByAttributeMatchingLenient(
            consumerAttributes,
            capabilitySelectors,
            targetComponentState,
            consumerSchema,
            requestedArtifacts
        )

        if (result == null) {
            val targetComponent = targetComponentState.getMetadata()
            val attributeMatcher = attributeSchemaServices.getMatcher(consumerSchema, targetComponent.getAttributesSchema())
            val candidates = targetComponentState.getCandidatesForGraphVariantSelection()
            throw failureHandler.noCompatibleVariantsFailure(attributeMatcher, targetComponentState, consumerAttributes, capabilitySelectors, candidates)
        }

        return result
    }

    fun selectByAttributeMatchingLenient(
        consumerAttributes: ImmutableAttributes,
        capabilitySelectors: MutableSet<CapabilitySelector>,
        targetComponentState: ComponentGraphResolveState,
        consumerSchema: ImmutableAttributesSchema,
        requestedArtifacts: MutableList<IvyArtifactName>
    ): VariantGraphResolveState? {
        val candidates = targetComponentState.getCandidatesForGraphVariantSelection().getVariantsForAttributeMatching()
        assert(!candidates.isEmpty())

        val producerSchema = targetComponentState.getMetadata().getAttributesSchema()
        val attributeMatcher = attributeSchemaServices.getMatcher(consumerSchema, producerSchema)

        // Find all variants that match the requested capabilities
        val variantsProvidingRequestedCapabilities: ImmutableList<VariantGraphResolveState> = filterVariantsByRequestedCapabilities(targetComponentState, capabilitySelectors, candidates, true)
        if (variantsProvidingRequestedCapabilities.isEmpty()) {
            throw failureHandler.noVariantsWithMatchingCapabilitiesFailure(attributeMatcher, targetComponentState, consumerAttributes, capabilitySelectors, candidates)
        }

        // Perform attribute matching on the candidates satisfying our capability selectors
        var matches = attributeMatcher.matchMultipleCandidates<VariantGraphResolveState>(variantsProvidingRequestedCapabilities, consumerAttributes)
        if (matches.size < 2) {
            return zeroOrSingleVariant(matches)
        }

        // There's an ambiguity, but we may have several variants matching the requested capabilities.
        // Try to find a set of candidates that _strictly_ match the capability selectors.
        matches = filterVariantsByRequestedCapabilities(targetComponentState, capabilitySelectors, matches, false)
        if (matches.size < 2) {
            return zeroOrSingleVariant(matches)
        }

        // there are still more than one candidate, but this time we know only a subset strictly matches the required attributes
        // so we perform another round of selection on the remaining candidates
        matches = attributeMatcher.matchMultipleCandidates<VariantGraphResolveState>(matches, consumerAttributes)
        if (matches.size < 2) {
            return zeroOrSingleVariant(matches)
        }

        // TODO: Deprecate this.
        // Variant matching should not depend on requested artifacts, which are not part of the variant model.
        if (requestedArtifacts.size == 1) {
            // Here, we know that the user requested a specific classifier. There may be multiple
            // candidate variants left, but maybe only one of them provides the classified artifact
            // we're looking for.
            val classifier = requestedArtifacts.get(0).getClassifier()
            if (classifier != null) {
                val sameClassifier: MutableList<VariantGraphResolveState> = findVariantsProvidingExactlySameClassifier(matches, classifier)
                if (sameClassifier.size < 2) {
                    return zeroOrSingleVariant(sameClassifier)
                }
            }
        }

        throw failureHandler.ambiguousVariantsFailure(attributeMatcher, targetComponentState, consumerAttributes, capabilitySelectors, matches)
    }

    /**
     * Select the legacy variant from the target component.
     */
    fun selectLegacyVariant(
        consumerAttributes: ImmutableAttributes,
        targetComponentState: ComponentGraphResolveState,
        consumerSchema: ImmutableAttributesSchema,
        failureHandler: ResolutionFailureHandler
    ): VariantGraphResolveState {
        val conf: VariantGraphResolveState = targetComponentState.getCandidatesForGraphVariantSelection().getLegacyVariant()!!
        if (conf == null) {
            // We wanted to do variant matching, but there were no variants in the target component.
            // So, we fell back to looking for the legacy (`default`) configuration, but it didn't exist.
            // So, there are no variants to select from, and selection fails here.
            throw failureHandler.noVariantsFailure(targetComponentState, consumerAttributes)
        }

        validateVariantAttributes(conf, consumerAttributes, targetComponentState, consumerSchema)
        maybeEmitConsumptionDeprecation(conf)
        return conf
    }

    /**
     * Select the variant that is identified by the given configuration name.
     */
    fun selectVariantByConfigurationName(
        name: String,
        consumerAttributes: ImmutableAttributes,
        targetComponentState: ComponentGraphResolveState,
        consumerSchema: ImmutableAttributesSchema
    ): VariantGraphResolveState {
        val conf: VariantGraphResolveState
        if (targetComponentState is IvyComponentGraphResolveState) {
            conf = targetComponentState.getCandidatesForGraphVariantSelection().getVariantByConfigurationName(name)!!
        } else if (targetComponentState is LocalComponentGraphResolveState) {
            conf = targetComponentState.getCandidatesForGraphVariantSelection().getVariantByConfigurationName(name)!!
        } else {
            throw IllegalArgumentException("Cannot select a variant by configuration name from '" + targetComponentState.getId() + "'.")
        }

        if (conf == null) {
            throw failureHandler.configurationDoesNotExistFailure(targetComponentState, name)
        }

        validateVariantAttributes(conf, consumerAttributes, targetComponentState, consumerSchema)
        maybeEmitConsumptionDeprecation(conf)
        return conf
    }

    /**
     * Ensures the target variant matches the request attributes and is consumable. This needs to be called
     * for variants that are selected by means other than attribute matching.
     *
     * Note: This does not need to be called for variants selected via attribute matching, since
     * attribute matching ensures selected variants are compatible with the requested attributes.
     */
    private fun validateVariantAttributes(
        conf: VariantGraphResolveState,
        consumerAttributes: ImmutableAttributes,
        targetComponentState: ComponentGraphResolveState,
        consumerSchema: ImmutableAttributesSchema
    ) {
        val targetComponent = targetComponentState.getMetadata()
        val attributeMatcher = attributeSchemaServices.getMatcher(consumerSchema, targetComponent.getAttributesSchema())

        if (!consumerAttributes.isEmpty() && !conf.getAttributes().isEmpty()) {
            // Need to validate that the selected configuration still matches the consumer attributes
            if (!attributeMatcher.isMatchingCandidate(conf.getAttributes(), consumerAttributes)) {
                throw failureHandler.configurationNotCompatibleFailure(attributeMatcher, targetComponentState, conf, consumerAttributes, conf.getCapabilities())
            }
        }
    }

    companion object {
        private fun findVariantsProvidingExactlySameClassifier(matches: MutableList<VariantGraphResolveState>, classifier: String): MutableList<VariantGraphResolveState> {
            var sameClassifier = mutableListOf<VariantGraphResolveState>()
            // let's see if we can find a single variant which has exactly the requested artifacts
            for (match in matches) {
                if (variantProvidesClassifier(match, classifier)) {
                    if (sameClassifier === Collections.EMPTY_LIST) {
                        sameClassifier = mutableListOf<VariantGraphResolveState>(match)
                    } else {
                        sameClassifier = Lists.newArrayList<VariantGraphResolveState>(sameClassifier)
                        sameClassifier.add(match)
                    }
                }
            }
            return sameClassifier
        }

        private fun variantProvidesClassifier(variant: VariantGraphResolveState, classifier: String): Boolean {
            val artifactSets = variant.prepareForArtifactResolution().getArtifactVariants()
            for (artifactSet in artifactSets) {
                if (artifactSetStrictlyProvidesClassifier(artifactSet, classifier)) {
                    return true
                }
            }

            return false
        }

        private fun artifactSetStrictlyProvidesClassifier(artifactSet: VariantResolveMetadata, classifier: String): Boolean {
            val artifacts: MutableList<out ComponentArtifactMetadata> = artifactSet.getArtifacts()
            if (artifacts.size != 1) {
                return false
            }

            val componentArtifactMetadata: ComponentArtifactMetadata = artifacts.get(0)
            if (componentArtifactMetadata !is ModuleComponentArtifactMetadata) {
                return false
            }

            return classifier == componentArtifactMetadata.getName().getClassifier()
        }

        private fun zeroOrSingleVariant(matches: MutableList<VariantGraphResolveState>): VariantGraphResolveState? {
            if (matches.isEmpty()) {
                return null
            }

            assert(matches.size == 1)
            val match = matches.get(0)
            maybeEmitConsumptionDeprecation(match)
            return match
        }

        private fun maybeEmitConsumptionDeprecation(targetVariant: VariantGraphResolveState) {
            if (targetVariant.getMetadata().isDeprecated()) {
                deprecateConfiguration(targetVariant.getName())
                    .forConsumption()
                    .willBecomeAnErrorInNextMajorGradleVersion()
                    .withUserManual("declaring_dependencies", "sec:deprecated-configurations")!!
                    .nagUser()
            }
        }

        private fun filterVariantsByRequestedCapabilities(
            targetComponent: ComponentGraphResolveState,
            capabilitySelectors: MutableSet<CapabilitySelector>,
            consumableVariants: MutableCollection<out VariantGraphResolveState>,
            lenient: Boolean
        ): ImmutableList<VariantGraphResolveState> {
            val defaultCapability = targetComponent.getDefaultCapability()
            val explicitlyRequested = !capabilitySelectors.isEmpty()
            val builder = ImmutableList.builderWithExpectedSize<VariantGraphResolveState>(consumableVariants.size)

            for (variant in consumableVariants) {
                val capabilities = variant.getCapabilities()
                if (explicitlyRequested) {
                    // Capabilities were explicitly requested.
                    // Require the variants capabilities match all requested selectors.
                    if (matchesCapabilitySelectors(capabilitySelectors, capabilities, defaultCapability, lenient)) {
                        builder.add(variant)
                    }
                } else {
                    // No capabilities were explicitly requested.
                    // Default to requiring the implicit capability as specified by the component.
                    if (containsImplicitCapability(capabilities, defaultCapability, lenient)) {
                        builder.add(variant)
                    }
                }
            }

            return builder.build()
        }

        /**
         * Determines if the provided capabilities contains the implicit capability of the component.
         *
         * @param capabilities The capabilities to check
         * @param implicitCapability The implicit capability of the component
         * @param lenient If false, the method will return fail if the component has more capabilities than the implicit capability.
         *
         * @return true if the capabilities contain the implicit capability
         */
        private fun containsImplicitCapability(
            capabilities: ImmutableCapabilities,
            implicitCapability: ImmutableCapability,
            lenient: Boolean
        ): Boolean {
            // If the variant declares no capabilities, it inherits the implicit capability of the component.
            if (capabilities.isEmpty()) {
                return true
            }

            // If the variant contains only the shadowed capability, it's an implicit capability.
            // TODO: Why do we not check the content of the shadowed capability?
            val capabilitiesSet = capabilities.asSet()
            if (capabilitiesSet.size == 1 && capabilitiesSet.iterator().next() is ShadowedCapability) {
                return true
            }

            // Otherwise, check the declared capabilities.
            for (capability in capabilities) {
                var capability: Capability = capability
                if (capability is ShadowedCapability) {
                    capability = capability.getShadowedCapability()
                }
                if (implicitCapability.getGroup() == capability.getGroup() && implicitCapability.getName() == capability.getName()) {
                    return lenient || capabilities.asSet().size == 1
                }
            }

            return false
        }

        /**
         * Determines if the provided capabilities matches all the provided selectors.
         *
         * @param capabilitySelectors The selectors to check against
         * @param capabilities The capabilities to check
         * @param implicitCapability The implicit capability of the component
         * @param lenient If false, the method will return fail if there are extra capabilities not explicitly requested.
         *
         * @return true if the capabilities match the selectors
         */
        private fun matchesCapabilitySelectors(
            capabilitySelectors: MutableSet<CapabilitySelector>,
            capabilities: ImmutableCapabilities,
            implicitCapability: ImmutableCapability,
            lenient: Boolean
        ): Boolean {
            var capabilities = capabilities
            if (capabilities.isEmpty()) {
                // The variant does not declare any capabilities.
                // Use the component's implicit capability by default.
                capabilities = ImmutableCapabilities.of(implicitCapability)
            }

            // Check that every selector matches at least one capability
            for (selector in capabilitySelectors) {
                if (noMatchingCapability(selector, capabilities, implicitCapability)) {
                    return false
                }
            }

            // If lenient, we allow extra capabilities not explicitly requested
            if (lenient) {
                return true
            }

            // Check that every capability matches at least one selector
            for (capability in capabilities) {
                if (noMatchingSelector(capability, capabilitySelectors, implicitCapability)) {
                    return false
                }
            }

            return true
        }

        private fun noMatchingCapability(
            selector: CapabilitySelector,
            capabilities: ImmutableCapabilities,
            implicitCapability: ImmutableCapability
        ): Boolean {
            for (capability in capabilities) {
                if (matches(selector, capability, implicitCapability)) {
                    return false
                }
            }

            return true
        }

        private fun noMatchingSelector(
            capability: CapabilityInternal,
            selectors: MutableSet<CapabilitySelector>,
            implicitCapability: ImmutableCapability
        ): Boolean {
            for (selector in selectors) {
                if (matches(selector, capability, implicitCapability)) {
                    return false
                }
            }

            return true
        }

        private fun matches(
            selector: CapabilitySelector,
            capability: CapabilityInternal,
            implicitCapability: ImmutableCapability
        ): Boolean {
            val internalSelector = selector as CapabilitySelectorInternal
            return internalSelector.matches(capability.getGroup(), capability.getName(), implicitCapability)
        }
    }
}
