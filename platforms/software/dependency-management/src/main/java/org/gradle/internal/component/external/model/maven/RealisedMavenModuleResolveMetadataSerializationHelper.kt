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

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Maps
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.NamedVariantIdentifier
import org.gradle.api.internal.artifacts.capability.CapabilitySelectorSerializer
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.AttributeContainerSerializer
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.IvyArtifactNameSerializer
import org.gradle.internal.component.external.descriptor.Configuration
import org.gradle.internal.component.external.descriptor.MavenScope
import org.gradle.internal.component.external.model.AbstractModuleComponentResolveMetadata.getId
import org.gradle.internal.component.external.model.AbstractRealisedModuleComponentResolveMetadata
import org.gradle.internal.component.external.model.AbstractRealisedModuleResolveMetadataSerializationHelper
import org.gradle.internal.component.external.model.ExternalDependencyDescriptor
import org.gradle.internal.component.external.model.ForcedDependencyMetadataWrapper
import org.gradle.internal.component.external.model.GradleDependencyMetadata
import org.gradle.internal.component.external.model.LazyToRealisedModuleComponentResolveMetadataHelper
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata.getId
import org.gradle.internal.component.external.model.ModuleDependencyMetadata
import org.gradle.internal.component.external.model.RealisedConfigurationMetadata
import org.gradle.internal.component.model.ConfigurationMetadata
import org.gradle.internal.component.model.DependencyMetadata
import org.gradle.internal.component.model.ExcludeMetadata
import org.gradle.internal.component.model.ModuleConfigurationMetadata
import org.gradle.internal.component.model.VariantIdentifier
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import java.io.IOException

