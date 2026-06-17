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
package org.gradle.internal.component.external.model.ivy

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Maps
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.NamedVariantIdentifier
import org.gradle.api.internal.artifacts.ivyservice.NamespaceId
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.component.external.descriptor.Artifact
import org.gradle.internal.component.external.descriptor.Configuration
import org.gradle.internal.component.external.model.AbstractLazyModuleComponentResolveMetadata.getConfigurationNames
import org.gradle.internal.component.external.model.AbstractModuleComponentResolveMetadata.getAttributes
import org.gradle.internal.component.external.model.AbstractModuleComponentResolveMetadata.getId
import org.gradle.internal.component.external.model.AbstractRealisedModuleComponentResolveMetadata
import org.gradle.internal.component.external.model.AbstractRealisedModuleComponentResolveMetadata.getConfigurationNames
import org.gradle.internal.component.external.model.ComponentVariant
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector.Companion.newSelector
import org.gradle.internal.component.external.model.ExternalModuleVariantGraphResolveMetadata
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.internal.component.external.model.LazyToRealisedModuleComponentResolveMetadataHelper
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata.getId
import org.gradle.internal.component.external.model.ModuleDependencyMetadata
import org.gradle.internal.component.external.model.RealisedConfigurationMetadata
import org.gradle.internal.component.external.model.VariantDerivationStrategy
import org.gradle.internal.component.external.model.VariantMetadataRules
import org.gradle.internal.component.model.DependencyMetadata
import org.gradle.internal.component.model.Exclude
import org.gradle.internal.component.model.ExcludeMetadata
import org.gradle.internal.component.model.ModuleConfigurationMetadata
import org.gradle.internal.component.model.ModuleSources
import org.gradle.internal.component.model.VariantIdentifier
import java.util.IdentityHashMap
import java.util.Optional

/**
 * [Realised version][AbstractRealisedModuleComponentResolveMetadata] of a [IvyModuleResolveMetadata].
 *
 * @see DefaultIvyModuleResolveMetadata
 */
class RealisedIvyModuleResolveMetadata : AbstractRealisedModuleComponentResolveMetadata, IvyModuleResolveMetadata {
    private val configurationDefinitions: ImmutableMap<String, Configuration>
    private val dependencies: ImmutableList<IvyDependencyDescriptor>
    private val artifactDefinitions: ImmutableList<Artifact>
    private val excludes: ImmutableList<Exclude>
    private val extraAttributes: ImmutableMap<NamespaceId, String>
    private val metadata: DefaultIvyModuleResolveMetadata
    private val branch: String

    private var derivedVariants: Optional<MutableList<out ExternalModuleVariantGraphResolveMetadata>>? = null

    private constructor(
        metadata: RealisedIvyModuleResolveMetadata,
        dependencies: MutableList<IvyDependencyDescriptor>,
        transformedConfigurations: MutableMap<String, ModuleConfigurationMetadata>
    ) : super(metadata, metadata.variants, transformedConfigurations) {
        this.configurationDefinitions = metadata.getConfigurationDefinitions()
        this.branch = metadata.getBranch()!!
        this.artifactDefinitions = metadata.getArtifactDefinitions()
        this.dependencies = ImmutableList.copyOf<IvyDependencyDescriptor>(dependencies)
        this.excludes = metadata.getExcludes()
        this.extraAttributes = metadata.getExtraAttributes()
        this.metadata = metadata.metadata
    }

    private constructor(metadata: RealisedIvyModuleResolveMetadata, sources: ModuleSources, derivationStrategy: VariantDerivationStrategy) : super(metadata, sources, derivationStrategy) {
        this.configurationDefinitions = metadata.configurationDefinitions
        this.branch = metadata.branch
        this.artifactDefinitions = metadata.artifactDefinitions
        this.dependencies = metadata.dependencies
        this.excludes = metadata.excludes
        this.extraAttributes = metadata.extraAttributes
        this.metadata = metadata.metadata
    }

    internal constructor(
        metadata: DefaultIvyModuleResolveMetadata,
        variants: ImmutableList<out ComponentVariant>,
        configurations: MutableMap<String, ModuleConfigurationMetadata>
    ) : super(metadata, variants, configurations) {
        this.configurationDefinitions = metadata.configurationDefinitions
        this.branch = metadata.getBranch()
        this.artifactDefinitions = metadata.getArtifactDefinitions()
        this.dependencies = metadata.getDependencies()
        this.excludes = metadata.getExcludes()
        this.extraAttributes = metadata.getExtraAttributes()
        this.metadata = metadata
    }

