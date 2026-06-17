/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.modulecache

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import com.google.common.collect.LinkedHashMultimap
import com.google.common.collect.SetMultimap
import org.gradle.api.artifacts.capability.CapabilitySelector
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.capabilities.Capability
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.ModuleComponentSelectorSerializer
import org.gradle.api.internal.artifacts.capability.CapabilitySelectorSerializer
import org.gradle.api.internal.artifacts.ivyservice.NamespaceId
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DefaultExcludeRuleConverter
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.ExcludeRuleConverter
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.AttributeContainerSerializer
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.IvyArtifactNameSerializer
import org.gradle.api.internal.artifacts.repositories.metadata.IvyMutableModuleMetadataFactory
import org.gradle.api.internal.artifacts.repositories.metadata.MavenMutableModuleMetadataFactory
import org.gradle.api.internal.artifacts.repositories.resolver.MavenUniqueSnapshotComponentIdentifier
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.capabilities.CapabilityInternal
import org.gradle.api.internal.capabilities.ImmutableCapability
import org.gradle.api.internal.capabilities.ShadowedCapability
import org.gradle.internal.component.external.descriptor.Artifact
import org.gradle.internal.component.external.descriptor.Configuration
import org.gradle.internal.component.external.descriptor.DefaultExclude
import org.gradle.internal.component.external.descriptor.MavenScope
import org.gradle.internal.component.external.model.ComponentVariant
import org.gradle.internal.component.external.model.DefaultImmutableCapability
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier.Companion.newId
import org.gradle.internal.component.external.model.ExternalDependencyDescriptor
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata.isExternalVariant
import org.gradle.internal.component.external.model.MutableComponentVariant
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata.attributes
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata.isExternalVariant
import org.gradle.internal.component.external.model.ShadowedImmutableCapability
import org.gradle.internal.component.external.model.ivy.IvyDependencyDescriptor
import org.gradle.internal.component.external.model.ivy.IvyModuleResolveMetadata
import org.gradle.internal.component.external.model.maven.MavenDependencyDescriptor
import org.gradle.internal.component.external.model.maven.MavenDependencyType
import org.gradle.internal.component.external.model.maven.MavenModuleResolveMetadata
import org.gradle.internal.component.model.Exclude
import org.gradle.internal.component.model.ExcludeMetadata
import org.gradle.internal.component.model.IvyArtifactName
import org.gradle.internal.component.model.VariantResolveMetadata.capabilities
import org.gradle.internal.component.model.VariantResolveMetadata.isExternalVariant
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import java.io.IOException

