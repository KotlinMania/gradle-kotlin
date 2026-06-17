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

import com.google.common.collect.ImmutableList
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.Serializer
import java.io.IOException

/**
 * A thread-safe and reusable serializer for [ComponentSelectionReason] if and only if the passed in
 * [ComponentSelectionDescriptorFactory] is thread-safe and reusable.
 */
class ComponentSelectionReasonSerializer(componentSelectionDescriptorFactory: ComponentSelectionDescriptorFactory) : Serializer<ComponentSelectionReasonInternal?> {
    private val componentSelectionDescriptorSerializer: ComponentSelectionDescriptorSerializer

    init {
        this.componentSelectionDescriptorSerializer = ComponentSelectionDescriptorSerializer(componentSelectionDescriptorFactory)
    }

    @Throws(IOException::class)
    override fun read(decoder: Decoder): ComponentSelectionReasonInternal {
        val descriptions = readDescriptions(decoder)
        return ComponentSelectionReasons.DefaultComponentSelectionReason(descriptions)
    }

    @Throws(IOException::class)
    private fun readDescriptions(decoder: Decoder): ImmutableList<ComponentSelectionDescriptorInternal> {
        val size = decoder.readSmallInt()
        val builder = ImmutableList.builderWithExpectedSize<ComponentSelectionDescriptorInternal>(size)
        for (i in 0..<size) {
            builder.add(componentSelectionDescriptorSerializer.read(decoder))
        }
        return builder.build()
    }

    @Throws(IOException::class)
    override fun write(encoder: Encoder, value: ComponentSelectionReasonInternal) {
        val descriptions = value.getDescriptions()
        encoder.writeSmallInt(descriptions.size)
        for (description in descriptions) {
            componentSelectionDescriptorSerializer.write(encoder, description)
        }
    }
}
