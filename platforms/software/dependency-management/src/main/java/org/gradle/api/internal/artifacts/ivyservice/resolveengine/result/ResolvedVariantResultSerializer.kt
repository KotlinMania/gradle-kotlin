/*
 * Copyright 2019 the original author or authors.
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

import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.result.ResolvedVariantResult
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.capabilities.Capability
import org.gradle.api.internal.artifacts.result.DefaultResolvedVariantResult
import org.gradle.internal.Describables
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.ListSerializer
import org.gradle.internal.serialize.Serializer
import javax.annotation.concurrent.NotThreadSafe

/**
 * A serializer for [ResolvedVariantResult] that is not thread safe and not reusable.
 */
@NotThreadSafe
class ResolvedVariantResultSerializer(private val componentIdentifierSerializer: ComponentIdentifierSerializer, private val attributeContainerSerializer: AttributeContainerSerializer) :
    Serializer<ResolvedVariantResult?> {
    private val written: MutableMap<ResolvedVariantResult, Int> = HashMap<ResolvedVariantResult, Int>()
    private val read: MutableList<ResolvedVariantResult> = ArrayList<ResolvedVariantResult>()

    private val capabilitySerializer: ListSerializer<Capability?>

    init {
        this.capabilitySerializer = ListSerializer<Capability?>(CapabilitySerializer())
    }

    @Throws(Exception::class)
    override fun read(decoder: Decoder): ResolvedVariantResult {
        val index = decoder.readSmallInt()
        if (index == -1) {
            return null
        }
        if (index == read.size) {
            val owner = componentIdentifierSerializer.read(decoder)
            val variantName = decoder.readString()
            val attributes: AttributeContainer = attributeContainerSerializer.read(decoder)
            val capabilities: ImmutableList<Capability> = capabilitySerializer.read(decoder)
            read.add(null)
            val externalVariant = read(decoder)
            val result = DefaultResolvedVariantResult(owner, Describables.of(variantName!!), attributes, capabilities, externalVariant)
            this.read.set(index, result)
            return result
        }
        return read.get(index)
    }

    @Throws(Exception::class)
    override fun write(encoder: Encoder, variant: ResolvedVariantResult) {
        if (variant == null) {
            encoder.writeSmallInt(-1)
            return
        }
        var index = written.get(variant)
        if (index == null) {
            index = written.size
            written.put(variant, index)
            encoder.writeSmallInt(index)
            componentIdentifierSerializer.write(encoder, variant.getOwner())
            encoder.writeString(variant.getDisplayName())
            attributeContainerSerializer.write(encoder, variant.getAttributes())
            capabilitySerializer.write(encoder, variant.getCapabilities())
            write(encoder, variant.getExternalVariant().orElse(null))
        } else {
            encoder.writeSmallInt(index)
        }
    }

    fun reset() {
        written.clear()
        read.clear()
    }
}
