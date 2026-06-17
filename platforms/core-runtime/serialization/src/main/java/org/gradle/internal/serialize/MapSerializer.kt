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
package org.gradle.internal.serialize

import com.google.common.base.Objects

class MapSerializer<U, V>(private val keySerializer: Serializer<U?>, private val valueSerializer: Serializer<V?>) : AbstractSerializer<MutableMap<U?, V?>?>() {
    @Throws(Exception::class)
    override fun read(decoder: Decoder): MutableMap<U?, V?> {
        val size = decoder.readInt()
        val valueMap: MutableMap<U?, V?> = LinkedHashMap<U?, V?>(size)
        for (i in 0..<size) {
            val key = keySerializer.read(decoder)
            val value = valueSerializer.read(decoder)
            valueMap.put(key, value)
        }
        return valueMap
    }

    @Throws(Exception::class)
    override fun write(encoder: Encoder, value: MutableMap<U?, V?>) {
        encoder.writeInt(value.size)
        for (entry in value.entries) {
            keySerializer.write(encoder, entry.key)
            valueSerializer.write(encoder, entry.value)
        }
    }

    public override fun equals(obj: Any?): Boolean {
        if (!super.equals(obj)) {
            return false
        }

        val rhs = obj as MapSerializer<*, *>
        return Objects.equal(keySerializer, rhs.keySerializer)
                && Objects.equal(valueSerializer, rhs.valueSerializer)
    }

    public override fun hashCode(): Int {
        return Objects.hashCode(super.hashCode(), keySerializer, valueSerializer)
    }
}
