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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.result

import com.google.common.collect.ImmutableSet
import org.gradle.api.artifacts.capability.CapabilitySelector
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.LibraryComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.artifacts.ModuleComponentSelectorSerializer
import org.gradle.api.internal.artifacts.capability.CapabilitySelectorSerializer
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.component.local.model.DefaultLibraryComponentSelector
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector
import org.gradle.internal.serialize.AbstractSerializer
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import java.io.IOException
import javax.annotation.concurrent.NotThreadSafe

/**
 * A thread-safe and reusable serializer for [ComponentSelector]s.
 */
@NotThreadSafe
class ComponentSelectorSerializer(private val attributeContainerSerializer: AttributeContainerSerializer, private val capabilitySelectorSerializer: CapabilitySelectorSerializer) :
    AbstractSerializer<ComponentSelector?>() {
    private val projectIdentitySerializer: ProjectIdentitySerializer
    private val moduleComponentSelectorSerializer: ModuleComponentSelectorSerializer

    init {
        this.projectIdentitySerializer = ProjectIdentitySerializer(PathSerializer())
        this.moduleComponentSelectorSerializer = ModuleComponentSelectorSerializer(attributeContainerSerializer, capabilitySelectorSerializer)
    }

    @Throws(IOException::class)
    override fun read(decoder: Decoder): ComponentSelector {
        val id = decoder.readByte()

        if (Implementation.PROJECT.id == id) {
            val projectId = projectIdentitySerializer.read(decoder)
            val attributes = readAttributes(decoder)
            val capabilitySelectors = readCapabilitySelectors(decoder)
            return DefaultProjectComponentSelector(projectId, attributes, capabilitySelectors)
        } else if (Implementation.MODULE.id == id) {
            return moduleComponentSelectorSerializer.read(decoder)
        } else if (Implementation.LIBRARY.id == id) {
            return DefaultLibraryComponentSelector(decoder.readString()!!, decoder.readNullableString()!!, decoder.readNullableString()!!)
        }

        throw IllegalArgumentException("Unable to find component selector with id: " + id)
    }

    @Throws(IOException::class)
    private fun readAttributes(decoder: Decoder): ImmutableAttributes {
        return attributeContainerSerializer.read(decoder)
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

    @Throws(IOException::class)
    override fun write(encoder: Encoder, value: ComponentSelector) {
        requireNotNull(value) { "Provided component selector may not be null" }

        val implementation: Implementation = resolveImplementation(value)

        encoder.writeByte(implementation.id)

        if (implementation == Implementation.MODULE) {
            moduleComponentSelectorSerializer.write(encoder, value as ModuleComponentSelector)
        } else if (implementation == Implementation.PROJECT) {
            val projectComponentSelector = value as DefaultProjectComponentSelector
            projectIdentitySerializer.write(encoder, projectComponentSelector.getProjectIdentity())
            writeAttributes(encoder, projectComponentSelector.getAttributes())
            writeCapabilitySelectors(encoder, projectComponentSelector.getCapabilitySelectors())
        } else if (implementation == Implementation.LIBRARY) {
            val libraryComponentSelector = value as LibraryComponentSelector
            encoder.writeString(libraryComponentSelector.getProjectPath())
            encoder.writeNullableString(libraryComponentSelector.getLibraryName())
            encoder.writeNullableString(libraryComponentSelector.getVariant())
        } else {
            throw IllegalStateException("Unsupported implementation type: " + implementation)
        }
    }

    @Throws(IOException::class)
    private fun writeAttributes(encoder: Encoder, attributes: ImmutableAttributes) {
        attributeContainerSerializer.write(encoder, attributes)
    }

    private enum class Implementation(id: Int) {
        MODULE(1), PROJECT(2), LIBRARY(6);

        val id: Byte

        init {
            this.id = id.toByte()
        }
    }

    companion object {
        private fun resolveImplementation(value: ComponentSelector): Implementation {
            if (value is DefaultModuleComponentSelector) {
                return Implementation.MODULE
            } else if (value is DefaultProjectComponentSelector) {
                return Implementation.PROJECT
            } else if (value is DefaultLibraryComponentSelector) {
                return Implementation.LIBRARY
            } else {
                throw IllegalArgumentException("Unsupported component selector class: " + value.javaClass)
            }
        }
    }
}
