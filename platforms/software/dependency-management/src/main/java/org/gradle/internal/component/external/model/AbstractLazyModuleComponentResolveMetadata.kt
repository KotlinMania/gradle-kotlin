/*
 * Copyright 2013 the original author or authors.
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
import com.google.common.collect.ImmutableSet
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.NamedVariantIdentifier
import org.gradle.internal.component.external.descriptor.Configuration
import org.gradle.internal.component.model.ExcludeMetadata
import org.gradle.internal.component.model.ModuleConfigurationMetadata
import org.gradle.internal.component.model.ModuleSources
import org.gradle.internal.component.model.VariantGraphResolveMetadata
import org.gradle.internal.component.model.VariantIdentifier
import java.util.Optional
import java.util.function.Function
import java.util.stream.Collectors

/**
 * Common base class for the lazy versions of [ModuleComponentResolveMetadata] implementations.
 *
 * The lazy part is about the application of [VariantMetadataRules] which are applied lazily
 * when configuration or variant data is required by consumers.
 *
 * This type hierarchy is used whenever the `ModuleComponentResolveMetadata` does not need to outlive
 * the build execution.
 */
abstract class AbstractLazyModuleComponentResolveMetadata : AbstractModuleComponentResolveMetadata {
    @JvmField
    val variantMetadataRules: VariantMetadataRules?
    open val configurationDefinitions: ImmutableMap<String?, Configuration?>

    // Configurations are built on-demand, but only once.
    private val configurations: MutableMap<String?, ModuleConfigurationMetadata?> = HashMap<String?, ModuleConfigurationMetadata?>()

    private var graphVariants: Optional<MutableList<out ExternalModuleVariantGraphResolveMetadata?>?>? = null

    protected constructor(metadata: AbstractMutableModuleComponentResolveMetadata) : super(metadata) {
        this.configurationDefinitions = metadata.getConfigurationDefinitions()
        this.variantMetadataRules = metadata.variantMetadataRules
    }

    /**
     * Creates a copy of the given metadata
     */
    protected constructor(metadata: AbstractLazyModuleComponentResolveMetadata, sources: ModuleSources?, variantDerivationStrategy: VariantDerivationStrategy?) : super(
        metadata,
        sources,
        variantDerivationStrategy
    ) {
        this.configurationDefinitions = metadata.configurationDefinitions
        this.variantMetadataRules = metadata.variantMetadataRules
    }

    /**
     * Clear any cached state, for the case where the inputs are invalidated.
     * This only happens when constructing a copy
     */
    protected fun copyCachedState(metadata: AbstractLazyModuleComponentResolveMetadata, copyGraphVariants: Boolean) {
        // Copy built-on-demand state
        metadata.copyCachedConfigurations(this.configurations)
        if (copyGraphVariants) {
            this.graphVariants = metadata.graphVariants
        }
    }

    @Synchronized
    private fun copyCachedConfigurations(target: MutableMap<String?, ModuleConfigurationMetadata?>) {
        target.putAll(configurations)
    }

    private fun buildVariantsForGraphTraversal(): Optional<MutableList<out ExternalModuleVariantGraphResolveMetadata?>?>? {
        val variants: ImmutableList<out ComponentVariant?> = variants!!
        if (variants.isEmpty()) {
            return addVariantsByRule(maybeDeriveVariants())
        }
        val configurations = ImmutableList.Builder<ExternalModuleVariantGraphResolveMetadata?>()
        for (variant in variants) {
            configurations.add(LazyVariantBackedConfigurationMetadata(getId(), variant, getAttributes(), attributesFactory, variantMetadataRules))
        }
        return addVariantsByRule(Optional.of<MutableList<out ExternalModuleVariantGraphResolveMetadata?>?>(configurations.build()))
    }

