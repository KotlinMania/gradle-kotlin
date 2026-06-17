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
import com.google.common.collect.ImmutableSet
import com.google.common.collect.LinkedHashMultimap
import com.google.common.collect.Maps
import com.google.common.collect.SetMultimap
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.NamedVariantIdentifier
import org.gradle.api.internal.artifacts.capability.CapabilitySelectorSerializer
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.AttributeContainerSerializer
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.IvyArtifactNameSerializer
import org.gradle.internal.component.external.descriptor.Artifact
import org.gradle.internal.component.external.model.AbstractConfigurationMetadata.getName
import org.gradle.internal.component.external.model.AbstractModuleComponentResolveMetadata.getId
import org.gradle.internal.component.external.model.AbstractRealisedModuleComponentResolveMetadata
import org.gradle.internal.component.external.model.AbstractRealisedModuleResolveMetadataSerializationHelper
import org.gradle.internal.component.external.model.ExternalDependencyDescriptor
import org.gradle.internal.component.external.model.GradleDependencyMetadata
import org.gradle.internal.component.external.model.LazyToRealisedModuleComponentResolveMetadataHelper
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata.getId
import org.gradle.internal.component.external.model.ModuleDependencyMetadata
import org.gradle.internal.component.external.model.RealisedConfigurationMetadata
import org.gradle.internal.component.model.ConfigurationGraphResolveState.getName
import org.gradle.internal.component.model.ConfigurationMetadata
import org.gradle.internal.component.model.DependencyMetadata
import org.gradle.internal.component.model.Exclude
import org.gradle.internal.component.model.ExcludeMetadata
import org.gradle.internal.component.model.ModuleConfigurationMetadata
import org.gradle.internal.component.model.VariantIdentifier
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import java.io.IOException
import java.util.IdentityHashMap

