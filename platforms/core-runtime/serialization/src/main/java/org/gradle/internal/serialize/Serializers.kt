/*
 * Copyright 2015 the original author or authors.
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

object Serializers {
    fun <T> stateful(serializer: Serializer<T?>): StatefulSerializer<T?> {
        return StatefulSerializerAdapter<T?>(serializer)
    }

    fun <T> constant(instance: T?): Serializer<T?> {
        return object : Serializer<T?> {
            override fun read(decoder: Decoder?): T? {
                return instance
            }

            override fun write(encoder: Encoder?, value: T?) {
                require(value === instance) { "Cannot serialize constant value: " + value }
            }
        }
    }

    private class StatefulSerializerAdapter<T>(private val serializer: Serializer<T?>) : StatefulSerializer<T?> {
        override fun newReader(decoder: Decoder?): ObjectReader<T?> {
            return object : ObjectReader<T?> {
                @Throws(Exception::class)
                override fun read(): T? {
                    return serializer.read(decoder)
                }
            }
        }

        override fun newWriter(encoder: Encoder?): ObjectWriter<T?> {
            return object : ObjectWriter<T?> {
                @Throws(Exception::class)
                override fun write(value: T?) {
                    serializer.write(encoder, value)
                }
            }
        }
    }
}