class ModuleMetadataSerializer(
    attributeContainerSerializer: AttributeContainerSerializer,
    capabilitySelectorSerializer: CapabilitySelectorSerializer,
    private val mavenMetadataFactory: MavenMutableModuleMetadataFactory,
    private val ivyMetadataFactory: IvyMutableModuleMetadataFactory,
    moduleSourcesSerializer: ModuleSourcesSerializer
) {
    private val componentSelectorSerializer: ModuleComponentSelectorSerializer
    private val attributeContainerSerializer: AttributeContainerSerializer?
    private val moduleSourcesSerializer: ModuleSourcesSerializer

    init {
        this.attributeContainerSerializer = attributeContainerSerializer
        this.componentSelectorSerializer = ModuleComponentSelectorSerializer(attributeContainerSerializer, capabilitySelectorSerializer)
        this.moduleSourcesSerializer = moduleSourcesSerializer
    }

    @Throws(IOException::class)
    fun read(
        decoder: Decoder,
        moduleIdentifierFactory: ImmutableModuleIdentifierFactory,
        deduplicationDependencyCache: MutableMap<Int?, MavenDependencyDescriptor>
    ): MutableModuleComponentResolveMetadata {
        return ModuleMetadataSerializer.Reader(
            decoder,
            moduleIdentifierFactory,
            attributeContainerSerializer!!,
            componentSelectorSerializer,
            mavenMetadataFactory,
            ivyMetadataFactory,
            moduleSourcesSerializer
        ).read(deduplicationDependencyCache)
    }

    @Throws(IOException::class)
    fun write(encoder: Encoder, metadata: ModuleComponentResolveMetadata, deduplicationDependencyCache: MutableMap<ExternalDependencyDescriptor?, Int?>) {
        ModuleMetadataSerializer.Writer(encoder, attributeContainerSerializer!!, componentSelectorSerializer, moduleSourcesSerializer).write(metadata, deduplicationDependencyCache)
    }

    private class Writer(
        private val encoder: Encoder,
        private val attributeContainerSerializer: AttributeContainerSerializer,
        private val componentSelectorSerializer: ModuleComponentSelectorSerializer,
        private val moduleSourcesSerializer: ModuleSourcesSerializer
    ) {
        @Throws(IOException::class)
        fun write(metadata: ModuleComponentResolveMetadata, deduplicationDependencyCache: MutableMap<ExternalDependencyDescriptor?, Int?>) {
            if (metadata is IvyModuleResolveMetadata) {
                write(metadata)
            } else if (metadata is MavenModuleResolveMetadata) {
                write(metadata, deduplicationDependencyCache)
            } else {
                throw IllegalArgumentException("Unexpected metadata type: " + metadata.javaClass)
            }
        }

        @Throws(IOException::class)
        fun write(metadata: MavenModuleResolveMetadata, deduplicationDependencyCache: MutableMap<ExternalDependencyDescriptor?, Int?>) {
            encoder.writeByte(TYPE_MAVEN)
            writeInfoSection(metadata)
            writeNullableString(metadata.snapshotTimestamp)
            writeMavenDependencies(metadata.dependencies, deduplicationDependencyCache)
            writeSharedInfo(metadata)
            // NOTE: This looks nullable, but only non-null Strings are provided. Changing this to write a non-null string would not be backwards compatible.
            writeNullableString(metadata.packaging)
            writeBoolean(metadata.isRelocated)
            writeVariants(metadata)
        }

        @Throws(IOException::class)
        fun writeVariants(metadata: ModuleComponentResolveMetadata) {
            encoder.writeSmallInt(metadata.variants.size())
            for (variant in metadata.variants) {
                encoder.writeString(variant.name)
                writeAttributes(variant.attributes)
                writeVariantDependencies(variant.dependencies)
                writeVariantConstraints(variant.dependencyConstraints)
                writeVariantFiles(variant.files)
                writeVariantCapabilities(variant.capabilities)
                encoder.writeBoolean(variant.isExternalVariant())
            }
        }

        @Throws(IOException::class)
        fun writeVariantConstraints(constraints: ImmutableList<out ComponentVariant.DependencyConstraint>) {
            encoder.writeSmallInt(constraints.size)
            for (constraint in constraints) {
                componentSelectorSerializer.write(encoder, constraint.group!!, constraint.module!!, constraint.versionConstraint, constraint.attributes, mutableSetOf<CapabilitySelector>())
                encoder.writeNullableString(constraint.reason)
            }
        }

        @Throws(IOException::class)
        fun writeVariantDependencies(dependencies: MutableList<out ComponentVariant.Dependency>) {
            encoder.writeSmallInt(dependencies.size)
            for (dependency in dependencies) {
                componentSelectorSerializer.write(encoder, dependency.group!!, dependency.module!!, dependency.versionConstraint, dependency.attributes, dependency.capabilitySelectors)
                encoder.writeNullableString(dependency.reason)
                writeVariantDependencyExcludes(dependency.excludes)
                encoder.writeBoolean(dependency.isEndorsingStrictVersions)
                writeNullableArtifact(dependency.dependencyArtifact)
            }
        }

        @Throws(IOException::class)
        fun writeVariantDependencyExcludes(excludes: MutableList<ExcludeMetadata>) {
            writeCount(excludes.size)
            for (exclude in excludes) {
                writeString(exclude.moduleId.getGroup())
                writeString(exclude.moduleId.getName())
            }
        }

        @Throws(IOException::class)
        fun writeAttributes(attributes: AttributeContainer) {
            attributeContainerSerializer.write(encoder, attributes)
        }

        @Throws(IOException::class)
        fun writeVariantFiles(files: MutableList<out ComponentVariant.File>) {
            encoder.writeSmallInt(files.size)
            for (file in files) {
                encoder.writeString(file.name)
                encoder.writeString(file.uri)
            }
        }

        @Throws(IOException::class)
        fun writeVariantCapabilities(capabilities: ImmutableCapabilities) {
            val capabilitySet: ImmutableSet<ImmutableCapability> = capabilities.asSet()
            encoder.writeSmallInt(capabilitySet.size)
            for (capability in capabilitySet) {
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


        @Throws(IOException::class)
        fun write(metadata: IvyModuleResolveMetadata) {
            encoder.writeByte(TYPE_IVY)
            writeInfoSection(metadata)
            writeExtraInfo(metadata.extraAttributes)
            writeConfigurations(metadata.configurationDefinitions.values())
            writeIvyDependencies(metadata.dependencies)
            writeArtifacts(metadata.artifactDefinitions)
            writeExcludeRules(metadata.excludes)
            writeSharedInfo(metadata)
            writeNullableString(metadata.branch)
            writeVariants(metadata)
        }

        @Throws(IOException::class)
        fun writeSharedInfo(metadata: ModuleComponentResolveMetadata) {
            encoder.writeBoolean(metadata.isMissing)
            encoder.writeBoolean(metadata.isChanging)
            encoder.writeBoolean(metadata.isExternalVariant)
            encoder.writeString(metadata.status)
            writeStringList(metadata.statusScheme!!)
            moduleSourcesSerializer.write(encoder, metadata.sources)
        }

        @Throws(IOException::class)
        fun writeId(componentIdentifier: ModuleComponentIdentifier) {
            writeString(componentIdentifier.getGroup())
            writeString(componentIdentifier.getModule())
            writeString(componentIdentifier.getVersion())
        }

        @Throws(IOException::class)
        fun writeInfoSection(metadata: ModuleComponentResolveMetadata) {
            writeId(metadata.getId()!!)
            writeAttributes(metadata.attributes)
        }

        @Throws(IOException::class)
        fun writeExtraInfo(extraInfo: MutableMap<NamespaceId?, String?>) {
            writeCount(extraInfo.size)
            for (entry in extraInfo.entries) {
                val namespaceId: NamespaceId = entry.key!!
                writeString(namespaceId.namespace)
                writeString(namespaceId.name)
                writeString(entry.value)
            }
        }

        @Throws(IOException::class)
        fun writeConfigurations(configurations: MutableCollection<Configuration>) {
            writeCount(configurations.size)
            for (conf in configurations) {
                writeConfiguration(conf)
            }
        }

        @Throws(IOException::class)
        fun writeConfiguration(conf: Configuration) {
            writeString(conf.name)
            writeBoolean(conf.isTransitive)
            writeBoolean(conf.isVisible)
            writeStringList(conf.extendsFrom)
        }

        @Throws(IOException::class)
        fun writeArtifacts(artifacts: MutableList<Artifact>) {
            writeCount(artifacts.size)
            for (artifact in artifacts) {
                IvyArtifactNameSerializer.INSTANCE.write(encoder, artifact.artifactName)
                writeStringSet(artifact.configurations!!)
            }
        }

        @Throws(IOException::class)
        fun writeIvyDependencies(dependencies: MutableList<IvyDependencyDescriptor>) {
            writeCount(dependencies.size)
            for (dd in dependencies) {
                writeIvyDependency(dd)
            }
        }

        @Throws(IOException::class)
        fun writeMavenDependencies(dependencies: MutableList<MavenDependencyDescriptor>, deduplicationDependencyCache: MutableMap<ExternalDependencyDescriptor?, Int?>) {
            writeCount(dependencies.size)
            for (dd in dependencies) {
                writeMavenDependency(dd, deduplicationDependencyCache)
            }
        }

        @Throws(IOException::class)
        fun writeIvyDependency(ivyDependency: IvyDependencyDescriptor) {
            componentSelectorSerializer.write(encoder, ivyDependency.selector)
            writeDependencyConfigurationMapping(ivyDependency)
            writeArtifacts(ivyDependency.dependencyArtifacts)
            writeExcludeRules(ivyDependency.allExcludes)
            writeString(ivyDependency.dynamicConstraintVersion)
            writeBoolean(ivyDependency.isChanging)
            writeBoolean(ivyDependency.isTransitive)
            writeBoolean(ivyDependency.isOptional)
        }

        @Throws(IOException::class)
        fun writeDependencyConfigurationMapping(dep: IvyDependencyDescriptor) {
            val confMappings: SetMultimap<String?, String?> = dep.confMappings
            writeCount(confMappings.keySet().size)
            for (conf in confMappings.keySet()) {
                writeString(conf)
                writeStringSet(confMappings.get(conf))
            }
        }

        @Throws(IOException::class)
        fun writeExcludeRules(excludes: MutableList<Exclude>) {
            writeCount(excludes.size)
            for (exclude in excludes) {
                writeString(exclude.moduleId.getGroup())
                writeString(exclude.moduleId.getName())
                val artifact = exclude.artifact
                writeNullableArtifact(artifact)
                writeStringArray(exclude.configurations.toArray(arrayOfNulls<String>(0)))
                writeNullableString(exclude.matcher)
            }
        }

        @Throws(IOException::class)
        fun writeMavenDependency(mavenDependency: MavenDependencyDescriptor, deduplicationDependencyCache: MutableMap<ExternalDependencyDescriptor?, Int?>) {
            val nextMapping = deduplicationDependencyCache.size
            val mapping = deduplicationDependencyCache.putIfAbsent(mavenDependency, nextMapping)
            if (mapping != null) {
                // Save a reference to the dependency that was written before
                encoder.writeSmallInt(mapping)
            } else {
                encoder.writeSmallInt(nextMapping)
                componentSelectorSerializer.write(encoder, mavenDependency.selector)
                writeNullableArtifact(mavenDependency.dependencyArtifact)
                writeMavenExcludeRules(mavenDependency.allExcludes)
                encoder.writeSmallInt(mavenDependency.scope.ordinal)
                encoder.writeSmallInt(mavenDependency.type.ordinal)
            }
        }

        @Throws(IOException::class)
        fun writeNullableArtifact(artifact: IvyArtifactName?) {
            if (artifact == null) {
                writeBoolean(false)
            } else {
                writeBoolean(true)
                IvyArtifactNameSerializer.INSTANCE.write(encoder, artifact)
            }
        }

        @Throws(IOException::class)
        fun writeMavenExcludeRules(excludes: MutableList<ExcludeMetadata>) {
            writeCount(excludes.size)
            for (exclude in excludes) {
                writeString(exclude.moduleId.getGroup())
                writeString(exclude.moduleId.getName())
            }
        }

        @Throws(IOException::class)
        fun writeCount(i: Int) {
            encoder.writeSmallInt(i)
        }

        @Throws(IOException::class)
        fun writeString(str: String?) {
            encoder.writeString(str)
        }

        @Throws(IOException::class)
        fun writeNullableString(str: String?) {
            encoder.writeNullableString(str)
        }

        @Throws(IOException::class)
        fun writeBoolean(b: Boolean) {
            encoder.writeBoolean(b)
        }

        @Throws(IOException::class)
        fun writeStringArray(values: Array<String?>) {
            writeCount(values.size)
            for (configuration in values) {
                writeNullableString(configuration)
            }
        }

        @Throws(IOException::class)
        fun writeStringList(values: MutableList<String?>) {
            writeCount(values.size)
            for (configuration in values) {
                writeString(configuration)
            }
        }

        @Throws(IOException::class)
        fun writeStringSet(values: MutableSet<String?>) {
            writeCount(values.size)
            for (configuration in values) {
                writeString(configuration)
            }
        }
    }

    private class Reader(
        private val decoder: Decoder,
        moduleIdentifierFactory: ImmutableModuleIdentifierFactory,
        attributeContainerSerializer: AttributeContainerSerializer,
        componentSelectorSerializer: ModuleComponentSelectorSerializer, mavenMutableModuleMetadataFactory: MavenMutableModuleMetadataFactory,
        ivyMetadataFactory: IvyMutableModuleMetadataFactory,
        moduleSourcesSerializer: ModuleSourcesSerializer
    ) {
        private val moduleIdentifierFactory: ImmutableModuleIdentifierFactory?
        private val excludeRuleConverter: ExcludeRuleConverter
        private val attributeContainerSerializer: AttributeContainerSerializer
        private val componentSelectorSerializer: ModuleComponentSelectorSerializer
        private val mavenMetadataFactory: MavenMutableModuleMetadataFactory
        private val ivyMetadataFactory: IvyMutableModuleMetadataFactory
        private val moduleSourcesSerializer: ModuleSourcesSerializer
        private var id: ModuleComponentIdentifier? = null
        private var attributes: ImmutableAttributes? = null

        init {
            this.moduleIdentifierFactory = moduleIdentifierFactory
            this.excludeRuleConverter = DefaultExcludeRuleConverter(moduleIdentifierFactory)
            this.attributeContainerSerializer = attributeContainerSerializer
            this.componentSelectorSerializer = componentSelectorSerializer
            this.mavenMetadataFactory = mavenMutableModuleMetadataFactory
            this.ivyMetadataFactory = ivyMetadataFactory
            this.moduleSourcesSerializer = moduleSourcesSerializer
        }

        @Throws(IOException::class)
        fun read(deduplicationDependencyCache: MutableMap<Int?, MavenDependencyDescriptor>): MutableModuleComponentResolveMetadata {
            val type = decoder.readByte()
            when (type) {
                TYPE_IVY -> return readIvy()
                TYPE_MAVEN -> return readMaven(deduplicationDependencyCache)
                else -> throw IllegalArgumentException("Unexpected metadata type found.")
            }
        }

        @Throws(IOException::class)
        fun readSharedInfo(metadata: MutableModuleComponentResolveMetadata) {
            metadata.isMissing = decoder.readBoolean()
            metadata.isChanging = decoder.readBoolean()
            metadata.isExternalVariant = decoder.readBoolean()
            metadata.status = decoder.readString()
            metadata.statusScheme = readStringList()
            metadata.sources = moduleSourcesSerializer.read(decoder)
        }

        @Throws(IOException::class)
        fun readMaven(deduplicationDependencyCache: MutableMap<Int?, MavenDependencyDescriptor>): MutableModuleComponentResolveMetadata {
            readInfoSection()
            val snapshotTimestamp = readNullableString()
            if (snapshotTimestamp != null) {
                id = MavenUniqueSnapshotComponentIdentifier(id!!, snapshotTimestamp)
            }

            val dependencies = readMavenDependencies(deduplicationDependencyCache)
            val metadata = mavenMetadataFactory.create(id!!, dependencies)
            readSharedInfo(metadata)
            metadata.snapshotTimestamp = snapshotTimestamp
            // NOTE: this looks nullable, but only non-null Strings are written
            metadata.packaging = readNullableString()!!
            metadata.isRelocated = readBoolean()
            metadata.attributes = attributes
            readVariants(metadata)
            return metadata
        }

        @Throws(IOException::class)
        fun readVariants(metadata: MutableModuleComponentResolveMetadata) {
            val count = decoder.readSmallInt()
            for (i in 0..<count) {
                val name = decoder.readString()
                val attributes = readAttributes()
                val variant = metadata.addVariant(name!!, attributes)
                readVariantDependencies(variant!!)
                readVariantConstraints(variant)
                readVariantFiles(variant)
                readVariantCapabilities(variant)
                val externalVariant = decoder.readBoolean()
                variant.isAvailableExternally = externalVariant
            }
        }

        @Throws(IOException::class)
        fun readAttributes(): ImmutableAttributes {
            return attributeContainerSerializer.read(decoder)!!
        }

        @Throws(IOException::class)
        fun readVariantDependencies(variant: MutableComponentVariant) {
            val count = decoder.readSmallInt()
            for (i in 0..<count) {
                val selector = componentSelectorSerializer.read(decoder)
                val reason = decoder.readNullableString()
                val excludes: ImmutableList<ExcludeMetadata?> = readVariantDependencyExcludes()
                val endorsing = decoder.readBoolean()
                val dependencyArtifact = IvyArtifactNameSerializer.INSTANCE.readNullable(decoder)
                variant.addDependency(
                    selector.getGroup(),
                    selector.getModule(),
                    selector.getVersionConstraint(),
                    excludes,
                    reason!!,
                    selector.getAttributes() as ImmutableAttributes,
                    selector.getCapabilitySelectors(),
                    endorsing,
                    dependencyArtifact
                )
            }
        }

        @Throws(IOException::class)
        fun readVariantConstraints(variant: MutableComponentVariant) {
            val count = decoder.readSmallInt()
            for (i in 0..<count) {
                val selector = componentSelectorSerializer.read(decoder)
                val reason = decoder.readNullableString()
                variant.addDependencyConstraint(selector.getGroup(), selector.getModule(), selector.getVersionConstraint(), reason!!, selector.getAttributes() as ImmutableAttributes)
            }
        }

        @Throws(IOException::class)
        fun readVariantDependencyExcludes(): ImmutableList<ExcludeMetadata?> {
            val builder = ImmutableList.Builder<ExcludeMetadata?>()
            val len = readCount()
            for (i in 0..<len) {
                val group = readString()
                val module = readString()
                builder.add(excludeRuleConverter.createExcludeRule(group, module))
            }
            return builder.build()
        }

        @Throws(IOException::class)
        fun readVariantFiles(variant: MutableComponentVariant) {
            val count = decoder.readSmallInt()
            for (i in 0..<count) {
                variant.addFile(decoder.readString()!!, decoder.readString()!!)
            }
        }

        @Throws(IOException::class)
        fun readVariantCapabilities(variant: MutableComponentVariant) {
            val capabilitiesCount = decoder.readSmallInt()
            for (j in 0..<capabilitiesCount) {
                val appendix = decoder.readNullableString()
                var capability: CapabilityInternal = DefaultImmutableCapability(decoder.readString()!!, decoder.readString()!!, decoder.readString())
                if (appendix != null) {
                    capability = ShadowedImmutableCapability(capability, appendix)
                }
                variant.addCapability(capability)
            }
        }

        @Throws(IOException::class)
        fun readIvy(): MutableModuleComponentResolveMetadata {
            readInfoSection()
            val extraAttributes = readExtraInfo()
            val configurations = readConfigurations()
            val dependencies = readIvyDependencies()
            val artifacts = readArtifacts()
            val excludes = readModuleExcludes()
            val metadata = ivyMetadataFactory.create(id!!, dependencies, configurations, artifacts, excludes)
            readSharedInfo(metadata)
            val branch = readNullableString()
            metadata.branch = branch
            metadata.extraAttributes = extraAttributes
            metadata.attributes = attributes
            readVariants(metadata)
            return metadata
        }

        @Throws(IOException::class)
        fun readInfoSection() {
            id = readId()
            attributes = readAttributes()
        }

        @Throws(IOException::class)
        fun readId(): ModuleComponentIdentifier {
            return newId(DefaultModuleIdentifier.newId(readString(), readString()), readString())
        }

        @Throws(IOException::class)
        fun readExtraInfo(): MutableMap<NamespaceId?, String?> {
            val len = readCount()
            val result: MutableMap<NamespaceId?, String?> = LinkedHashMap<NamespaceId?, String?>(len)
            for (i in 0..<len) {
                val namespaceId = NamespaceId(readString(), readString())
                val value = readString()
                result.put(namespaceId, value)
            }
            return result
        }

        @Throws(IOException::class)
        fun readConfigurations(): MutableList<Configuration?> {
            val len = readCount()
            val configurations: MutableList<Configuration?> = ArrayList<Configuration?>(len)
            for (i in 0..<len) {
                val configuration = readConfiguration()
                configurations.add(configuration)
            }
            return configurations
        }

        @Throws(IOException::class)
        fun readConfiguration(): Configuration {
            val name = readString()
            val transitive = readBoolean()
            val visible = readBoolean()
            val extendsFrom = readStringList()
            return Configuration(name, transitive, visible, extendsFrom)
        }

        @Throws(IOException::class)
        fun readArtifacts(): MutableList<Artifact?> {
            val size = readCount()
            val result: MutableList<Artifact?> = ArrayList<Artifact?>(size)
            for (i in 0..<size) {
                val ivyArtifactName = IvyArtifactNameSerializer.INSTANCE.read(decoder)
                result.add(Artifact(ivyArtifactName, readStringSet()))
            }
            return result
        }

        @Throws(IOException::class)
        fun readIvyDependencies(): MutableList<IvyDependencyDescriptor?> {
            val len = readCount()
            val result: MutableList<IvyDependencyDescriptor?> = ArrayList<IvyDependencyDescriptor?>(len)
            for (i in 0..<len) {
                result.add(readIvyDependency())
            }
            return result
        }

        @Throws(IOException::class)
        fun readIvyDependency(): IvyDependencyDescriptor {
            val requested = componentSelectorSerializer.read(decoder)
            val configMappings = readDependencyConfigurationMapping()
            val artifacts = readDependencyArtifactDescriptors()
            val excludes = readDependencyExcludes()
            val dynamicConstraintVersion = readString()
            val changing = readBoolean()
            val transitive = readBoolean()
            val optional = readBoolean()
            return IvyDependencyDescriptor(requested, dynamicConstraintVersion, changing, transitive, optional, configMappings, artifacts, excludes)
        }

        @Throws(IOException::class)
        fun readDependencyConfigurationMapping(): SetMultimap<String?, String?> {
            val size = readCount()
            val result: SetMultimap<String?, String?> = LinkedHashMultimap.create<String?, String?>()
            for (i in 0..<size) {
                val from = readString()
                val to = readStringSet()
                result.putAll(from, to)
            }
            return result
        }

        @Throws(IOException::class)
        fun readDependencyArtifactDescriptors(): MutableList<Artifact?> {
            val size = readCount()
            val result: MutableList<Artifact?> = ArrayList<Artifact?>(size)
            for (i in 0..<size) {
                val ivyArtifactName = IvyArtifactNameSerializer.INSTANCE.read(decoder)
                result.add(Artifact(ivyArtifactName, readStringSet()))
            }
            return result
        }

        @Throws(IOException::class)
        fun readDependencyExcludes(): MutableList<Exclude?> {
            val len = readCount()
            val result: MutableList<Exclude?> = ArrayList<Exclude?>(len)
            for (i in 0..<len) {
                val rule = readExcludeRule()
                result.add(rule)
            }
            return result
        }

        @Throws(IOException::class)
        fun readModuleExcludes(): MutableList<Exclude?> {
            val len = readCount()
            val result: MutableList<Exclude?> = ArrayList<Exclude?>(len)
            for (i in 0..<len) {
                result.add(readExcludeRule())
            }
            return result
        }

        @Throws(IOException::class)
        fun readExcludeRule(): DefaultExclude {
            val moduleOrg = readString()
            val moduleName = readString()
            val artifactName = IvyArtifactNameSerializer.INSTANCE.readNullable(decoder)
            val confs = readStringArray()
            val matcher = readNullableString()
            return DefaultExclude(moduleIdentifierFactory!!.module(moduleOrg, moduleName)!!, artifactName, confs, matcher)
        }

        @Throws(IOException::class)
        fun readMavenDependencies(deduplicationDependencyCache: MutableMap<Int?, MavenDependencyDescriptor>): MutableList<MavenDependencyDescriptor?> {
            val len = readCount()
            val result: MutableList<MavenDependencyDescriptor?> = ArrayList<MavenDependencyDescriptor?>(len)
            for (i in 0..<len) {
                result.add(readMavenDependency(deduplicationDependencyCache))
            }
            return result
        }

        @Throws(IOException::class)
        fun readMavenDependency(deduplicationDependencyCache: MutableMap<Int?, MavenDependencyDescriptor>): MavenDependencyDescriptor {
            val mapping = decoder.readSmallInt()
            if (mapping == deduplicationDependencyCache.size) {
                val requested = componentSelectorSerializer.read(decoder)
                val artifactName = IvyArtifactNameSerializer.INSTANCE.readNullable(decoder)
                val mavenExcludes = readMavenDependencyExcludes()
                val scope = MavenScope.values()[decoder.readSmallInt()]
                val type = MavenDependencyType.values()[decoder.readSmallInt()]
                val mavenDependencyDescriptor = MavenDependencyDescriptor(scope, type, requested, artifactName, mavenExcludes)
                deduplicationDependencyCache.put(mapping, mavenDependencyDescriptor)
                return mavenDependencyDescriptor
            } else {
                val mavenDependencyDescriptor = checkNotNull(deduplicationDependencyCache.get(mapping))
                return mavenDependencyDescriptor
            }
        }

        @Throws(IOException::class)
        fun readMavenDependencyExcludes(): MutableList<ExcludeMetadata?> {
            val len = readCount()
            val result: MutableList<ExcludeMetadata?> = ArrayList<ExcludeMetadata?>(len)
            for (i in 0..<len) {
                val moduleOrg = readString()
                val moduleName = readString()
                val rule = DefaultExclude(moduleIdentifierFactory!!.module(moduleOrg, moduleName)!!)
                result.add(rule)
            }
            return result
        }

        @Throws(IOException::class)
        fun readCount(): Int {
            return decoder.readSmallInt()
        }

        @Throws(IOException::class)
        fun readString(): String {
            return decoder.readString()!!
        }

        @Throws(IOException::class)
        fun readNullableString(): String? {
            return decoder.readNullableString()
        }

        @Throws(IOException::class)
        fun readBoolean(): Boolean {
            return decoder.readBoolean()
        }

        @Throws(IOException::class)
        fun readStringArray(): Array<String?> {
            val size = readCount()
            val array = arrayOfNulls<String>(size)
            for (i in 0..<size) {
                array[i] = readNullableString()
            }
            return array
        }

        @Throws(IOException::class)
        fun readStringList(): MutableList<String?> {
            val size = readCount()
            val builder = ImmutableList.builderWithExpectedSize<String?>(size)
            for (i in 0..<size) {
                builder.add(readString())
            }
            return builder.build()
        }

        @Throws(IOException::class)
        fun readStringSet(): MutableSet<String?> {
            val size = readCount()
            val builder = ImmutableSet.builderWithExpectedSize<String?>(size)
            for (i in 0..<size) {
                builder.add(readString())
            }
            return builder.build()
        }
    }

    companion object {
        private const val TYPE_IVY: Byte = 1
        private const val TYPE_MAVEN: Byte = 2
    }
}
