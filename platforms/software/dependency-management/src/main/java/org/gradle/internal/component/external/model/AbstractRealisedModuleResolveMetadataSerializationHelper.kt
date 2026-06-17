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

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Maps
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.capabilities.Capability
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.ModuleComponentSelectorSerializer
import org.gradle.api.internal.artifacts.capability.CapabilitySelectorSerializer
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DefaultExcludeRuleConverter
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.ExcludeRuleConverter
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.AttributeContainerSerializer
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.IvyArtifactNameSerializer
import org.gradle.api.internal.artifacts.repositories.resolver.MavenUniqueSnapshotComponentIdentifier
import org.gradle.api.internal.capabilities.ImmutableCapability
import org.gradle.api.internal.capabilities.ShadowedCapability
import org.gradle.internal.component.external.descriptor.DefaultExclude
import org.gradle.internal.component.model.ComponentArtifactMetadata
import org.gradle.internal.component.model.ComponentArtifactMetadata.getName
import org.gradle.internal.component.model.ConfigurationMetadata
import org.gradle.internal.component.model.ExcludeMetadata
import org.gradle.internal.component.model.IvyArtifactName
import org.gradle.internal.component.model.VariantGraphResolveMetadata.getName
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import java.io.IOException

abstract class AbstractRealisedModuleResolveMetadataSerializationHelper(
    @JvmField protected val attributeContainerSerializer: AttributeContainerSerializer,
    capabilitySelectorSerializer: CapabilitySelectorSerializer,
    private val moduleIdentifierFactory: ImmutableModuleIdentifierFactory
) {
    @JvmField
    protected val componentSelectorSerializer: ModuleComponentSelectorSerializer
    private val excludeRuleConverter: ExcludeRuleConverter

    init {
        this.componentSelectorSerializer = ModuleComponentSelectorSerializer(attributeContainerSerializer, capabilitySelectorSerializer)
        this.excludeRuleConverter = DefaultExcludeRuleConverter(moduleIdentifierFactory)
    }

    @Throws(IOException::class)
    fun writeRealisedVariantsData(encoder: Encoder, transformed: AbstractRealisedModuleComponentResolveMetadata) {
        encoder.writeSmallInt(transformed.variants!!.size)
        for (variant in transformed.variants!!) {
            if (variant is AbstractRealisedModuleComponentResolveMetadata.ImmutableRealisedVariantImpl) {
                val realisedVariant = variant
                encoder.writeString(realisedVariant.getName())
                encoder.writeSmallInt(realisedVariant.getDependencyMetadata().size)
                for (dependencyMetadata in realisedVariant.getDependencyMetadata()) {
                    if (dependencyMetadata is GradleDependencyMetadata) {
                        writeDependencyMetadata(encoder, dependencyMetadata)
                    }
                }
            } else {
                throw IllegalStateException("Unknown type of variant: " + variant.javaClass)
            }
        }
    }

    @Throws(IOException::class)
    open fun writeRealisedConfigurationsData(
        encoder: Encoder,
        transformed: AbstractRealisedModuleComponentResolveMetadata,
        deduplicationDependencyCache: MutableMap<ExternalDependencyDescriptor?, Int?>?
    ) {
        encoder.writeSmallInt(transformed.getConfigurationNames().size)
        for (configurationName in transformed.getConfigurationNames()) {
            val configuration: ConfigurationMetadata? = transformed.getConfiguration(configurationName)
            writeConfiguration(encoder, configuration!!)
            writeFiles(encoder, configuration.artifacts)
            writeDependencies(encoder, configuration, deduplicationDependencyCache)
        }
    }

    @Throws(IOException::class)
    protected open fun writeConfiguration(encoder: Encoder, configuration: ConfigurationMetadata) {
        checkNotNull(configuration)
        encoder.writeString(configuration.name)
        attributeContainerSerializer.write(encoder, configuration.attributes)
        writeCapabilities(encoder, configuration.capabilities)
        encoder.writeBoolean(configuration.isExternalVariant)
    }

    @Throws(IOException::class)
    protected fun readVariantDependencies(decoder: Decoder): MutableMap<String?, MutableList<GradleDependencyMetadata?>?> {
        val variantsCount = decoder.readSmallInt()
        val variantsToDependencies: MutableMap<String?, MutableList<GradleDependencyMetadata?>?> = Maps.newHashMapWithExpectedSize<String?, MutableList<GradleDependencyMetadata?>?>(variantsCount)
        for (i in 0..<variantsCount) {
            val variantName = decoder.readString()
            val dependencyCount = decoder.readSmallInt()
            val dependencies: MutableList<GradleDependencyMetadata?> = ArrayList<GradleDependencyMetadata?>(dependencyCount)
            for (j in 0..<dependencyCount) {
                dependencies.add(readDependencyMetadata(decoder))
            }
            variantsToDependencies.put(variantName, dependencies)
        }
        return variantsToDependencies
    }

    @Throws(IOException::class)
    protected fun readDependencyMetadata(decoder: Decoder): GradleDependencyMetadata {
        val selector = componentSelectorSerializer.read(decoder)
        val excludes: ImmutableList<ExcludeMetadata?> = readMavenExcludes(decoder)
        val constraint = decoder.readBoolean()
        val endorsing = decoder.readBoolean()
        val force = decoder.readBoolean()
        val reason = decoder.readNullableString()
        val artifact = IvyArtifactNameSerializer.INSTANCE.readNullable(decoder)
        return GradleDependencyMetadata(selector, excludes, constraint, endorsing, reason, force, artifact)
    }

    @Throws(IOException::class)
    protected fun readFiles(decoder: Decoder, componentIdentifier: ModuleComponentIdentifier): ImmutableList<out ModuleComponentArtifactMetadata?> {
        val artifacts = ImmutableList.Builder<ModuleComponentArtifactMetadata?>()
        val artifactsCount = decoder.readSmallInt()
        for (i in 0..<artifactsCount) {
            val artifactName = IvyArtifactNameSerializer.INSTANCE.read(decoder)
            val timestamp = decoder.readNullableString()

            val version: String?
            var cid = componentIdentifier
            if (timestamp != null) {
                version = decoder.readString()
                cid = MavenUniqueSnapshotComponentIdentifier(componentIdentifier.getModuleIdentifier(), version!!, timestamp)
            }

            val alternativeArtifact = decoder.readBoolean()
            var alternative: IvyArtifactName? = null
            if (alternativeArtifact) {
                alternative = IvyArtifactNameSerializer.INSTANCE.read(decoder)
            }
            val optional = decoder.readBoolean()

            if (optional) {
                artifacts.add(ModuleComponentOptionalArtifactMetadata(cid, artifactName))
            } else {
                if (alternativeArtifact) {
                    artifacts.add(
                        DefaultModuleComponentArtifactMetadata(
                            cid, artifactName,
                            DefaultModuleComponentArtifactMetadata(cid, alternative)
                        )
                    )
                } else {
                    artifacts.add(DefaultModuleComponentArtifactMetadata(cid, artifactName))
                }
            }
        }
        val filesCount = decoder.readSmallInt()
        for (i in 0..<filesCount) {
            val fileName = decoder.readString()
            val uri = decoder.readString()
            artifacts.add(UrlBackedArtifactMetadata(componentIdentifier, fileName!!, uri!!))
        }
        return artifacts.build()
    }

    @Throws(IOException::class)
    protected fun readMavenExcludes(decoder: Decoder): ImmutableList<ExcludeMetadata?> {
        val excludeCount = decoder.readSmallInt()
        val excludes = ImmutableList.builderWithExpectedSize<ExcludeMetadata?>(excludeCount)
        for (i in 0..<excludeCount) {
            val group = decoder.readString()
            val name = decoder.readString()
            excludes.add(excludeRuleConverter.createExcludeRule(group!!, name!!))
        }
        return excludes.build()
    }

    @Throws(IOException::class)
    protected fun writeFiles(encoder: Encoder, artifacts: ImmutableList<out ComponentArtifactMetadata>) {
        val fileArtifactsCount = artifacts.stream().filter { a: ComponentArtifactMetadata -> a is UrlBackedArtifactMetadata }.count().toInt()
        val ivyArtifactsCount = artifacts.size - fileArtifactsCount
        encoder.writeSmallInt(ivyArtifactsCount)
        for (artifact in artifacts) {
            if (artifact !is UrlBackedArtifactMetadata) {
                IvyArtifactNameSerializer.INSTANCE.write(encoder, artifact.getName())
                val componentId = artifact.getComponentId()
                if (componentId is MavenUniqueSnapshotComponentIdentifier) {
                    val uid = componentId
                    encoder.writeNullableString(uid.timestamp)
                    encoder.writeString(uid.getVersion())
                } else {
                    encoder.writeNullableString(null)
                }
                encoder.writeBoolean(artifact.getAlternativeArtifact().isPresent())
                if (artifact.getAlternativeArtifact().isPresent()) {
                    IvyArtifactNameSerializer.INSTANCE.write(encoder, artifact.getAlternativeArtifact().get().getName())
                }
                encoder.writeBoolean(artifact.isOptionalArtifact())
            }
        }
        encoder.writeSmallInt(fileArtifactsCount)
        for (file in artifacts) {
            if (file is UrlBackedArtifactMetadata) {
                encoder.writeString(file.fileName)
                encoder.writeString(file.relativeUrl)
            }
        }
    }

    @Throws(IOException::class)
    protected abstract fun writeDependencies(encoder: Encoder?, configuration: ConfigurationMetadata?, deduplicationDependencyCache: MutableMap<ExternalDependencyDescriptor?, Int?>?)

    @Throws(IOException::class)
    protected fun writeDependencyMetadata(encoder: Encoder, dependencyMetadata: GradleDependencyMetadata) {
        componentSelectorSerializer.write(encoder, dependencyMetadata.selector)
        val excludes: MutableList<ExcludeMetadata> = dependencyMetadata.getExcludes()
        writeMavenExcludeRules(encoder, excludes)
        encoder.writeBoolean(dependencyMetadata.isConstraint())
        encoder.writeBoolean(dependencyMetadata.isEndorsingStrictVersions())
        encoder.writeBoolean(dependencyMetadata.isForce())
        encoder.writeNullableString(dependencyMetadata.getReason())
        IvyArtifactNameSerializer.INSTANCE.writeNullable(encoder, dependencyMetadata.getDependencyArtifact())
    }

    @Throws(IOException::class)
    protected fun writeMavenExcludeRules(encoder: Encoder, excludes: MutableList<ExcludeMetadata>) {
        encoder.writeSmallInt(excludes.size)
        for (exclude in excludes) {
            encoder.writeString(exclude.moduleId.getGroup())
            encoder.writeString(exclude.moduleId.getName())
        }
    }

    @Throws(IOException::class)
    protected fun readExcludeRule(decoder: Decoder): DefaultExclude {
        val moduleOrg = decoder.readString()
        val moduleName = decoder.readString()
        val artifactName = IvyArtifactNameSerializer.INSTANCE.readNullable(decoder)
        val confs = readStringSet(decoder).toTypedArray<String?>()
        val matcher = decoder.readNullableString()
        return DefaultExclude(moduleIdentifierFactory.module(moduleOrg!!, moduleName!!), artifactName, confs, matcher)
    }

    @Throws(IOException::class)
    protected fun readStringSet(decoder: Decoder): MutableSet<String?> {
        val size = decoder.readSmallInt()
        val set: MutableSet<String?> = LinkedHashSet<String?>(3 * size / 2, 0.9f)
        for (i in 0..<size) {
            set.add(decoder.readString())
        }
        return set
    }

    @Throws(IOException::class)
    protected fun writeStringSet(encoder: Encoder, values: MutableSet<String?>) {
        encoder.writeSmallInt(values.size)
        for (configuration in values) {
            encoder.writeString(configuration)
        }
    }

    companion object {
        protected const val GRADLE_DEPENDENCY_METADATA: Byte = 1
        protected const val MAVEN_DEPENDENCY_METADATA: Byte = 2
        protected const val IVY_DEPENDENCY_METADATA: Byte = 3
        protected const val FORCED_DEPENDENCY_METADATA: Byte = 4

        @JvmStatic
        @Throws(IOException::class)
        protected fun readCapabilities(decoder: Decoder): ImmutableCapabilities {
            val capabilitiesCount = decoder.readSmallInt()
            val rawCapabilities = ImmutableSet.builderWithExpectedSize<ImmutableCapability?>(capabilitiesCount)
            for (j in 0..<capabilitiesCount) {
                val appendix = decoder.readNullableString()
                var capability: ImmutableCapability = DefaultImmutableCapability(decoder.readString(), decoder.readString(), decoder.readString())
                if (appendix != null) {
                    capability = ShadowedImmutableCapability(capability, appendix)
                }
                rawCapabilities.add(capability)
            }
            return ImmutableCapabilities(rawCapabilities.build())
        }

        @Throws(IOException::class)
        private fun writeCapabilities(encoder: Encoder, capabilities: ImmutableCapabilities) {
            val capabilitiesSet = capabilities.asSet()
            encoder.writeSmallInt(capabilitiesSet.size)
            for (capability in capabilitiesSet) {
                var capability: Capability = capability
                val shadowed = capability is ShadowedCapability
                if (shadowed) {
                    val shadowedCapability = capability
                    encoder.writeNullableString(shadowedCapability.getAppendix())
                    capability = shadowedCapability.getShadowedCapability()
                } else {
                    encoder.writeNullableString(null)
                }
                encoder.writeString(capability.getGroup())
                encoder.writeString(capability.getName())
                encoder.writeString(capability.getVersion())
            }
        }
    }
}
