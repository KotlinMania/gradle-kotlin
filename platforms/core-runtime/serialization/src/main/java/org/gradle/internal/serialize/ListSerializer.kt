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

import com.google.common.collect.ImmutableList

class ListSerializer<T : Any>(private val entrySerializer: Serializer<T>) : AbstractSerializer<List<@JvmSuppressWildcards T>>() {
    @Throws(Exception::class)
    override fun read(decoder: Decoder): ImmutableList<T> {
        val size = decoder.readInt()
        val values = ImmutableList.builderWithExpectedSize<T>(size)
        for (i in 0..<size) {
            values.add(entrySerializer.read(decoder))
        }
        return values.build()
    }

    @Throws(Exception::class)
    override fun write(encoder: Encoder, value: List<@JvmSuppressWildcards T>) {
        encoder.writeInt(value.size)
        for (t in value) {
            entrySerializer.write(encoder, t)
        }
    }
}
