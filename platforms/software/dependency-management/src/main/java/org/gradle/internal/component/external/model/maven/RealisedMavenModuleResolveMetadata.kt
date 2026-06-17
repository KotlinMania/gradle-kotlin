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
package org.gradle.internal.component.external.model.maven

import com.google.common.base.Objects
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Maps
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.NamedVariantIdentifier
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.PomReader
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.model.NamedObjectInstantiator
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.component.external.descriptor.Configuration
import org.gradle.internal.component.external.model.AbstractConfigurationMetadata.getName
import org.gradle.internal.component.external.model.AbstractLazyModuleComponentResolveMetadata.equals
import org.gradle.internal.component.external.model.AbstractLazyModuleComponentResolveMetadata.getConfigurationNames
import org.gradle.internal.component.external.model.AbstractModuleComponentResolveMetadata.equals
import org.gradle.internal.component.external.model.AbstractModuleComponentResolveMetadata.getAttributes
import org.gradle.internal.component.external.model.AbstractModuleComponentResolveMetadata.getId
import org.gradle.internal.component.external.model.AbstractRealisedModuleComponentResolveMetadata
import org.gradle.internal.component.external.model.AbstractRealisedModuleComponentResolveMetadata.getConfigurationNames
import org.gradle.internal.component.external.model.ComponentVariant
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactMetadata
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
import org.gradle.internal.component.model.ConfigurationGraphResolveState.getName
import org.gradle.internal.component.model.ConfigurationMetadata
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.component.model.DependencyMetadata
import org.gradle.internal.component.model.ExcludeMetadata
import org.gradle.internal.component.model.ModuleConfigurationMetadata
import org.gradle.internal.component.model.ModuleSources
import org.gradle.internal.component.model.VariantIdentifier
import java.util.Optional
import java.util.function.Function
import java.util.stream.Collectors

/**
 * [Realised version][AbstractRealisedModuleComponentResolveMetadata] of a [MavenModuleResolveMetadata].
 *
 * @see DefaultMavenModuleResolveMetadata
 */
class RealisedMavenModuleResolveMetadata : AbstractRealisedModuleComponentResolveMetadata, MavenModuleResolveMetadata {
    private val objectInstantiator: NamedObjectInstantiator

    private val dependencies: ImmutableList<MavenDependencyDescriptor>
    private val packaging: String
    private val relocated: Boolean
    private val snapshotTimestamp: String

    val derivedVariants: ImmutableList<out ModuleConfigurationMetadata>

    internal constructor(
        metadata: DefaultMavenModuleResolveMetadata, variants: ImmutableList<out ComponentVariant>,
        derivedVariants: MutableList<ModuleConfigurationMetadata>, configurations: MutableMap<String, ModuleConfigurationMetadata>
    ) : super(metadata, variants, configurations) {
        this.objectInstantiator = metadata.getObjectInstantiator()
        packaging = metadata.getPackaging()
        relocated = metadata.isRelocated()
        snapshotTimestamp = metadata.getSnapshotTimestamp()!!
        dependencies = metadata.getDependencies()
        this.derivedVariants = ImmutableList.copyOf<ModuleConfigurationMetadata>(derivedVariants)
    }

    private constructor(metadata: RealisedMavenModuleResolveMetadata, sources: ModuleSources, derivationStrategy: VariantDerivationStrategy) : super(metadata, sources, derivationStrategy) {
        this.objectInstantiator = metadata.objectInstantiator
        packaging = metadata.packaging
        relocated = metadata.relocated
        snapshotTimestamp = metadata.snapshotTimestamp
        dependencies = metadata.dependencies
        this.derivedVariants = metadata.derivedVariants
    }

    override fun maybeDeriveVariants(): Optional<MutableList<out ExternalModuleVariantGraphResolveMetadata>> {
        return Optional.of<MutableList<out ExternalModuleVariantGraphResolveMetadata>>(this.derivedVariants)
    }