    override fun maybeDeriveVariants(): Optional<MutableList<out ExternalModuleVariantGraphResolveMetadata>> {
        if (derivedVariants == null && getConfigurationNames().size != configurationDefinitions.size) {
            // if there are more configurations than definitions, configurations have been added by rules and thus they are variants
            derivedVariants = Optional.of<MutableList<out ExternalModuleVariantGraphResolveMetadata>>(allConfigurationsThatAreVariants())
        } else {
            derivedVariants = Optional.empty<MutableList<out ExternalModuleVariantGraphResolveMetadata>>()
        }
        return derivedVariants!!
    }

    private fun allConfigurationsThatAreVariants(): ImmutableList<out ModuleConfigurationMetadata> {
        val builder = ImmutableList.Builder<ModuleConfigurationMetadata>()
        for (potentialVariantName in getConfigurationNames()) {
            if (!configurationDefinitions.containsKey(potentialVariantName)) {
                builder.add(getConfiguration(potentialVariantName)!!)
            }
        }
        return builder.build()
    }

    override fun asMutable(): MutableIvyModuleResolveMetadata {
        return metadata.asMutable()
    }

    override fun withSources(sources: ModuleSources): RealisedIvyModuleResolveMetadata {
        return RealisedIvyModuleResolveMetadata(this, sources, variantDerivationStrategy!!)
    }

    override fun withDerivationStrategy(derivationStrategy: VariantDerivationStrategy): ModuleComponentResolveMetadata {
        if (variantDerivationStrategy === derivationStrategy) {
            return this
        }
        return RealisedIvyModuleResolveMetadata(this, getSources(), derivationStrategy)
    }

    override fun getBranch(): String? {
        return branch
    }

    override fun getConfigurationDefinitions(): ImmutableMap<String, Configuration> {
        return configurationDefinitions
    }

    override fun getArtifactDefinitions(): ImmutableList<Artifact> {
        return artifactDefinitions
    }

    override fun getExcludes(): ImmutableList<Exclude> {
        return excludes
    }

    override fun getExtraAttributes(): ImmutableMap<NamespaceId, String> {
        return extraAttributes
    }

    override fun withDynamicConstraintVersions(): IvyModuleResolveMetadata {
        val descriptors = getDependencies()
        if (descriptors.isEmpty()) {
            return this
        }
        val transformedDescriptors: MutableMap<IvyDependencyDescriptor, IvyDependencyDescriptor> = Maps.newHashMapWithExpectedSize<IvyDependencyDescriptor, IvyDependencyDescriptor>(descriptors.size)
        for (dependency in descriptors) {
            val selector = dependency.selector
            val dynamicConstraintVersion = dependency.getDynamicConstraintVersion()
            val newSelector = newSelector(selector.getModuleIdentifier(), dynamicConstraintVersion)
            transformedDescriptors.put(dependency, dependency.withRequested(newSelector))
        }
        return this.withDependencies(transformedDescriptors)
    }

    override fun getDependencies(): ImmutableList<IvyDependencyDescriptor> {
        return dependencies
    }

    private fun withDependencies(transformed: MutableMap<IvyDependencyDescriptor, IvyDependencyDescriptor>): IvyModuleResolveMetadata {
        val transformedDescriptors = ImmutableList.copyOf<IvyDependencyDescriptor>(transformed.values)
        val configurationNames: MutableSet<String> = getConfigurationNames()
        val transformedConfigurations: MutableMap<String, ModuleConfigurationMetadata> = Maps.newHashMapWithExpectedSize<String, ModuleConfigurationMetadata>(configurationNames.size)
        for (name in configurationNames) {
            val configuration = getConfiguration(name) as RealisedConfigurationMetadata?
            val dependencies: MutableList<out DependencyMetadata> = configuration!!.dependencies
            val transformedConfigurationDependencies = ImmutableList.builder<ModuleDependencyMetadata>()
            for (dependency in dependencies) {
                if (dependency is IvyDependencyMetadata) {
                    val ivyDependency = dependency
                    val newDescriptor: IvyDependencyDescriptor = transformed.get(ivyDependency.dependencyDescriptor)!!
                    transformedConfigurationDependencies.add(ivyDependency.withDescriptor(newDescriptor))
                } else {
                    transformedConfigurationDependencies.add(dependency as ModuleDependencyMetadata)
                }
            }
            transformedConfigurations.put(name, configuration.withDependencies(transformedConfigurationDependencies.build()))
        }
        return RealisedIvyModuleResolveMetadata(this, transformedDescriptors, transformedConfigurations)
    }