class RealisedMavenModuleResolveMetadataSerializationHelper(
    attributeContainerSerializer: AttributeContainerSerializer,
    capabilitySelectorSerializer: CapabilitySelectorSerializer,
    moduleIdentifierFactory: ImmutableModuleIdentifierFactory
) : AbstractRealisedModuleResolveMetadataSerializationHelper(attributeContainerSerializer, capabilitySelectorSerializer, moduleIdentifierFactory) {
    @Throws(IOException::class)
    public override fun writeRealisedConfigurationsData(
        encoder: Encoder,
        transformed: AbstractRealisedModuleComponentResolveMetadata,
        deduplicationDependencyCache: MutableMap<ExternalDependencyDescriptor, Int>
    ) {
        super.writeRealisedConfigurationsData(encoder, transformed, deduplicationDependencyCache)
        if (transformed is RealisedMavenModuleResolveMetadata) {
            writeDerivedVariants(encoder, transformed, deduplicationDependencyCache)
        }
    }

    @Throws(IOException::class)
    fun readMetadata(decoder: Decoder, resolveMetadata: DefaultMavenModuleResolveMetadata, deduplicationDependencyCache: MutableMap<Int, MavenDependencyDescriptor>): ModuleComponentResolveMetadata {
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

        val configurations = readMavenConfigurations(decoder, resolveMetadata, deduplicationDependencyCache)
        val derivedVariants = readDerivedVariants(decoder, resolveMetadata, deduplicationDependencyCache)

        return RealisedMavenModuleResolveMetadata(resolveMetadata, realisedVariants, derivedVariants, configurations)
    }

    @Throws(IOException::class)
    override fun writeDependencies(encoder: Encoder, configuration: ConfigurationMetadata, deduplicationDependencyCache: MutableMap<ExternalDependencyDescriptor, Int>) {
        val dependencies: MutableList<out DependencyMetadata> = configuration.dependencies!!
        encoder.writeSmallInt(dependencies.size)
        for (dependency in dependencies) {
            var dependency = dependency
            if (dependency is ForcedDependencyMetadataWrapper) {
                val wrapper = dependency
                dependency = wrapper.unwrap()
                if (wrapper.isForce()) {
                    encoder.writeByte(FORCED_DEPENDENCY_METADATA)
                }
            }
            if (dependency is GradleDependencyMetadata) {
                encoder.writeByte(GRADLE_DEPENDENCY_METADATA)
                writeDependencyMetadata(encoder, dependency)
            } else if (dependency is MavenDependencyMetadata) {
                val dependencyMetadata = dependency
                val dependencyDescriptor = dependencyMetadata.dependencyDescriptor
                encoder.writeByte(MAVEN_DEPENDENCY_METADATA)
                writeMavenDependency(encoder, dependencyDescriptor, deduplicationDependencyCache)
                encoder.writeNullableString(dependency.reason)
            } else {
                throw IllegalStateException("Unknown type of dependency: " + dependency.javaClass)
            }
        }
    }

    @Throws(IOException::class)
    private fun writeDerivedVariants(encoder: Encoder, metadata: RealisedMavenModuleResolveMetadata, deduplicationDependencyCache: MutableMap<ExternalDependencyDescriptor, Int>) {
        val derivedVariants: ImmutableList<out ConfigurationMetadata> = metadata.getDerivedVariants()
        encoder.writeSmallInt(derivedVariants.size)
        for (derivedVariant in derivedVariants) {
            writeConfiguration(encoder, derivedVariant)
            writeFiles(encoder, derivedVariant.artifacts)
            writeDerivedVariantExtra(encoder, derivedVariant, deduplicationDependencyCache)
        }
    }

    @Throws(IOException::class)
    private fun writeDerivedVariantExtra(encoder: Encoder, derivedVariant: ConfigurationMetadata, deduplicationDependencyCache: MutableMap<ExternalDependencyDescriptor, Int>) {
        encoder.writeBoolean(derivedVariant.isTransitive)
        encoder.writeBoolean(derivedVariant.isVisible)
        writeStringSet(encoder, derivedVariant.hierarchy)
        writeMavenExcludeRules(encoder, derivedVariant.excludes)
        writeDependencies(encoder, derivedVariant, deduplicationDependencyCache)
    }

    @Throws(IOException::class)
    private fun readMavenConfigurations(
        decoder: Decoder,
        metadata: DefaultMavenModuleResolveMetadata,
        deduplicationDependencyCache: MutableMap<Int, MavenDependencyDescriptor>
    ): MutableMap<String, ModuleConfigurationMetadata> {
        val configurationDefinitions: ImmutableMap<String, Configuration> = metadata.configurationDefinitions

        val configurationsCount = decoder.readSmallInt()
        val configurations: MutableMap<String, ModuleConfigurationMetadata> = Maps.newHashMapWithExpectedSize<String, ModuleConfigurationMetadata>(configurationsCount)
        for (i in 0..<configurationsCount) {
            val configurationName = decoder.readString()
            val configuration = configurationDefinitions.get(configurationName)
            val hierarchy: ImmutableSet<String> = LazyToRealisedModuleComponentResolveMetadataHelper.constructHierarchy(configuration!!, configurationDefinitions)
            val attributes = attributeContainerSerializer.read(decoder)
            val capabilities = readCapabilities(decoder)
            val isExternalVariant = decoder.readBoolean()
            val artifacts = readFiles(decoder, metadata.getId())

            val id: VariantIdentifier = NamedVariantIdentifier(metadata.getId(), configurationName!!)
            val configurationMetadata = RealisedConfigurationMetadata(
                configurationName, id, metadata.getId(), configuration.isTransitive(), configuration.isVisible(),
                hierarchy, artifacts, ImmutableList.of<ExcludeMetadata>(), attributes, capabilities, false, isExternalVariant
            )
            val dependencies = readDependencies(decoder, deduplicationDependencyCache)
            configurationMetadata.setDependencies(dependencies)
            configurations.put(configurationName, configurationMetadata)
        }
        return configurations
    }

    @Throws(IOException::class)
    private fun readDependencies(decoder: Decoder, deduplicationDependencyCache: MutableMap<Int, MavenDependencyDescriptor>): ImmutableList<ModuleDependencyMetadata> {
        val builder = ImmutableList.builder<ModuleDependencyMetadata>()
        val dependenciesCount = decoder.readSmallInt()
        if (dependenciesCount == 0) {
            return ImmutableList.of<ModuleDependencyMetadata>()
        }
        for (j in 0..<dependenciesCount) {
            var dependencyType = decoder.readByte()
            var force = false
            if (dependencyType == FORCED_DEPENDENCY_METADATA) {
                force = true
                dependencyType = decoder.readByte()
            }
            var md: ModuleDependencyMetadata?
            when (dependencyType) {
                GRADLE_DEPENDENCY_METADATA -> md = readDependencyMetadata(decoder)
                MAVEN_DEPENDENCY_METADATA -> {
                    val mavenDependencyDescriptor = readMavenDependency(decoder, deduplicationDependencyCache)
                    val reason = decoder.readNullableString()
                    md = MavenDependencyMetadata(mavenDependencyDescriptor, reason, false)
                }

                IVY_DEPENDENCY_METADATA -> throw IllegalStateException("Unexpected Ivy dependency for Maven module")
                else -> throw IllegalStateException("Unknown dependency type " + dependencyType)
            }
            if (force) {
                md = ForcedDependencyMetadataWrapper(md)
            }
            builder.add(md)
        }
        return builder.build()
    }

    @Throws(IOException::class)
    private fun readDerivedVariants(
        decoder: Decoder,
        resolveMetadata: DefaultMavenModuleResolveMetadata,
        deduplicationDependencyCache: MutableMap<Int, MavenDependencyDescriptor>
    ): ImmutableList<ModuleConfigurationMetadata> {
        val derivedVariantsCount = decoder.readSmallInt()
        if (derivedVariantsCount == 0) {
            return ImmutableList.of<ModuleConfigurationMetadata>()
        }
        val builder = ImmutableList.Builder<ModuleConfigurationMetadata>()
        for (i in 0..<derivedVariantsCount) {
            builder.add(readDerivedVariant(decoder, resolveMetadata, deduplicationDependencyCache))
        }
        return builder.build()
    }

    @Throws(IOException::class)
    private fun readDerivedVariant(
        decoder: Decoder,
        resolveMetadata: DefaultMavenModuleResolveMetadata,
        deduplicationDependencyCache: MutableMap<Int, MavenDependencyDescriptor>
    ): ModuleConfigurationMetadata {
        val name = decoder.readString()
        val attributes = attributeContainerSerializer.read(decoder)
        val immutableCapabilities = readCapabilities(decoder)
        val isExternalVariant = decoder.readBoolean()
        val artifacts = readFiles(decoder, resolveMetadata.getId())
        val transitive = decoder.readBoolean()
        val visible = decoder.readBoolean()
        val hierarchy: ImmutableSet<String> = ImmutableSet.copyOf<String>(readStringSet(decoder))
        val excludeMetadata: MutableList<ExcludeMetadata> = readMavenExcludes(decoder)
        val id: VariantIdentifier = NamedVariantIdentifier(resolveMetadata.getId(), name!!)
        val realized = RealisedConfigurationMetadata(
            name,
            id,
            resolveMetadata.getId(),
            transitive,
            visible,
            hierarchy,
            artifacts,
            ImmutableList.copyOf<ExcludeMetadata>(excludeMetadata),
            attributes,
            immutableCapabilities,
            false,
            isExternalVariant
        )
        val dependencies = readDependencies(decoder, deduplicationDependencyCache)
        realized.setDependencies(dependencies)
        return realized
    }

    @Throws(IOException::class)
    private fun readMavenDependency(decoder: Decoder, deduplicationDependencyCache: MutableMap<Int, MavenDependencyDescriptor>): MavenDependencyDescriptor {
        val mapping = decoder.readSmallInt()
        if (mapping == deduplicationDependencyCache.size) {
            val requested = componentSelectorSerializer.read(decoder)
            val artifactName = IvyArtifactNameSerializer.INSTANCE.readNullable(decoder)
            val mavenExcludes: MutableList<ExcludeMetadata> = readMavenExcludes(decoder)
            val scope = MavenScope.entries[decoder.readSmallInt()]
            val type = MavenDependencyType.entries[decoder.readSmallInt()]
            val mavenDependencyDescriptor = MavenDependencyDescriptor(scope, type, requested, artifactName, mavenExcludes)
            deduplicationDependencyCache.put(mapping, mavenDependencyDescriptor)
            return mavenDependencyDescriptor
        } else {
            val mavenDependencyDescriptor = checkNotNull(deduplicationDependencyCache.get(mapping))
            return mavenDependencyDescriptor
        }
    }

    @Throws(IOException::class)
    private fun writeMavenDependency(encoder: Encoder, mavenDependency: MavenDependencyDescriptor, deduplicationDependencyCache: MutableMap<ExternalDependencyDescriptor, Int>) {
        val nextMapping = deduplicationDependencyCache.size
        val mapping = deduplicationDependencyCache.putIfAbsent(mavenDependency, nextMapping)
        if (mapping != null) {
            encoder.writeSmallInt(mapping)
        } else {
            encoder.writeSmallInt(nextMapping)
            componentSelectorSerializer.write(encoder, mavenDependency.selector)
            IvyArtifactNameSerializer.INSTANCE.writeNullable(encoder, mavenDependency.getDependencyArtifact())
            writeMavenExcludeRules(encoder, mavenDependency.getAllExcludes())
            encoder.writeSmallInt(mavenDependency.getScope().ordinal)
            encoder.writeSmallInt(mavenDependency.getType().ordinal)
        }
    }
}
