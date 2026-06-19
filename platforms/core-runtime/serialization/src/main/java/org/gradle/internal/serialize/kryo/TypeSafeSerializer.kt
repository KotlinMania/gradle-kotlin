/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.internal.serialize.kryo

import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.ObjectReader
import org.gradle.internal.serialize.ObjectWriter
import org.gradle.internal.serialize.StatefulSerializer

class TypeSafeSerializer<T>(private val type: Class<T>, private val serializer: StatefulSerializer<T?>) : StatefulSerializer<Any?> {
    override fun newReader(decoder: Decoder?): ObjectReader<Any?> {
        val reader = serializer.newReader(decoder)
        return object : ObjectReader<Any?> {
            @Throws(Exception::class)
            override fun read(): Any? {
                return reader!!.read()
            }
        }
    }

    override fun newWriter(encoder: Encoder?): ObjectWriter<Any?> {
        val writer = serializer.newWriter(encoder)
        return object : ObjectWriter<Any?> {
            @Throws(Exception::class)
            override fun write(value: Any?) {
                writer!!.write(type.cast(value))
            }
        }
    }
}
