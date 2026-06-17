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
import java.io.EOFException

abstract class AbstractCollectionSerializer<T, C : MutableCollection<T?>?>(protected val entrySerializer: Serializer<T?>) : Serializer<C?> {
    override fun equals(obj: Any?): Boolean {
        if (obj == null) {
            return false
        }
        if (obj === this) {
            return true
        }
        if (obj.javaClass != javaClass) {
            return false
        }

        val rhs = obj as AbstractCollectionSerializer<*, *>
        return Objects.equal(entrySerializer, rhs.entrySerializer)
    }

    override fun hashCode(): Int {
        return Objects.hashCode(javaClass, entrySerializer)
    }

    protected abstract fun createCollection(size: Int): C?

    @Throws(EOFException::class, Exception::class)
    override fun read(decoder: Decoder): C? {
        val size = decoder.readInt()
        val values = createCollection(size)
        for (i in 0..<size) {
            values!!.add(entrySerializer.read(decoder))
        }
        return values
    }

    @Throws(Exception::class)
    override fun write(encoder: Encoder, value: C?) {
        encoder.writeInt(value!!.size)
        for (t in value) {
            entrySerializer.write(encoder, t)
        }
    }
}