    companion object {
        @JvmStatic
        fun transform(metadata: DefaultIvyModuleResolveMetadata): RealisedIvyModuleResolveMetadata {
            val variantMetadataRules = metadata.variantMetadataRules

            val variants: ImmutableList<ImmutableRealisedVariantImpl>? = LazyToRealisedModuleComponentResolveMetadataHelper.realiseVariants(metadata, variantMetadataRules!!, metadata.variants)

            val configurations: MutableMap<String, ModuleConfigurationMetadata> = Companion.realiseConfigurations(metadata, variantMetadataRules)

            if (variants!!.isEmpty()) {
                Companion.addVariantsFromRules(metadata, configurations, variantMetadataRules)
            }

            return RealisedIvyModuleResolveMetadata(metadata, variants, configurations)
        }

        private fun realiseConfigurations(metadata: DefaultIvyModuleResolveMetadata, variantMetadataRules: VariantMetadataRules): MutableMap<String, ModuleConfigurationMetadata> {
            val configurations: MutableMap<String, ModuleConfigurationMetadata> = Maps.newHashMapWithExpectedSize<String, ModuleConfigurationMetadata>(metadata.getConfigurationNames().size)
            for (configurationName in metadata.getConfigurationNames()) {
                configurations.put(configurationName, applyRules(metadata, variantMetadataRules, configurationName))
            }
            return configurations
        }

        private fun addVariantsFromRules(
            componentMetadata: DefaultIvyModuleResolveMetadata,
            declaredConfigurations: MutableMap<String, ModuleConfigurationMetadata>,
            variantMetadataRules: VariantMetadataRules
        ) {
            val additionalVariants = variantMetadataRules.additionalVariants
            if (additionalVariants.isEmpty()) {
                return
            }

            for (additionalVariant in additionalVariants) {
                val name = additionalVariant.name
                val baseName = additionalVariant.base
                val attributes: ImmutableAttributes
                val capabilities: ImmutableCapabilities
                val dependencies: MutableList<ModuleDependencyMetadata>?
                val artifacts: ImmutableList<out ModuleComponentArtifactMetadata>?
                val excludes: ImmutableList<ExcludeMetadata>?

                val baseConf = declaredConfigurations.get(baseName)
                if (baseConf == null) {
                    attributes = componentMetadata.getAttributes()
                    capabilities = ImmutableCapabilities.EMPTY
                    dependencies = ImmutableList.of<ModuleDependencyMetadata>()
                    artifacts = ImmutableList.of<ModuleComponentArtifactMetadata>()
                    excludes = ImmutableList.of<ExcludeMetadata>()
                } else {
                    attributes = baseConf.attributes
                    capabilities = baseConf.capabilities
                    dependencies = uncheckedCast<MutableList<ModuleDependencyMetadata>?>(baseConf.getDependencies())
                    artifacts = uncheckedCast<ImmutableList<out ModuleComponentArtifactMetadata>?>(baseConf.artifacts)
                    excludes = uncheckedCast<ImmutableList<ExcludeMetadata>?>(baseConf.excludes)
                }

                if (baseName == null || baseConf != null) {
                    declaredConfigurations.put(
                        name!!, Companion.applyRules(
                            componentMetadata.getId(),
                            name, variantMetadataRules, attributes, capabilities,
                            artifacts!!, excludes!!, true, true,
                            ImmutableSet.of<String>(), null, dependencies, true, false
                        )
                    )
                } else if (!additionalVariant.isLenient) {
                    throw InvalidUserDataException("Configuration '" + baseName + "' not defined in module " + componentMetadata.getId().getDisplayName())
                }
            }
        }

        private fun applyRules(metadata: DefaultIvyModuleResolveMetadata, variantMetadataRules: VariantMetadataRules, configurationName: String): RealisedConfigurationMetadata {
            val configurationDefinitions = metadata.configurationDefinitions
            val configuration = configurationDefinitions.get(configurationName)
            val configurationHelper = IvyConfigurationHelper(
                metadata.getArtifactDefinitions(),
                IdentityHashMap<Artifact?, ModuleComponentArtifactMetadata?>(),
                metadata.getExcludes(),
                metadata.getDependencies(),
                metadata.getId()
            )
            val hierarchy: ImmutableSet<String> = LazyToRealisedModuleComponentResolveMetadataHelper.constructHierarchy(configuration!!, configurationDefinitions)
            val excludes = configurationHelper.filterExcludes(hierarchy)

            val artifacts = configurationHelper.filterArtifacts(configurationName, hierarchy)

            return applyRules(
                metadata.getId(),
                configurationName,
                variantMetadataRules,
                metadata.getAttributes(),
                ImmutableCapabilities.EMPTY,
                artifacts,
                excludes,
                configuration.isTransitive(),
                configuration.isVisible(),
                hierarchy,
                configurationHelper,
                null,
                false,
                metadata.isExternalVariant
            )
        }

        private fun applyRules(
            id: ModuleComponentIdentifier,
            configurationName: String,
            variantMetadataRules: VariantMetadataRules,
            attributes: ImmutableAttributes,
            capabilities: ImmutableCapabilities,
            artifacts: ImmutableList<out ModuleComponentArtifactMetadata>,
            excludes: ImmutableList<ExcludeMetadata>,
            transitive: Boolean,
            visible: Boolean,
            hierarchy: ImmutableSet<String>,
            configurationHelper: IvyConfigurationHelper,
            dependenciesOverride: MutableList<ModuleDependencyMetadata>?,
            addedByRule: Boolean,
            isExternalVariant: Boolean
        ): RealisedConfigurationMetadata {
            val variant = NameOnlyVariantResolveMetadata(configurationName)
            val variantAttributes = variantMetadataRules.applyVariantAttributeRules(variant, attributes)
            val variantCapabilities = variantMetadataRules.applyCapabilitiesRules(variant, capabilities)
            val artifactsMetadata: ImmutableList<out ModuleComponentArtifactMetadata> = variantMetadataRules.applyVariantFilesMetadataRulesToArtifacts(variant, artifacts, id)
            return Companion.createConfiguration(
                id, configurationName, transitive, visible, hierarchy,
                artifactsMetadata, excludes, variantAttributes, variantCapabilities, variantMetadataRules, configurationHelper, dependenciesOverride!!, addedByRule, isExternalVariant
            )
        }

        private fun createConfiguration(
            componentId: ModuleComponentIdentifier,
            name: String,
            transitive: Boolean,
            visible: Boolean,
            hierarchy: ImmutableSet<String>,
            artifacts: ImmutableList<out ModuleComponentArtifactMetadata>,
            excludes: ImmutableList<ExcludeMetadata>,
            componentLevelAttributes: ImmutableAttributes,
            capabilities: ImmutableCapabilities,
            variantMetadataRules: VariantMetadataRules,
            configurationHelper: IvyConfigurationHelper,
            dependenciesFromRule: MutableList<ModuleDependencyMetadata>,
            addedByRule: Boolean,
            externalVariant: Boolean
        ): RealisedConfigurationMetadata {
            val id: VariantIdentifier = NamedVariantIdentifier(componentId, name)
            val configuration =
                RealisedConfigurationMetadata(name, id, componentId, transitive, visible, hierarchy, artifacts, excludes, componentLevelAttributes, capabilities, addedByRule, externalVariant)
            val dependencyMetadata: MutableList<ModuleDependencyMetadata>?
            if (configurationHelper != null) {
                dependencyMetadata = configurationHelper.filterDependencies(configuration)
            } else {
                dependencyMetadata = dependenciesFromRule
            }
            configuration.setDependencies(
                ImmutableList.copyOf<ModuleDependencyMetadata?>(
                    variantMetadataRules.applyDependencyMetadataRules<ModuleDependencyMetadata?>(
                        NameOnlyVariantResolveMetadata(
                            name
                        ), dependencyMetadata
                    )
                )
            )
            return configuration
        }
    }
}
