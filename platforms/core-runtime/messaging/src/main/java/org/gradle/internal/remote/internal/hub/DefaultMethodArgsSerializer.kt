/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.internal.remote.internal.hub

import org.gradle.internal.Cast
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.Serializer
import org.gradle.internal.serialize.SerializerRegistry

internal class DefaultMethodArgsSerializer(private val serializerRegistries: MutableList<SerializerRegistry>, private val defaultArgsSerializer: MethodArgsSerializer) : MethodArgsSerializer {
    override fun forTypes(types: Array<Class<*>?>): Serializer<Array<Any?>?>? {
        if (types.size == 0) {
            return EmptyArraySerializer()
        }
        var selected: SerializerRegistry? = null
        for (serializerRegistry in serializerRegistries) {
            if (serializerRegistry.canSerialize(types[0])) {
                selected = serializerRegistry
                break
            }
        }
        if (selected == null) {
            return defaultArgsSerializer.forTypes(types)
        }

        val serializers = Cast.uncheckedNonnullCast<Array<Serializer<Any?>?>?>(arrayOfNulls<Serializer<*>>(types.size))
        for (i in types.indices) {
            val type = types[i]
            serializers[i] = Cast.uncheckedNonnullCast<Serializer<Any?>?>(selected.build(type))
        }
        return ArraySerializer(serializers)
    }

    private class ArraySerializer(private val serializers: Array<Serializer<Any?>?>) : Serializer<Array<Any?>?> {
        @Throws(Exception::class)
        override fun read(decoder: Decoder?): Array<Any?> {
            val result = arrayOfNulls<Any>(serializers.size)
            for (i in serializers.indices) {
                result[i] = serializers[i]!!.read(decoder)
            }
            return result
        }

        @Throws(Exception::class)
        override fun write(encoder: Encoder?, value: Array<Any?>) {
            for (i in value.indices) {
                serializers[i]!!.write(encoder, value[i])
            }
        }
    }

    private class EmptyArraySerializer : Serializer<Array<Any?>?> {
        override fun read(decoder: Decoder?): Array<Any?> {
            return ZERO_ARGS
        }

        override fun write(encoder: Encoder?, value: Array<Any?>?) {
        }
    }

    companion object {
        private val ZERO_ARGS = arrayOfNulls<Any>(0)
    }
}
