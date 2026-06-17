/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.internal.component.external.model

import com.google.common.base.Objects
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.Describables
import org.gradle.internal.DisplayName
import org.gradle.internal.component.model.ComponentArtifactMetadata
import org.gradle.internal.component.model.ModuleConfigurationMetadata
import org.gradle.internal.component.model.ModuleSources
import org.gradle.internal.component.model.VariantResolveMetadata
import java.util.Optional

/**
 * Common base class for the realised versions of [ModuleComponentResolveMetadata] implementations.
 *
 * The realised part is about the application of [VariantMetadataRules] which are applied eagerly
 * to configuration or variant data.
 *
 * This type hierarchy is used whenever the `ModuleComponentResolveMetadata` needs to outlive
 * the build execution.
 */
abstract class AbstractRealisedModuleComponentResolveMetadata : AbstractModuleComponentResolveMetadata {
    private var graphVariants: Optional<MutableList<out ExternalModuleVariantGraphResolveMetadata?>?>? = null
    private val configurations: ImmutableMap<String?, ModuleConfigurationMetadata?>

    constructor(metadata: AbstractRealisedModuleComponentResolveMetadata, sources: ModuleSources?, derivationStrategy: VariantDerivationStrategy?) : super(metadata, sources, derivationStrategy) {
        this.configurations = metadata.configurations
    }

    constructor(
        mutableMetadata: AbstractModuleComponentResolveMetadata,
        variants: ImmutableList<out ComponentVariant?>?,
        configurations: MutableMap<String?, ModuleConfigurationMetadata?>
    ) : super(mutableMetadata, variants) {
        this.configurations = ImmutableMap.builder<String?, ModuleConfigurationMetadata?>().putAll(configurations).build()
    }

    val variantMetadataRules: VariantMetadataRules
        get() = VariantMetadataRules.noOp()

    override fun getConfigurationNames(): MutableSet<String?> {
        return configurations.keys
    }

    override fun getConfiguration(name: String?): ModuleConfigurationMetadata? {
        return configurations.get(name)
    }

    override fun getVariantsForGraphTraversal(): MutableList<out ExternalModuleVariantGraphResolveMetadata?> {
        if (graphVariants == null) {
            graphVariants = buildVariantsForGraphTraversal(variants!!)
        }
        return graphVariants!!.orElse(mutableListOf<ExternalModuleVariantGraphResolveMetadata?>())!!
    }

    private fun buildVariantsForGraphTraversal(variants: MutableList<out ComponentVariant>): Optional<MutableList<out ExternalModuleVariantGraphResolveMetadata?>?>? {
        if (variants.isEmpty()) {
            return maybeDeriveVariants()
        }
        val configurations = ImmutableList.Builder<ModuleConfigurationMetadata?>()
        for (variant in variants) {
            configurations.add(RealisedVariantBackedConfigurationMetadata(getId()!!, variant, getAttributes(), attributesFactory!!))
        }
        return Optional.of<MutableList<out ExternalModuleVariantGraphResolveMetadata?>?>(configurations.build())
    }

    protected class NameOnlyVariantResolveMetadata(val name: String?) : VariantResolveMetadata {
        val identifier: VariantResolveMetadata.Identifier?
            get() = null

        override fun asDescribable(): DisplayName? {
            throw UnsupportedOperationException("NameOnlyVariantResolveMetadata cannot be used that way")
        }

        val attributes: ImmutableAttributes?
            get() {
                throw UnsupportedOperationException("NameOnlyVariantResolveMetadata cannot be used that way")
            }

        val artifacts: ImmutableList<out ComponentArtifactMetadata?>?
            get() {
                throw UnsupportedOperationException("NameOnlyVariantResolveMetadata cannot be used that way")
            }

        val capabilities: ImmutableCapabilities
            get() {
                throw UnsupportedOperationException("NameOnlyVariantResolveMetadata cannot be used that way")
            }

        override fun isExternalVariant(): Boolean {
            return false
        }
    }

    class ImmutableRealisedVariantImpl(
        private val componentId: ModuleComponentIdentifier, val name: String, val attributes: ImmutableAttributes?,
        private val dependencies: ImmutableList<out ComponentVariant.Dependency?>?, private val dependencyConstraints: ImmutableList<out ComponentVariant.DependencyConstraint?>?,
        private val files: ImmutableList<out ComponentVariant.File>, val capabilities: ImmutableCapabilities,
        dependencyMetadata: MutableList<out ModuleDependencyMetadata?>,
        private val externalVariant: Boolean
    ) : ComponentVariant, VariantResolveMetadata {
        val dependencyMetadata: ImmutableList<out ModuleDependencyMetadata?>

        init {
            this.dependencyMetadata = ImmutableList.copyOf(dependencyMetadata)
        }

        val identifier: VariantResolveMetadata.Identifier?
            get() = null

        override fun asDescribable(): DisplayName? {
            return Describables.of(componentId, "variant", name)
        }

        override fun getDependencies(): ImmutableList<out ComponentVariant.Dependency?>? {
            return dependencies
        }

        override fun getDependencyConstraints(): ImmutableList<out ComponentVariant.DependencyConstraint?>? {
            return dependencyConstraints
        }

        override fun getFiles(): ImmutableList<out ComponentVariant.File> {
            return files
        }

        val artifacts: ImmutableList<out ComponentArtifactMetadata?>
            get() {
                val artifacts =
                    ImmutableList.Builder<ComponentArtifactMetadata?>()
                for (file in files) {
                    artifacts.add(UrlBackedArtifactMetadata(componentId, file.getName(), file.getUri()))
                }
                return artifacts.build()
            }

        override fun isExternalVariant(): Boolean {
            return externalVariant
        }

        override fun equals(o: Any?): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }

            val that = o as ImmutableRealisedVariantImpl
            return Objects.equal(componentId, that.componentId)
                    && Objects.equal(name, that.name)
                    && Objects.equal(attributes, that.attributes)
                    && Objects.equal(dependencies, that.dependencies)
                    && Objects.equal(dependencyConstraints, that.dependencyConstraints)
                    && Objects.equal(files, that.files)
                    && externalVariant == that.externalVariant
        }

        override fun hashCode(): Int {
            return Objects.hashCode(
                componentId,
                name,
                attributes,
                dependencies,
                dependencyConstraints,
                files,
                externalVariant
            )
        }
    }
}
