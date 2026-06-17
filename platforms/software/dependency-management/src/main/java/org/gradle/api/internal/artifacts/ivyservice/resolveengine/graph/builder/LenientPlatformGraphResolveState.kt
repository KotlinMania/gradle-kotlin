/*
 * Copyright 2022 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.NamedVariantIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.strict.StrictVersionConstraints.equals
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema
import org.gradle.internal.Describables
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactMetadata
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier.Companion.newId
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector.Companion.newSelector
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.internal.component.external.model.ModuleDependencyMetadata
import org.gradle.internal.component.model.AbstractComponentGraphResolveState
import org.gradle.internal.component.model.ComponentArtifactMetadata
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata
import org.gradle.internal.component.model.ComponentConfigurationIdentifier
import org.gradle.internal.component.model.DefaultVariantMetadata
import org.gradle.internal.component.model.DependencyMetadata
import org.gradle.internal.component.model.ExcludeMetadata
import org.gradle.internal.component.model.GraphSelectionCandidates
import org.gradle.internal.component.model.ImmutableModuleSources
import org.gradle.internal.component.model.IvyArtifactName
import org.gradle.internal.component.model.ModuleSources
import org.gradle.internal.component.model.VariantArtifactResolveState
import org.gradle.internal.component.model.VariantGraphResolveMetadata
import org.gradle.internal.component.model.VariantGraphResolveState
import org.gradle.internal.component.model.VariantIdentifier
import org.gradle.internal.component.model.VariantResolveMetadata

class LenientPlatformGraphResolveState(
    componentId: Long,
    variantId: Long,
    metadata: LenientPlatformResolveMetadata,
    virtualPlatformState: VirtualPlatformState,
    resolveState: ResolveState
) : AbstractComponentGraphResolveState<LenientPlatformResolveMetadata?>(componentId, metadata) {
    private val variant: LenientPlatformVariantGraphResolveState

    init {
        this.variant = createVariant(variantId, virtualPlatformState, resolveState, metadata.getId())
    }

    override fun getArtifactMetadata(): ComponentArtifactResolveMetadata {
        return LenientPlatformGraphResolveState.LenientPlatformArtifactResolveMetadata(getMetadata()!!)
    }

    /**
     * Artifact metadata for a lenient platform.
     */
    private class LenientPlatformArtifactResolveMetadata(private val metadata: LenientPlatformResolveMetadata) : ComponentArtifactResolveMetadata {
        override fun getId(): ComponentIdentifier {
            return metadata.getId()
        }

        override fun getModuleVersionId(): ModuleVersionIdentifier {
            return metadata.getModuleVersionId()
        }

        override fun getSources(): ModuleSources {
            return ImmutableModuleSources.of()
        }

        override fun getAttributes(): ImmutableAttributes {
            return ImmutableAttributes.EMPTY
        }

        override fun getAttributesSchema(): ImmutableAttributesSchema {
            return metadata.getAttributesSchema()
        }
    }

    override fun getCandidatesForGraphVariantSelection(): GraphSelectionCandidates {
        return LenientPlatformGraphSelectionCandidates(variant)
    }

    @JvmRecord
    private data class LenientPlatformGraphSelectionCandidates(val variant: VariantGraphResolveState) : GraphSelectionCandidates {
        override fun getVariantsForAttributeMatching(): MutableList<out VariantGraphResolveState> {
            // Variants are not selected from a lenient platform in the conventional manner.
            return mutableListOf<VariantGraphResolveState>()
        }

        override fun getLegacyVariant(): VariantGraphResolveState {
            return variant
        }
    }

    /**
     * Metadata for a variant of a lenient platform.
     */
    private class LenientPlatformVariantGraphResolveMetadata(
        private val id: VariantIdentifier,
        private val name: String
    ) : VariantGraphResolveMetadata {
        override fun getName(): String {
            return name
        }

        override fun getId(): VariantIdentifier {
            return id
        }

        override fun getAttributes(): ImmutableAttributes {
            return ImmutableAttributes.EMPTY
        }

        override fun getCapabilities(): ImmutableCapabilities {
            return ImmutableCapabilities.EMPTY
        }

        override fun isTransitive(): Boolean {
            return true
        }

        override fun isExternalVariant(): Boolean {
            return false
        }
    }

    /**
     * State for a variant of a lenient platform.
     */
    private class LenientPlatformVariantGraphResolveState(
        private val instanceId: Long,
        private val platformId: ModuleComponentIdentifier,
        private val virtualPlatformState: VirtualPlatformState,
        private val resolveState: ResolveState,
        private val metadata: LenientPlatformVariantGraphResolveMetadata
    ) : VariantGraphResolveState {
        private val artifactResolveState: LenientPlatformVariantArtifactResolveState

        init {
            this.artifactResolveState = LenientPlatformVariantArtifactResolveState(
                platformId,
                metadata
            )
        }

        override fun getInstanceId(): Long {
            return instanceId
        }

        override fun getName(): String {
            return metadata.getName()
        }

        val attributes: ImmutableAttributes
            get() = metadata.getAttributes()

        override fun getCapabilities(): ImmutableCapabilities {
            return metadata.getCapabilities()
        }

        override fun getDependencies(): MutableList<out DependencyMetadata> {
            var result: MutableList<ModuleDependencyMetadata>? = null
            val candidateVersions = virtualPlatformState.getCandidateVersions()
            val modules = virtualPlatformState.getParticipatingModules()
            val forced = virtualPlatformState.isForced()

            for (module in modules) {
                val selected = module.getSelected()
                if (selected != null) {
                    val resultForSelected = getDependencyForParticipatingComponent(module, selected, candidateVersions, forced)
                    if (resultForSelected != null) {
                        if (result == null) {
                            result = ArrayList<ModuleDependencyMetadata>(modules.size)
                        }
                        result.add(resultForSelected)
                    }

                    virtualPlatformState.attachOrphanEdges()
                }
            }

            return if (result == null) mutableListOf<DependencyMetadata>() else result
        }

        fun getDependencyForParticipatingComponent(
            module: ModuleResolveState,
            selectedComponent: ComponentState,
            candidateVersions: MutableList<String>,
            forced: Boolean
        ): ModuleDependencyMetadata? {
            for (target in candidateVersions) {
                val targetComponentId = newId(module.getId(), target)

                // We will only add dependencies to the leaves if there is such a published module.
                // To do this, we either check module has already selected the target version
                // or we need to resolve the potential target version to see if it exists.
                if (selectedComponent.componentId == targetComponentId ||
                    componentVersionExists(targetComponentId)
                ) {
                    return createConstraint(targetComponentId, forced)
                }
            }

            return null
        }

        /**
         * Determine if the given component version exists, by resolving it.
         */
        fun componentVersionExists(componentId: ModuleComponentIdentifier): Boolean {
            val moduleVersionId =
                DefaultModuleVersionIdentifier.newId(componentId.getModuleIdentifier(), componentId.getVersion())
            return resolveState.getModule(componentId.getModuleIdentifier())
                .getVersion(moduleVersionId, componentId)
                .getResolveStateOrNull() != null
        }

        fun createConstraint(
            targetComponentId: ModuleComponentIdentifier,
            forced: Boolean
        ): LenientPlatformDependencyMetadata {
            val selector =
                newSelector(targetComponentId.getModuleIdentifier(), targetComponentId.getVersion())
            return LenientPlatformDependencyMetadata(
                selector,
                platformId,
                forced,
                true
            )
        }

        override fun getExcludes(): MutableList<out ExcludeMetadata> {
            return mutableListOf<ExcludeMetadata>()
        }

        override fun getMetadata(): VariantGraphResolveMetadata {
            return metadata
        }

        override fun prepareForArtifactResolution(): VariantArtifactResolveState {
            return artifactResolveState
        }
    }

    /**
     * Artifact state for a variant of a lenient platform.
     */
    private class LenientPlatformVariantArtifactResolveState(private val componentId: ModuleComponentIdentifier, private val variant: VariantGraphResolveMetadata) : VariantArtifactResolveState {
        override fun getAdhocArtifacts(dependencyArtifacts: MutableList<IvyArtifactName>): ImmutableList<ComponentArtifactMetadata> {
            val artifacts = ImmutableList.builderWithExpectedSize<ComponentArtifactMetadata>(dependencyArtifacts.size)
            for (dependencyArtifact in dependencyArtifacts) {
                artifacts.add(DefaultModuleComponentArtifactMetadata(componentId, dependencyArtifact))
            }
            return artifacts.build()
        }

        override fun getArtifactVariants(): MutableSet<out VariantResolveMetadata> {
            val name = variant.getName()
            return ImmutableSet.of<DefaultVariantMetadata>(
                DefaultVariantMetadata(
                    name,
                    ComponentConfigurationIdentifier(componentId, name),
                    Describables.of(componentId, "variant", variant.getDisplayName()),
                    variant.getAttributes()!!,
                    ImmutableList.of<ComponentArtifactMetadata>(),
                    variant.getCapabilities()!!
                )
            )
        }
    }

    companion object {
        private fun createVariant(
            instanceId: Long,
            virtualPlatformState: VirtualPlatformState,
            resolveState: ResolveState,
            platformId: ModuleComponentIdentifier
        ): LenientPlatformVariantGraphResolveState {
            val name = Dependency.DEFAULT_CONFIGURATION
            val variantId = NamedVariantIdentifier(platformId, name)
            return LenientPlatformVariantGraphResolveState(
                instanceId,
                platformId,
                virtualPlatformState,
                resolveState,
                LenientPlatformVariantGraphResolveMetadata(variantId, name)
            )
        }
    }
}
