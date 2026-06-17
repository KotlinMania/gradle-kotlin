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
import com.google.common.collect.Sets
import org.jspecify.annotations.NullMarked

/**
 * Serializer for `Set<T>` that writes elements in sorted order,
 * ensuring deterministic serialization regardless of input iteration order.
 *
 *
 * Elements must be [Comparable]. The wire format is identical to [SetSerializer]:
 * size followed by elements. On read, elements are returned in a [java.util.LinkedHashSet]
 * preserving the serialized (sorted) order.
 */
@NullMarked
class SortedSetSerializer<T : Comparable<T?>?>(private val entrySerializer: Serializer<T?>) : AbstractSerializer<MutableSet<T?>?>() {
    @Throws(Exception::class)
    override fun write(encoder: Encoder, value: MutableSet<T?>) {
        val sorted: MutableList<T?> = ArrayList<T?>(value)
        sorted.sort(null)
        encoder.writeInt(sorted.size)
        for (t in sorted) {
            entrySerializer.write(encoder, t)
        }
    }

    @Throws(Exception::class)
    override fun read(decoder: Decoder): MutableSet<T?> {
        val size = decoder.readInt()
        val set: MutableSet<T?> = Sets.newLinkedHashSetWithExpectedSize<T?>(size)
        for (i in 0..<size) {
            set.add(entrySerializer.read(decoder))
        }
        return set
    }

    public override fun equals(obj: Any): Boolean {
        if (!super.equals(obj)) {
            return false
        }
        val rhs = obj as SortedSetSerializer<*>
        return Objects.equal(entrySerializer, rhs.entrySerializer)
    }

    public override fun hashCode(): Int {
        return Objects.hashCode(super.hashCode(), entrySerializer)
    }
}