    private fun addVariantsByRule(variants: Optional<MutableList<out ExternalModuleVariantGraphResolveMetadata?>?>): Optional<MutableList<out ExternalModuleVariantGraphResolveMetadata?>?>? {
        if (variantMetadataRules!!.additionalVariants.isEmpty()) {
            return variants
        }
        val variantsByName: MutableMap<String?, ExternalModuleVariantGraphResolveMetadata?>?
        val builder = ImmutableList.Builder<ExternalModuleVariantGraphResolveMetadata?>()
        if (variants.isPresent()) {
            variantsByName = variants.get().stream().collect(Collectors.toMap({ obj: VariantGraphResolveMetadata? -> obj!!.getName() }, Function.identity()))
            builder.addAll(variants.get())
        } else {
            variantsByName = mutableMapOf<String?, ExternalModuleVariantGraphResolveMetadata?>()
        }
        for (additionalVariant in variantMetadataRules.additionalVariants) {
            val baseName = additionalVariant.getBase()
            var base: ExternalModuleVariantGraphResolveMetadata? = null
            if (baseName != null) {
                if (variants.isPresent()) {
                    base = variantsByName.get(baseName)
                    if (!additionalVariant.isLenient() && base !is ModuleConfigurationMetadata) {
                        throw InvalidUserDataException("Variant '" + baseName + "' not defined in module " + getId()!!.getDisplayName())
                    }
                } else {
                    base = getConfiguration(baseName)
                    if (!additionalVariant.isLenient() && base !is ModuleConfigurationMetadata) {
                        throw InvalidUserDataException("Configuration '" + baseName + "' not defined in module " + getId()!!.getDisplayName())
                    }
                }
            }
            if (baseName == null || base is ModuleConfigurationMetadata) {
                val id: VariantIdentifier = NamedVariantIdentifier(getId()!!, additionalVariant.getName())
                val configurationMetadata: ModuleConfigurationMetadata = LazyRuleAwareWithBaseConfigurationMetadata(
                    additionalVariant.getName(),
                    id,
                    getId(),
                    base as ModuleConfigurationMetadata?,
                    attributesFactory,
                    getAttributes(),
                    variantMetadataRules,
                    constructVariantExcludes(base),
                    false
                )
                builder.add(configurationMetadata)
            }
        }
        return Optional.of<MutableList<out ExternalModuleVariantGraphResolveMetadata?>?>(builder.build())
    }

    @Synchronized
    override fun getVariantsForGraphTraversal(): MutableList<out ExternalModuleVariantGraphResolveMetadata?> {
        if (graphVariants == null) {
            graphVariants = buildVariantsForGraphTraversal()
        }
        return graphVariants!!.orElse(mutableListOf<ExternalModuleVariantGraphResolveMetadata?>())!!
    }

    override fun getConfigurationNames(): MutableSet<String?> {
        return configurationDefinitions.keys
    }

    @Synchronized
    override fun getConfiguration(name: String?): ModuleConfigurationMetadata? {
        val populated = configurations.get(name)
        if (populated != null) {
            return populated
        }
        val md = populateConfigurationFromDescriptor(name, configurationDefinitions)
        configurations.put(name, md)
        return md
    }

    protected open fun populateConfigurationFromDescriptor(name: String?, configurationDefinitions: MutableMap<String?, Configuration?>): ModuleConfigurationMetadata? {
        val descriptorConfiguration = configurationDefinitions.get(name)
        if (descriptorConfiguration == null) {
            return null
        }

        val hierarchy: ImmutableSet<String?> = constructHierarchy(descriptorConfiguration)
        val transitive = descriptorConfiguration.isTransitive()
        val visible = descriptorConfiguration.isVisible()
        return createConfiguration(getId(), name, transitive, visible, hierarchy, variantMetadataRules)
    }

    private fun constructHierarchy(descriptorConfiguration: Configuration): ImmutableSet<String?> {
        if (descriptorConfiguration.extendsFrom.isEmpty()) {
            return ImmutableSet.of<String?>(descriptorConfiguration.name)
        }
        val accumulator = ImmutableSet.Builder<String?>()
        populateHierarchy(descriptorConfiguration, accumulator)
        return accumulator.build()
    }

    private fun populateHierarchy(metadata: Configuration, accumulator: ImmutableSet.Builder<String?>) {
        accumulator.add(metadata.name)
        for (parentName in metadata.extendsFrom) {
            val parent = configurationDefinitions.get(parentName)
            populateHierarchy(parent!!, accumulator)
        }
    }

    private fun constructVariantExcludes(base: ExternalModuleVariantGraphResolveMetadata?): ImmutableList<ExcludeMetadata?> {
        if (base == null) {
            return ImmutableList.of<ExcludeMetadata?>()
        }
        return ImmutableList.copyOf<ExcludeMetadata?>(base.getExcludes())
    }

    /**
     * Creates a [org.gradle.internal.component.model.ConfigurationMetadata] implementation for this component.
     */
    protected abstract fun createConfiguration(
        componentId: ModuleComponentIdentifier?,
        name: String?,
        transitive: Boolean,
        visible: Boolean,
        hierarchy: ImmutableSet<String?>?,
        componentMetadataRules: VariantMetadataRules?
    ): DefaultConfigurationMetadata?

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        if (!super.equals(o)) {
            return false
        }

        val that = o as AbstractLazyModuleComponentResolveMetadata
        return Objects.equal(configurationDefinitions, that.configurationDefinitions)
    }

    override fun hashCode(): Int {
        return Objects.hashCode(super.hashCode(), configurationDefinitions)
    }
}
