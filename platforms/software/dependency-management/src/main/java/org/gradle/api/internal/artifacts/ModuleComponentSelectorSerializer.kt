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
package org.gradle.api.internal.artifacts

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.artifacts.capability.CapabilitySelector
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.artifacts.capability.CapabilitySelectorSerializer
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.AttributeContainerSerializer
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.Serializer
import java.io.IOException

class ModuleComponentSelectorSerializer(
    private val attributeContainerSerializer: AttributeContainerSerializer,
    private val capabilitySelectorSerializer: CapabilitySelectorSerializer
) : Serializer<ModuleComponentSelector?> {
    @Throws(IOException::class)
    override fun read(decoder: Decoder): ModuleComponentSelector {
        val group = decoder.readString()
        val name = decoder.readString()
        val versionConstraint = readVersionConstraint(decoder)
        val attributes = readAttributes(decoder)
        val capabilitySelectors = readCapabilitySelectors(decoder)
        return newSelector(DefaultModuleIdentifier.newId(group, name!!), versionConstraint, attributes, capabilitySelectors)
    }

    @Throws(IOException::class)
    fun readVersionConstraint(decoder: Decoder): VersionConstraint {
        val required = decoder.readString()
        val preferred = decoder.readString()
        val strictly = decoder.readString()
        val cpt = decoder.readSmallInt()
        val rejects = ImmutableList.builderWithExpectedSize<String>(cpt)
        for (i in 0..<cpt) {
            rejects.add(decoder.readString()!!)
        }
        val branch = decoder.readNullableString()
        return DefaultImmutableVersionConstraint(preferred!!, required!!, strictly!!, rejects.build(), branch)
    }

    @Throws(IOException::class)
    override fun write(encoder: Encoder, value: ModuleComponentSelector) {
        encoder.writeString(value.getGroup())
        encoder.writeString(value.getModule())
        writeVersionConstraint(encoder, value.getVersionConstraint())
        writeAttributes(encoder, (value.getAttributes() as AttributeContainerInternal).asImmutable())
        writeCapabilitySelectors(encoder, value.getCapabilitySelectors())
    }

    @Throws(IOException::class)
    fun write(encoder: Encoder, group: String, module: String, version: VersionConstraint, attributes: ImmutableAttributes, capabilitySelectors: MutableSet<CapabilitySelector>) {
        encoder.writeString(group)
        encoder.writeString(module)
        writeVersionConstraint(encoder, version)
        writeAttributes(encoder, attributes)
        writeCapabilitySelectors(encoder, capabilitySelectors)
    }

    @Throws(IOException::class)
    fun writeVersionConstraint(encoder: Encoder, cst: VersionConstraint) {
        encoder.writeString(cst.getRequiredVersion())
        encoder.writeString(cst.getPreferredVersion())
        encoder.writeString(cst.getStrictVersion())
        val rejectedVersions = cst.getRejectedVersions()
        encoder.writeSmallInt(rejectedVersions.size)
        for (rejectedVersion in rejectedVersions) {
            encoder.writeString(rejectedVersion)
        }
        encoder.writeNullableString(cst.getBranch())
    }

    @Throws(IOException::class)
    private fun readAttributes(decoder: Decoder): ImmutableAttributes {
        return attributeContainerSerializer.read(decoder)
    }

    @Throws(IOException::class)
    private fun writeAttributes(encoder: Encoder, attributes: ImmutableAttributes) {
        attributeContainerSerializer.write(encoder, attributes)
    }

    @Throws(IOException::class)
    private fun readCapabilitySelectors(decoder: Decoder): ImmutableSet<CapabilitySelector> {
        val size = decoder.readSmallInt()
        if (size == 0) {
            return ImmutableSet.of<CapabilitySelector>()
        }
        val builder = ImmutableSet.builderWithExpectedSize<CapabilitySelector>(size)
        for (i in 0..<size) {
            builder.add(capabilitySelectorSerializer.read(decoder))
        }
        return builder.build()
    }

    @Throws(IOException::class)
    private fun writeCapabilitySelectors(encoder: Encoder, capabilitySelectors: MutableSet<CapabilitySelector>) {
        encoder.writeSmallInt(capabilitySelectors.size)
        for (capabilitySelector in capabilitySelectors) {
            capabilitySelectorSerializer.write(encoder, capabilitySelector)
        }
    }
}