    override fun withSources(sources: ModuleSources): RealisedMavenModuleResolveMetadata {
        return RealisedMavenModuleResolveMetadata(this, sources, variantDerivationStrategy!!)
    }

    override fun withDerivationStrategy(derivationStrategy: VariantDerivationStrategy): ModuleComponentResolveMetadata {
        if (variantDerivationStrategy === derivationStrategy) {
            return this
        }
        return RealisedMavenModuleResolveMetadata(this, getSources(), derivationStrategy)
    }

    override fun asMutable(): MutableMavenModuleResolveMetadata {
        return DefaultMutableMavenModuleResolveMetadata(this, objectInstantiator)
    }

    override fun getPackaging(): String {
        return packaging
    }

    override fun isRelocated(): Boolean {
        return relocated
    }

    override fun isPomPackaging(): Boolean {
        return DefaultMavenModuleResolveMetadata.Companion.POM_PACKAGING == packaging
    }

    override fun isKnownJarPackaging(): Boolean {
        return DefaultMavenModuleResolveMetadata.Companion.JAR_PACKAGINGS.contains(packaging)
    }

    override fun getSnapshotTimestamp(): String? {
        return snapshotTimestamp
    }

    override fun getDependencies(): ImmutableList<MavenDependencyDescriptor> {
        return dependencies
    }

    override fun equals(o: Any): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        if (!super.equals(o)) {
            return false
        }

