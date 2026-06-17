/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact

import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.capability.CapabilitySelector
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata
import org.gradle.internal.component.model.ComponentGraphResolveState
import org.gradle.internal.component.model.IvyArtifactName
import org.gradle.internal.component.model.VariantGraphResolveState
import org.gradle.internal.resolve.resolver.ExcludingVariantArtifactSet
import org.gradle.internal.resolve.resolver.VariantArtifactResolver

/**
 * An [ArtifactSet] representing the artifacts contributed by a single variant in a dependency
 * graph, in the context of the dependency referencing it.
 */
class VariantResolvingArtifactSet(
    private val component: ComponentGraphResolveState,
    private val variant: VariantGraphResolveState,
    private val overriddenAttributes: ImmutableAttributes,
// TODO: Create a separate implementation of ArtifactSet for when !requestedArtifacts.isEmpty()
    private val requestedArtifacts: MutableList<IvyArtifactName>,
    private val exclusions: ExcludeSpec,
    private val capabilitySelectors: MutableSet<CapabilitySelector>
) : ArtifactSet {
    override fun select(
        consumerServices: ArtifactSelectionServices,
        spec: ArtifactSelectionSpec
    ): ResolvedArtifactSet {
        val componentId = component.getId()
        if (!spec.componentFilter.isSatisfiedBy(componentId)) {
            return ResolvedArtifactSet.Companion.EMPTY
        } else {
            if (spec.selectFromAllVariants && !requestedArtifacts.isEmpty()) {
                // Variants with overridden artifacts cannot be reselected since
                // we do not know the "true" attributes of the requested artifact.
                return ResolvedArtifactSet.Companion.EMPTY
            }

            val variants: ImmutableList<ResolvedVariant>?
            try {
                if (!spec.selectFromAllVariants) {
                    variants = getOwnArtifacts(consumerServices)
                } else {
                    variants = getArtifactVariantsForReselection(spec.requestAttributes, consumerServices)
                }
            } catch (e: Exception) {
                return BrokenResolvedArtifactSet(e)
            }

            if (variants.isEmpty() && spec.allowNoMatchingVariants) {
                return ResolvedArtifactSet.Companion.EMPTY
            }

            val artifactVariantSelector = consumerServices.getArtifactVariantSelector()
            val resolvedVariantTransformer = consumerServices.resolvedVariantTransformer

            val producerSchema = component.getMetadata()!!.getAttributesSchema()
            val variantSet: ResolvedVariantSet = DefaultResolvedVariantSet(componentId!!, producerSchema!!, overriddenAttributes, variants, resolvedVariantTransformer)
            return artifactVariantSelector.select(variantSet, spec.requestAttributes, spec.allowNoMatchingVariants)!!
        }
    }

    /**
     * Get all artifact sets corresponding to the graph node that this artifact set is derived from.
     */
    fun getOwnArtifacts(artifactSelectionServices: ArtifactSelectionServices): ImmutableList<ResolvedVariant> {
        val variantArtifactResolver = artifactSelectionServices.getVariantArtifactResolver()
        if (requestedArtifacts.isEmpty()) {
            return getArtifactsForGraphVariant(variant, variantArtifactResolver)
        }

        // The user requested artifacts on the dependency.
        // Resolve an adhoc variant with those artifacts.
        val componentArtifactMetadata = component.prepareForArtifactResolution()!!.getArtifactMetadata()
        val artifactState = variant.prepareForArtifactResolution()
        val adhocArtifacts = artifactState!!.getAdhocArtifacts(requestedArtifacts)
        return ImmutableList.of<ResolvedVariant>(variantArtifactResolver.resolveAdhocVariant(componentArtifactMetadata!!, variant.getMetadata()!!.getId()!!, adhocArtifacts!!)!!)
    }

    /**
     * Gets all artifact variants that should be considered for artifact selection.
     *
     *
     * This emulates the normal variant selection process where graph variants are first
     * considered, then artifact variants. We first consider graph variants, which leverages the
     * same algorithm used during graph variant selection. This considers requested and declared
     * capabilities.
     */
    private fun getArtifactVariantsForReselection(
        requestAttributes: ImmutableAttributes,
        artifactSelectionServices: ArtifactSelectionServices
    ): ImmutableList<ResolvedVariant> {
        // First, find the graph variant containing the artifact variants to select among.
        val graphVariant = artifactSelectionServices.graphVariantSelector.selectByAttributeMatchingLenient(
            requestAttributes,
            capabilitySelectors,
            component,
            artifactSelectionServices.consumerSchema,
            mutableListOf<IvyArtifactName>()
        )

        // It is fine if no graph variants satisfy our request.
        // Variant reselection allows no target variants to be found.
        if (graphVariant == null) {
            return ImmutableList.of<ResolvedVariant>()
        }

        // Next, return all artifact variants for the selected graph variant.
        return getArtifactsForGraphVariant(graphVariant, artifactSelectionServices.getVariantArtifactResolver())
    }

    /**
     * Resolve all artifact variants for the given graph variant.
     */
    private fun getArtifactsForGraphVariant(
        graphVariant: VariantGraphResolveState,
        variantArtifactResolver: VariantArtifactResolver
    ): ImmutableList<ResolvedVariant> {
        val componentArtifacts = component.prepareForArtifactResolution()!!.getArtifactMetadata()
        val variantArtifactSets: ImmutableList<ResolvedVariant> = Companion.resolveVariantArtifactSets(graphVariant, componentArtifacts!!, variantArtifactResolver)

        // Only apply exclusions to the resolved variant artifact sets if necessary.
        if (!exclusions.mayExcludeArtifacts()) {
            return variantArtifactSets
        }

        val excluded = ImmutableList.builderWithExpectedSize<ResolvedVariant>(variantArtifactSets.size)
        val moduleId = componentArtifacts.getModuleVersionId()!!.getModule()
        for (artifactSet in variantArtifactSets) {
            excluded.add(ExcludingVariantArtifactSet(artifactSet, moduleId, exclusions))
        }
        return excluded.build()
    }

    companion object {
        /**
         * Resolves all artifact sets for the given graph variant.
         */
        private fun resolveVariantArtifactSets(
            variant: VariantGraphResolveState,
            component: ComponentArtifactResolveMetadata,
            variantArtifactResolver: VariantArtifactResolver
        ): ImmutableList<ResolvedVariant> {
            val unresolved = variant.prepareForArtifactResolution()!!.getArtifactVariants()
            val resolved = ImmutableList.builderWithExpectedSize<ResolvedVariant>(unresolved!!.size)

            for (artifactSet in unresolved) {
                val resolvedArtifactSet = variantArtifactResolver.resolveVariantArtifactSet(component, variant.getMetadata()!!.getId()!!, artifactSet)
                resolved.add(resolvedArtifactSet!!)
            }

            return resolved.build()
        }
    }
}