class RealisedIvyModuleResolveMetadataSerializationHelper(
    attributeContainerSerializer: AttributeContainerSerializer,
    capabilitySelectorSerializer: CapabilitySelectorSerializer,
    moduleIdentifierFactory: ImmutableModuleIdentifierFactory
) : AbstractRealisedModuleResolveMetadataSerializationHelper(attributeContainerSerializer, capabilitySelectorSerializer, moduleIdentifierFactory) {
    @Throws(IOException::class)
    fun readMetadata(decoder: Decoder, resolveMetadata: DefaultIvyModuleResolveMetadata): ModuleComponentResolveMetadata {
        val variantToDependencies: MutableMap<String, MutableList<GradleDependencyMetadata>> = readVariantDependencies(decoder)
        val variants = resolveMetadata.variants
        val builder = ImmutableList.builder<AbstractRealisedModuleComponentResolveMetadata.ImmutableRealisedVariantImpl>()
        for (variant in variants) {
            builder.add(
                AbstractRealisedModuleComponentResolveMetadata.ImmutableRealisedVariantImpl(
                    resolveMetadata.getId(), variant.name!!, variant.attributes.asImmutable(), variant.dependencies, variant.dependencyConstraints,
                    variant.files, variant.capabilities, variantToDependencies.get(variant.name)!!, variant.isExternalVariant()
                )
            )
        }
        val realisedVariants = builder.build()
        return RealisedIvyModuleResolveMetadata(resolveMetadata, realisedVariants, readIvyConfigurations(decoder, resolveMetadata))
    }

    @Throws(IOException::class)
    override fun writeDependencies(encoder: Encoder, configuration: ConfigurationMetadata, deduplicationDependencyCache: MutableMap<ExternalDependencyDescriptor, Int>) {
        val dependencies: MutableList<out DependencyMetadata> = configuration.dependencies!!
        encoder.writeSmallInt(dependencies.size)
        for (dependency in dependencies) {
            if (dependency is GradleDependencyMetadata) {
                encoder.writeByte(GRADLE_DEPENDENCY_METADATA)
                writeDependencyMetadata(encoder, dependency)
            } else if (dependency is IvyDependencyMetadata) {
                val dependencyMetadata = dependency
                val dependencyDescriptor = dependencyMetadata.dependencyDescriptor
                encoder.writeByte(IVY_DEPENDENCY_METADATA)
                val addedByRule = configuration is RealisedConfigurationMetadata && configuration.isAddedByRule
                writeIvyDependency(encoder, dependencyDescriptor, configuration.name!!, addedByRule)
                encoder.writeNullableString(dependency.reason)
            } else {
                throw IllegalStateException("Unknown type of dependency: " + dependency.javaClass)
            }
        }
    }

    @Throws(IOException::class)
    override fun writeConfiguration(encoder: Encoder, configuration: ConfigurationMetadata) {
        super.writeConfiguration(encoder, configuration)
        if (configuration is RealisedConfigurationMetadata) {
            val realisedMetadata = configuration
            if (realisedMetadata.isAddedByRule) {
                encoder.writeBoolean(true)
                writeMavenExcludeRules(encoder, realisedMetadata.getExcludes())
            } else {
                encoder.writeBoolean(false)
            }
        } else {
            encoder.writeBoolean(false)
        }
    }

    @Throws(IOException::class)
    private fun readIvyConfigurations(decoder: Decoder, metadata: DefaultIvyModuleResolveMetadata): MutableMap<String, ModuleConfigurationMetadata> {
        val configurationHelper = IvyConfigurationHelper(
            metadata.getArtifactDefinitions(),
            IdentityHashMap<Artifact?, ModuleComponentArtifactMetadata?>(),
            metadata.getExcludes(),
            metadata.getDependencies(),
            metadata.getId()
        )

        val configurationDefinitions = metadata.configurationDefinitions
        val configurationsCount = decoder.readSmallInt()
        val configurations: MutableMap<String, ModuleConfigurationMetadata> = Maps.newHashMapWithExpectedSize<String, ModuleConfigurationMetadata>(configurationsCount)

        for (i in 0..<configurationsCount) {
            val configurationName = decoder.readString()
            var transitive = true
            var visible = true
            var hierarchy = ImmutableSet.of<String>(configurationName!!)
            var excludes: ImmutableList<ExcludeMetadata>?

            val configuration = configurationDefinitions.get(configurationName)
            if (configuration != null) { // if the configuration represents a variant added by a rule, it is not in the definition list
                transitive = configuration.isTransitive()
                visible = configuration.isVisible()
                hierarchy = LazyToRealisedModuleComponentResolveMetadataHelper.constructHierarchy(configuration, configurationDefinitions)
                excludes = configurationHelper.filterExcludes(hierarchy)
            } else {
                excludes = ImmutableList.of<ExcludeMetadata>()
            }

            val attributes = attributeContainerSerializer.read(decoder)
            val capabilities = readCapabilities(decoder)
            val isExternalVariant = decoder.readBoolean()
            val hasExplicitExcludes = decoder.readBoolean()
            if (hasExplicitExcludes) {
                excludes = ImmutableList.copyOf<ExcludeMetadata>(readMavenExcludes(decoder))
            }
            val artifacts = readFiles(decoder, metadata.getId())

            val id: VariantIdentifier = NamedVariantIdentifier(metadata.getId(), configurationName)
            val configurationMetadata = RealisedConfigurationMetadata(
                configurationName,
                id,
                metadata.getId(),
                transitive,
                visible,
                hierarchy,
                artifacts,
                excludes!!,
                attributes,
                capabilities,
                false,
                isExternalVariant
            )

            val builder = ImmutableList.builder<ModuleDependencyMetadata>()
            val dependenciesCount = decoder.readSmallInt()
            for (j in 0..<dependenciesCount) {
                val dependencyType = decoder.readByte()
                when (dependencyType) {
                    GRADLE_DEPENDENCY_METADATA -> builder.add(readDependencyMetadata(decoder))
                    IVY_DEPENDENCY_METADATA -> {
                        val ivyDependency = readIvyDependency(decoder)
                        val reason = decoder.readNullableString()
                        builder.add(IvyDependencyMetadata(configurationMetadata, ivyDependency, reason, false))
                    }

                    MAVEN_DEPENDENCY_METADATA -> throw IllegalStateException("Unexpected Maven dependency for Ivy module")
                    else -> throw IllegalStateException("Unknown dependency type " + dependencyType)
                }
            }
            val dependencies = builder.build()
            configurationMetadata.setDependencies(dependencies)

            configurations.put(configurationName, configurationMetadata)
        }
        return configurations
    }

    @Throws(IOException::class)
    private fun readIvyDependency(decoder: Decoder): IvyDependencyDescriptor {
        val requested = componentSelectorSerializer.read(decoder)
        val configMappings = readDependencyConfigurationMapping(decoder)
        val artifacts = readDependencyArtifactDescriptors(decoder)
        val excludes = readDependencyExcludes(decoder)
        val dynamicConstraintVersion = decoder.readString()
        val changing = decoder.readBoolean()
        val transitive = decoder.readBoolean()
        val optional = decoder.readBoolean()
        return IvyDependencyDescriptor(requested, dynamicConstraintVersion!!, changing, transitive, optional, configMappings, artifacts, excludes)
    }

    @Throws(IOException::class)
    private fun writeIvyDependency(encoder: Encoder, ivyDependency: IvyDependencyDescriptor, configurationName: String, configurationAddedByRule: Boolean) {
        componentSelectorSerializer.write(encoder, ivyDependency.selector)
        writeDependencyConfigurationMapping(encoder, ivyDependency, configurationName, configurationAddedByRule)
        writeArtifacts(encoder, ivyDependency.getDependencyArtifacts())
        writeExcludeRules(encoder, ivyDependency.getAllExcludes())
        encoder.writeString(ivyDependency.getDynamicConstraintVersion())
        encoder.writeBoolean(ivyDependency.isChanging)
        encoder.writeBoolean(ivyDependency.isTransitive)
        encoder.writeBoolean(ivyDependency.isOptional)
    }

    @Throws(IOException::class)
    private fun writeExcludeRules(encoder: Encoder, excludes: MutableList<Exclude>) {
        encoder.writeSmallInt(excludes.size)
        for (exclude in excludes) {
            encoder.writeString(exclude.moduleId.getGroup())
            encoder.writeString(exclude.moduleId.getName())
            val artifact = exclude.artifact
            IvyArtifactNameSerializer.INSTANCE.writeNullable(encoder, artifact)
            writeStringSet(encoder, exclude.configurations)
            encoder.writeNullableString(exclude.matcher)
        }
    }

    @Throws(IOException::class)
    private fun writeArtifacts(encoder: Encoder, artifacts: MutableList<Artifact>) {
        encoder.writeSmallInt(artifacts.size)
        for (artifact in artifacts) {
            IvyArtifactNameSerializer.INSTANCE.write(encoder, artifact.artifactName)
            writeStringSet(encoder, artifact.configurations)
        }
    }

    @Throws(IOException::class)
    private fun readDependencyConfigurationMapping(decoder: Decoder): SetMultimap<String, String> {
        val size = decoder.readSmallInt()
        val result: SetMultimap<String, String> = LinkedHashMultimap.create<String, String>()
        for (i in 0..<size) {
            val from = decoder.readString()
            val to: MutableSet<String> = readStringSet(decoder)
            result.putAll(from!!, to)
        }
        return result
    }

    @Throws(IOException::class)
    private fun writeDependencyConfigurationMapping(encoder: Encoder, dep: IvyDependencyDescriptor, configurationName: String, configurationAddedByRule: Boolean) {
        val confMappings = dep.getConfMappings()
        val mappingCount = confMappings.keySet().size + (if (configurationAddedByRule) 1 else 0)
        encoder.writeSmallInt(mappingCount)
        for (conf in confMappings.keySet()) {
            encoder.writeString(conf)
            writeStringSet(encoder, confMappings.get(conf))
        }
        if (configurationAddedByRule) {
            // since the dependencies are reconstructed from the serialized form which interprets the mappings,
            // we have to make sure to also map from the new configuration.
            encoder.writeString(configurationName)
            writeStringSet(encoder, ImmutableSet.copyOf<String?>(confMappings.values()))
        }
    }

    @Throws(IOException::class)
    private fun readDependencyArtifactDescriptors(decoder: Decoder): MutableList<Artifact> {
        val size = decoder.readSmallInt()
        val result: MutableList<Artifact> = ArrayList<Artifact>(size)
        for (i in 0..<size) {
            val ivyArtifactName = IvyArtifactNameSerializer.INSTANCE.read(decoder)
            result.add(Artifact(ivyArtifactName, readStringSet(decoder)))
        }
        return result
    }

    @Throws(IOException::class)
    private fun readDependencyExcludes(decoder: Decoder): MutableList<Exclude> {
        val len = decoder.readSmallInt()
        val result: MutableList<Exclude> = ArrayList<Exclude>(len)
        for (i in 0..<len) {
            val rule = readExcludeRule(decoder)
            result.add(rule)
        }
        return result
    }
}
