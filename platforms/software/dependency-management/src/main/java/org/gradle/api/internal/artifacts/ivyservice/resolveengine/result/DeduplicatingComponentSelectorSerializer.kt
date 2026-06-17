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

import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.Serializer
import javax.annotation.concurrent.NotThreadSafe

/**
 * A serializer for [ComponentSelector] that deduplicates the values and delegates to
 * another serializer for the actual serialization.
 *
 *
 * This serializer is not thread-safe and should not be reused to serialize multiple graphs.
 */
@NotThreadSafe
class DeduplicatingComponentSelectorSerializer(private val delegate: ComponentSelectorSerializer) : Serializer<ComponentSelector?> {
    private val writeIndex: MutableMap<ComponentSelector, Int> = HashMap<ComponentSelector, Int>()
    private val readIndex: MutableList<ComponentSelector> = ArrayList<ComponentSelector>()

    @Throws(Exception::class)
    override fun read(decoder: Decoder): ComponentSelector {
        val idx = decoder.readSmallInt()
        val selector: ComponentSelector
        if (idx == readIndex.size) {
            // new entry
            selector = delegate.read(decoder)
            readIndex.add(selector)
        } else {
            selector = readIndex.get(idx)
        }
        return selector
    }

    @Throws(Exception::class)
    override fun write(encoder: Encoder, selector: ComponentSelector) {
        val idx = writeIndex.get(selector)
        if (idx == null) {
            // new value
            val index = writeIndex.size
            encoder.writeSmallInt(index)
            writeIndex.put(selector, index)
            delegate.write(encoder, selector)
        } else {
            // known value, only write index
            encoder.writeSmallInt(idx)
        }
    }
}
