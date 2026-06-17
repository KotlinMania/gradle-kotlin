/*
 * Copyright 2026 the original author or authors.
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
import com.google.common.collect.Maps
import org.jspecify.annotations.NullMarked
import java.util.Collections

/**
 * Serializer for `Map<K, V>` that writes entries in sorted key order,
 * ensuring deterministic serialization regardless of input iteration order.
 *
 *
 * Keys must be [Comparable]. The wire format is identical to [MapSerializer]:
 * size followed by key/value pairs. On read, entries are returned in a [LinkedHashMap]
 * preserving the serialized (sorted) order.
 */
@NullMarked
class SortedMapSerializer<K : Comparable<K?>?, V>(private val keySerializer: Serializer<K?>, private val valueSerializer: Serializer<V?>) : AbstractSerializer<MutableMap<K?, V?>?>() {
    @Throws(Exception::class)
    override fun write(encoder: Encoder, value: MutableMap<K?, V?>) {
        val sortedKeys: MutableList<K?> = ArrayList<K?>(value.keys)
        Collections.sort<K?>(sortedKeys)

        encoder.writeInt(sortedKeys.size)
        for (key in sortedKeys) {
            keySerializer.write(encoder, key)
            valueSerializer.write(encoder, value.get(key))
        }
    }

    @Throws(Exception::class)
    override fun read(decoder: Decoder): MutableMap<K?, V?> {
        val size = decoder.readInt()
        val map: MutableMap<K?, V?> = Maps.newLinkedHashMapWithExpectedSize<K?, V?>(size)
        for (i in 0..<size) {
            val key = keySerializer.read(decoder)
            val value = valueSerializer.read(decoder)
            map.put(key, value)
        }
        return map
    }

    public override fun equals(obj: Any): Boolean {
        if (!super.equals(obj)) {
            return false
        }
        val rhs = obj as SortedMapSerializer<*, *>
        return Objects.equal(keySerializer, rhs.keySerializer)
                && Objects.equal(valueSerializer, rhs.valueSerializer)
    }

    public override fun hashCode(): Int {
        return Objects.hashCode(super.hashCode(), keySerializer, valueSerializer)
    }
}
