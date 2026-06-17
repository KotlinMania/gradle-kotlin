/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.internal.tasks.compile.incremental.deps

import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet
import it.unimi.dsi.fastutil.ints.IntSets
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.Serializer
import java.io.EOFException

class IntSetSerializer private constructor() : Serializer<IntSet?> {
    @Throws(EOFException::class, Exception::class)
    override fun read(decoder: Decoder): IntSet? {
        val size = decoder.readInt()
        if (size == 0) {
            return IntSets.EMPTY_SET
        }
        val result: IntSet = IntOpenHashSet(size)
        for (i in 0..<size) {
            result.add(decoder.readInt())
        }
        return result
    }

    @Throws(Exception::class)
    override fun write(encoder: Encoder, value: IntSet) {
        encoder.writeInt(value.size)
        val iterator = value.iterator()
        while (iterator.hasNext()) {
            encoder.writeInt(iterator.nextInt())
        }
    }

    companion object {
        val INSTANCE: IntSetSerializer = IntSetSerializer()
    }
}
