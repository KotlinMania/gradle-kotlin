/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.api.internal.artifacts.capability

import org.gradle.api.artifacts.capability.CapabilitySelector
import org.gradle.internal.component.external.model.DefaultImmutableCapability
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.Serializer
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import java.io.IOException

@ServiceScope(Scope.BuildTree::class)
class CapabilitySelectorSerializer : Serializer<CapabilitySelector?> {
    @Throws(IOException::class)
    override fun read(decoder: Decoder): CapabilitySelector {
        val type = decoder.readSmallInt()
        when (type) {
            SPECIFIC_CAPABILITY_SELECTOR -> return readSpecificCapabilitySelector(decoder)
            FEATURE_CAPABILITY_SELECTOR -> return readFeatureCapabilitySelector(decoder)
            else -> throw IllegalArgumentException("Unknown capability selector type: " + type)
        }
    }

    @Throws(IOException::class)
    override fun write(encoder: Encoder, value: CapabilitySelector) {
        if (value is SpecificCapabilitySelector) {
            encoder.writeSmallInt(SPECIFIC_CAPABILITY_SELECTOR)
            writeSpecificCapabilitySelector(encoder, value as DefaultSpecificCapabilitySelector)
        } else if (value is FeatureCapabilitySelector) {
            encoder.writeSmallInt(FEATURE_CAPABILITY_SELECTOR)
            writeFeatureCapabilitySelector(encoder, value)
        } else {
            throw IllegalArgumentException("Unknown capability selector type: " + value.javaClass)
        }
    }

    companion object {
        private const val SPECIFIC_CAPABILITY_SELECTOR = 1
        private const val FEATURE_CAPABILITY_SELECTOR = 2

        @Throws(IOException::class)
        private fun readSpecificCapabilitySelector(decoder: Decoder): CapabilitySelector {
            val group = decoder.readString()
            val name = decoder.readString()
            val version = decoder.readNullableString()
            return DefaultSpecificCapabilitySelector(DefaultImmutableCapability(group!!, name!!, version))
        }

        @Throws(IOException::class)
        private fun readFeatureCapabilitySelector(decoder: Decoder): CapabilitySelector {
            val feature = decoder.readString()
            return DefaultFeatureCapabilitySelector(feature)
        }

        @Suppress("deprecation")
        @Throws(IOException::class)
        private fun writeSpecificCapabilitySelector(encoder: Encoder, value: DefaultSpecificCapabilitySelector) {
            encoder.writeString(value.getGroup())
            encoder.writeString(value.getName())
            encoder.writeNullableString(value.getBackingCapability().getVersion())
        }

        @Throws(IOException::class)
        private fun writeFeatureCapabilitySelector(encoder: Encoder, value: FeatureCapabilitySelector) {
            encoder.writeString(value.getFeatureName())
        }
    }
}