        val that = o as RealisedMavenModuleResolveMetadata
        return relocated == that.relocated && Objects.equal(dependencies, that.dependencies)
                && Objects.equal(packaging, that.packaging)
                && Objects.equal(snapshotTimestamp, that.snapshotTimestamp)
    }

    override fun hashCode(): Int {
        return Objects.hashCode(
            super.hashCode(),
            dependencies,
            packaging,
            relocated,
            snapshotTimestamp
        )
    }

    companion object {
        /**
         * Factory method to transform a [DefaultMavenModuleResolveMetadata], which is lazy, into a realised version.
         *
         * @param metadata the lazy metadata to transform
         * @return the realised version of the metadata
         */
        @JvmStatic
        fun transform(metadata: DefaultMavenModuleResolveMetadata): RealisedMavenModuleResolveMetadata {
            val variantMetadataRules = metadata.variantMetadataRules
            val variants: ImmutableList<out ComponentVariant>? = LazyToRealisedModuleComponentResolveMetadataHelper.realiseVariants(metadata, variantMetadataRules!!, metadata.variants)
            val configurations: MutableMap<String, ModuleConfigurationMetadata> = Maps.newHashMapWithExpectedSize<String, ModuleConfigurationMetadata>(metadata.getConfigurationNames().size)
            var derivedVariants = ImmutableList.of<ModuleConfigurationMetadata>()
            if (variants!!.isEmpty()) {
                val sourceVariants = metadata.deriveVariants()
                if (sourceVariants.isPresent()) {
                    val builder = ImmutableList.Builder<ModuleConfigurationMetadata>()
                    for (sourceVariant in sourceVariants.get()) {
                        val dependencies = uncheckedCast<ImmutableList<ModuleDependencyMetadata>?>(sourceVariant.dependencies)
                        // We do not need to apply the rules manually to derived variants, because the derivation already
                        // instantiated 'derivedVariant' as 'DefaultConfigurationMetadata' which does the rules application
                        // automatically when calling the getters (done in the code below).
                        val id: VariantIdentifier = NamedVariantIdentifier(metadata.getId(), sourceVariant.name!!)
                        val derivedVariantMetadata = RealisedConfigurationMetadata(
                            sourceVariant.name!!,
                            id,
                            metadata.getId(),
                            sourceVariant.isTransitive,
                            sourceVariant.isVisible,
                            sourceVariant.hierarchy,
                            uncheckedCast<ImmutableList<out ModuleComponentArtifactMetadata>>(sourceVariant.artifacts)!!,
                            sourceVariant.excludes,
                            sourceVariant.attributes,
                            sourceVariant.capabilities,
                            dependencies!!,
                            false,
                            sourceVariant.isExternalVariant
                        )
                        builder.add(derivedVariantMetadata)
                    }
                    derivedVariants = builder.build()
                }
                derivedVariants = Companion.addVariantsFromRules(metadata, derivedVariants, variantMetadataRules)
            }
            for (configurationName in metadata.getConfigurationNames()) {
                configurations.put(configurationName, createConfiguration(metadata, configurationName))
            }
            return RealisedMavenModuleResolveMetadata(metadata, variants, derivedVariants, configurations)
        }

        private fun addVariantsFromRules(
            componentMetadata: ModuleComponentResolveMetadata,
            derivedVariants: ImmutableList<ModuleConfigurationMetadata>,
            variantMetadataRules: VariantMetadataRules
        ): ImmutableList<ModuleConfigurationMetadata> {
            val additionalVariants = variantMetadataRules.additionalVariants
            if (additionalVariants.isEmpty()) {
                return derivedVariants
            }
            val builder = ImmutableList.Builder<ModuleConfigurationMetadata>()
            builder.addAll(derivedVariants)
            val variantsByName: MutableMap<String, ModuleConfigurationMetadata> = derivedVariants.stream().collect(Collectors.toMap(ConfigurationMetadata::getName, Function.identity<Any>()))
            for (additionalVariant in additionalVariants) {
                val name = additionalVariant.name
                val baseName = additionalVariant.base
                val attributes: ImmutableAttributes
                val capabilities: ImmutableCapabilities
                val dependencies: MutableList<out ModuleDependencyMetadata>?
                val artifacts: ImmutableList<out ModuleComponentArtifactMetadata>?

                val baseConf = variantsByName.get(baseName)
                if (baseConf == null) {
                    attributes = componentMetadata.attributes
                    capabilities = ImmutableCapabilities.EMPTY
                    dependencies = ImmutableList.of<ModuleDependencyMetadata>()
                    artifacts = ImmutableList.of<ModuleComponentArtifactMetadata>()
                } else {
                    attributes = baseConf.attributes
                    capabilities = baseConf.capabilities
                    dependencies = baseConf.getDependencies()
                    artifacts = uncheckedCast<ImmutableList<out ModuleComponentArtifactMetadata>?>(baseConf.artifacts)
                }

                if (baseName == null || baseConf != null) {
                    builder.add(
                        Companion.applyRules(
                            componentMetadata.getId()!!,
                            name!!,
                            variantMetadataRules,
                            attributes,
                            capabilities,
                            dependencies!!,
                            artifacts!!,
                            true,
                            true,
                            ImmutableSet.of<String>(),
                            true,
                            false
                        )
                    )
                } else if (!additionalVariant.isLenient) {
                    throw InvalidUserDataException("Variant '" + baseName + "' not defined in module " + componentMetadata.getId()!!.getDisplayName())
                }
            }
            return builder.build()
        }

        private fun applyRules(
            id: ModuleComponentIdentifier,
            configurationName: String,
            variantMetadataRules: VariantMetadataRules,
            attributes: ImmutableAttributes,
            capabilities: ImmutableCapabilities,
            dependencies: MutableList<out ModuleDependencyMetadata>,
            artifacts: ImmutableList<out ModuleComponentArtifactMetadata>,
            transitive: Boolean,
            visible: Boolean,
            hierarchy: ImmutableSet<String>,
            addedByRule: Boolean,
            isExternalVariant: Boolean
        ): RealisedConfigurationMetadata {
            val variant = NameOnlyVariantResolveMetadata(configurationName)
            val variantAttributes = variantMetadataRules.applyVariantAttributeRules(variant, attributes)
            val variantCapabilities = variantMetadataRules.applyCapabilitiesRules(variant, capabilities)
            val dependenciesMetadata: MutableList<out DependencyMetadata> = variantMetadataRules.applyDependencyMetadataRules(variant, dependencies)
            val artifactsMetadata: ImmutableList<out ModuleComponentArtifactMetadata> = variantMetadataRules.applyVariantFilesMetadataRulesToArtifacts(variant, artifacts, id)
            return createConfiguration(
                id,
                configurationName,
                transitive,
                visible,
                hierarchy,
                artifactsMetadata,
                dependenciesMetadata,
                variantAttributes,
                variantCapabilities,
                addedByRule,
                isExternalVariant
            )
        }

        private fun createConfiguration(metadata: DefaultMavenModuleResolveMetadata, configurationName: String): RealisedConfigurationMetadata {
            val configurationDefinitions: ImmutableMap<String, Configuration> = metadata.configurationDefinitions
            val configuration = metadata.configurationDefinitions.get(configurationName)
            val hierarchy: ImmutableSet<String> = LazyToRealisedModuleComponentResolveMetadataHelper.constructHierarchy(configuration!!, configurationDefinitions)
            return Companion.createConfiguration(
                metadata.getId(), configurationName, configuration.isTransitive(), configuration.isVisible(), hierarchy,
                getArtifactsForConfiguration(metadata), metadata.getConfiguration(configurationName)!!.getDependencies()!!,
                metadata.getAttributes(), ImmutableCapabilities.EMPTY, false, metadata.isExternalVariant
            )
        }

        private fun createConfiguration(
            componentId: ModuleComponentIdentifier,
            name: String,
            transitive: Boolean,
            visible: Boolean,
            hierarchy: ImmutableSet<String>,
            artifacts: ImmutableList<out ModuleComponentArtifactMetadata>,
            dependencies: MutableList<out DependencyMetadata>,
            attributes: ImmutableAttributes,
            capabilities: ImmutableCapabilities,
            addedByRule: Boolean,
            isExternalVariant: Boolean
        ): RealisedConfigurationMetadata {
            val asImmutable: ImmutableList<ModuleDependencyMetadata> = ImmutableList.copyOf<ModuleDependencyMetadata>(uncheckedCast<MutableList<ModuleDependencyMetadata>?>(dependencies))
            val id: VariantIdentifier = NamedVariantIdentifier(componentId, name)
            return RealisedConfigurationMetadata(
                name,
                id,
                componentId,
                transitive,
                visible,
                hierarchy,
                artifacts,
                ImmutableList.of<ExcludeMetadata>(),
                attributes,
                capabilities,
                asImmutable,
                addedByRule,
                isExternalVariant
            )
        }

        fun getArtifactsForConfiguration(metadata: DefaultMavenModuleResolveMetadata): ImmutableList<out ModuleComponentArtifactMetadata> {
            if (metadata.isRelocated()) {
                // relocated packages have no artifacts
                return ImmutableList.of<ModuleComponentArtifactMetadata>()
            } else if (metadata.isPomPackaging()) {
                // Modules with POM packaging _may_ have a jar
                return ImmutableList.of<ModuleComponentArtifactMetadata?>(metadata.optionalArtifact("jar", "jar", null))
            } else if (metadata.isKnownJarPackaging()) {
                // Modules with a type of packaging that's always a jar
                return ImmutableList.of<ModuleComponentArtifactMetadata?>(metadata.artifact("jar", "jar", null))
            } else {
                val type = metadata.getPackaging()
                // We were unable to resolve variable substitutions in the POM, so assume we're looking for a jar
                if (PomReader.hasUnresolvedSubstitutions(type)) {
                    return ImmutableList.of<ModuleComponentArtifactMetadata?>(metadata.artifact("jar", "jar", null))
                } else {
                    // Modules with other types of packaging may publish an artifact with that extension or a jar
                    return ImmutableList.of<DefaultModuleComponentArtifactMetadata>(
                        DefaultModuleComponentArtifactMetadata(
                            metadata.getId(), DefaultIvyArtifactName(metadata.getId().getModule(), type, type),
                            metadata.artifact("jar", "jar", null)
                        )
                    )
                }
            }
        }
    }
}
