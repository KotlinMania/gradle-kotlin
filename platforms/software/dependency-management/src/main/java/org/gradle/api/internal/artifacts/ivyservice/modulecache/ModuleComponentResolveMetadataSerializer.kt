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
package org.gradle.api.internal.artifacts.ivyservice.modulecache

import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.capability.CapabilitySelectorSerializer
import org.gradle.internal.component.external.model.AbstractLazyModuleComponentResolveMetadata
import org.gradle.internal.component.external.model.AbstractRealisedModuleComponentResolveMetadata
import org.gradle.internal.component.external.model.DefaultVirtualModuleComponentIdentifier
import org.gradle.internal.component.external.model.ExternalDependencyDescriptor
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata
import org.gradle.internal.component.external.model.VirtualComponentIdentifier
import org.gradle.internal.component.external.model.ivy.DefaultIvyModuleResolveMetadata
import org.gradle.internal.component.external.model.ivy.RealisedIvyModuleResolveMetadata
import org.gradle.internal.component.external.model.ivy.RealisedIvyModuleResolveMetadataSerializationHelper
import org.gradle.internal.component.external.model.maven.DefaultMavenModuleResolveMetadata
import org.gradle.internal.component.external.model.maven.MavenDependencyDescriptor
import org.gradle.internal.component.external.model.maven.RealisedMavenModuleResolveMetadata
import org.gradle.internal.component.external.model.maven.RealisedMavenModuleResolveMetadataSerializationHelper
import org.gradle.internal.resolve.caching.DesugaringAttributeContainerSerializer
import org.gradle.internal.serialize.AbstractSerializer
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import java.io.EOFException
import java.io.IOException

/**
 * Serializer for [ModuleComponentResolveMetadata].
 *
 * This serializer will first transform any [lazy][AbstractLazyModuleComponentResolveMetadata] metadata
 * in the [realised][AbstractRealisedModuleComponentResolveMetadata] version so that the complete state can be serialized.
 */
@ServiceScope(Scope.Build::class)
class ModuleComponentResolveMetadataSerializer(
    private val delegate: ModuleMetadataSerializer,
    attributeContainerSerializer: DesugaringAttributeContainerSerializer,
    capabilitySelectorSerializer: CapabilitySelectorSerializer,
    moduleIdentifierFactory: ImmutableModuleIdentifierFactory
) : AbstractSerializer<ModuleComponentResolveMetadata?>() {
    private val ivySerializationHelper: RealisedIvyModuleResolveMetadataSerializationHelper
    private val mavenSerializationHelper: RealisedMavenModuleResolveMetadataSerializationHelper
    private val moduleIdentifierFactory: ImmutableModuleIdentifierFactory?

    init {
        this.moduleIdentifierFactory = moduleIdentifierFactory
        ivySerializationHelper = RealisedIvyModuleResolveMetadataSerializationHelper(attributeContainerSerializer, capabilitySelectorSerializer, moduleIdentifierFactory)
        mavenSerializationHelper = RealisedMavenModuleResolveMetadataSerializationHelper(attributeContainerSerializer, capabilitySelectorSerializer, moduleIdentifierFactory)
    }

    @Throws(EOFException::class, Exception::class)
    override fun read(decoder: Decoder): ModuleComponentResolveMetadata? {
        val deduplicationDependencyCache: MutableMap<Int?, MavenDependencyDescriptor?> = HashMap<Int?, MavenDependencyDescriptor?>()
        val mutable = delegate.read(decoder, moduleIdentifierFactory, deduplicationDependencyCache)
        readPlatformOwners(decoder, mutable)
        val resolveMetadata = mutable.asImmutable() as AbstractLazyModuleComponentResolveMetadata?

        if (resolveMetadata is DefaultIvyModuleResolveMetadata) {
            return ivySerializationHelper.readMetadata(decoder, resolveMetadata)
        } else if (resolveMetadata is DefaultMavenModuleResolveMetadata) {
            return mavenSerializationHelper.readMetadata(decoder, resolveMetadata, deduplicationDependencyCache)
        } else {
            throw IllegalStateException("Unknown resolved metadata type: " + resolveMetadata!!.javaClass)
        }
    }

    @Throws(IOException::class)
    private fun readPlatformOwners(decoder: Decoder, mutable: MutableModuleComponentResolveMetadata) {
        val len = decoder.readSmallInt()
        if (len > 0) {
            for (i in 0..<len) {
                val moduleComponentIdentifier = readModuleIdentifier(decoder)
                mutable.belongsTo(moduleComponentIdentifier)
            }
        }
    }

    @Throws(IOException::class)
    private fun readModuleIdentifier(decoder: Decoder): VirtualComponentIdentifier {
        val group = decoder.readString()
        val module = decoder.readString()
        val version = decoder.readString()
        val moduleIdentifier = DefaultModuleIdentifier.newId(group, module!!)
        return DefaultVirtualModuleComponentIdentifier(moduleIdentifier, version!!)
    }

    @Throws(Exception::class)
    override fun write(encoder: Encoder, value: ModuleComponentResolveMetadata) {
        val transformed = assertRealized(value)
        val deduplicationDependencyCache = HashMap<ExternalDependencyDescriptor?, Int?>()
        delegate.write(encoder, transformed, deduplicationDependencyCache)
        writeOwners(encoder, value.platformOwners)
        if (transformed is RealisedIvyModuleResolveMetadata) {
            ivySerializationHelper.writeRealisedVariantsData(encoder, transformed)
            ivySerializationHelper.writeRealisedConfigurationsData(encoder, transformed, deduplicationDependencyCache)
        } else if (transformed is RealisedMavenModuleResolveMetadata) {
            mavenSerializationHelper.writeRealisedVariantsData(encoder, transformed)
            mavenSerializationHelper.writeRealisedConfigurationsData(encoder, transformed, deduplicationDependencyCache)
        } else {
            throw IllegalStateException("Unexpected realised module component resolve metadata type: " + transformed.javaClass)
        }
    }

    @Throws(IOException::class)
    private fun writeOwners(encoder: Encoder, platformOwners: ImmutableList<out VirtualComponentIdentifier?>) {
        encoder.writeSmallInt(platformOwners.size)
        for (platformOwner in platformOwners) {
            writeComponentIdentifier(encoder, (platformOwner as org.gradle.api.artifacts.component.ModuleComponentIdentifier?)!!)
        }
    }

    @Throws(IOException::class)
    private fun writeComponentIdentifier(encoder: Encoder, platformOwner: ModuleComponentIdentifier) {
        encoder.writeString(platformOwner.getGroup())
        encoder.writeString(platformOwner.getModule())
        encoder.writeString(platformOwner.getVersion())
    }

    private fun assertRealized(metadata: ModuleComponentResolveMetadata): AbstractRealisedModuleComponentResolveMetadata {
        if (metadata is AbstractRealisedModuleComponentResolveMetadata) {
            return metadata
        }
        throw IllegalStateException("The type of metadata received is not supported - " + metadata.javaClass.getName())
    }
}
