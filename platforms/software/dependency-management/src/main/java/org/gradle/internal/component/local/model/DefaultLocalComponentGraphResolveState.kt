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
package org.gradle.internal.component.local.model

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema
import org.gradle.api.internal.tasks.NodeExecutionContext
import org.gradle.internal.Describables
import org.gradle.internal.component.model.AbstractComponentGraphResolveState
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata
import org.gradle.internal.component.model.ComponentGraphResolveMetadata
import org.gradle.internal.component.model.ImmutableModuleSources
import org.gradle.internal.component.model.ModuleSources
import org.gradle.internal.component.model.VariantGraphResolveState
import org.gradle.internal.model.CalculatedValue
import org.gradle.internal.model.CalculatedValueContainerFactory
import org.gradle.internal.model.ValueCalculator
import java.util.function.Consumer

/**
 * Holds the resolution state for a local component. The state is calculated as required, and an instance can be used for multiple resolutions across a build tree.
 *
 *
 * The aim is to create only a single instance of this type per project and reuse that for all resolution that happens in a build tree. This isn't quite the case yet.
 */
class DefaultLocalComponentGraphResolveState(
    instanceId: Long,
    metadata: LocalComponentGraphResolveMetadata,
    private val adHoc: Boolean,
    variantFactory: LocalVariantGraphResolveStateFactory,
    calculatedValueContainerFactory: CalculatedValueContainerFactory
) : AbstractComponentGraphResolveState<LocalComponentGraphResolveMetadata?>(instanceId, metadata), LocalComponentGraphResolveState {
    // The variants to use for variant selection during graph resolution
    private val graphSelectionCandidates: CalculatedValue<LocalComponentGraphResolveState.LocalComponentGraphSelectionCandidates>

    init {
        this.graphSelectionCandidates =
            calculatedValueContainerFactory.create<LocalComponentGraphResolveState.LocalComponentGraphSelectionCandidates, ValueCalculator<out LocalComponentGraphResolveState.LocalComponentGraphSelectionCandidates>>(
                Describables.of("variants of", getMetadata()), ValueCalculator { context: NodeExecutionContext? -> computeGraphSelectionCandidates(variantFactory) }
            )
    }

    override fun getModuleVersionId(): ModuleVersionIdentifier {
        return getMetadata().getModuleVersionId()
    }

    override fun isAdHoc(): Boolean {
        return adHoc
    }

    override fun getArtifactMetadata(): ComponentArtifactResolveMetadata {
        return LocalComponentArtifactResolveMetadata(getMetadata())
    }

    override fun getCandidatesForGraphVariantSelection(): LocalComponentGraphResolveState.LocalComponentGraphSelectionCandidates {
        graphSelectionCandidates.finalizeIfNotAlready()
        return graphSelectionCandidates.get()
    }

    private class LocalComponentArtifactResolveMetadata(private val metadata: ComponentGraphResolveMetadata) : ComponentArtifactResolveMetadata {
        override fun getId(): ComponentIdentifier {
            return metadata.getId()!!
        }

        override fun getModuleVersionId(): ModuleVersionIdentifier {
            return metadata.getModuleVersionId()!!
        }

        override fun getSources(): ModuleSources {
            return ImmutableModuleSources.of()
        }

        override fun getAttributes(): ImmutableAttributes {
            return ImmutableAttributes.EMPTY
        }

        override fun getAttributesSchema(): ImmutableAttributesSchema {
            return metadata.getAttributesSchema()!!
        }
    }

    private class DefaultLocalComponentGraphSelectionCandidates(
        private val variantsWithAttributes: MutableList<out LocalVariantGraphResolveState>,
        private val variantsByConfigurationName: MutableMap<String, LocalVariantGraphResolveState>
    ) : LocalComponentGraphResolveState.LocalComponentGraphSelectionCandidates {
        override fun getVariantsForAttributeMatching(): MutableList<out LocalVariantGraphResolveState> {
            return variantsWithAttributes
        }

        override fun getLegacyVariant(): VariantGraphResolveState? {
            return getVariantByConfigurationName(Dependency.DEFAULT_CONFIGURATION)
        }

        override fun getVariantByConfigurationName(name: String): LocalVariantGraphResolveState? {
            return variantsByConfigurationName.get(name)
        }

        override fun getAllSelectableVariants(): MutableList<LocalVariantGraphResolveState> {
            // Find the names of all selectable variants that are not in the variantsWithAttributes
            val configurationNames: MutableSet<String> = HashSet<String>(variantsByConfigurationName.keys)
            for (variant in variantsWithAttributes) {
                if (variant.getMetadata().getConfigurationName() != null) {
                    configurationNames.remove(variant.getMetadata().getConfigurationName())
                }
            }

            // Join the list of variants with attributes with the list of variants by configuration name
            val result: MutableList<LocalVariantGraphResolveState> = ArrayList<LocalVariantGraphResolveState>(variantsWithAttributes)
            for (configurationName in configurationNames) {
                result.add(variantsByConfigurationName.get(configurationName)!!)
            }

            return result
        }
    }

    companion object {
        private fun computeGraphSelectionCandidates(
            variantFactory: LocalVariantGraphResolveStateFactory
        ): LocalComponentGraphResolveState.LocalComponentGraphSelectionCandidates {
            val variantsWithAttributes = ImmutableList.Builder<LocalVariantGraphResolveState>()
            val variantsByConfigurationName = ImmutableMap.builder<String, LocalVariantGraphResolveState>()

            variantFactory.visitConsumableVariants(Consumer { variantState: LocalVariantGraphResolveState? ->
                if (!variantState!!.getAttributes().isEmpty()) {
                    variantsWithAttributes.add(variantState)
                }
                if (variantState.getMetadata().getConfigurationName() != null) {
                    variantsByConfigurationName.put(variantState.getMetadata().getConfigurationName()!!, variantState)
                }
            })

            return DefaultLocalComponentGraphSelectionCandidates(
                variantsWithAttributes.build(),
                variantsByConfigurationName.build()
            )
        }
    }
}
