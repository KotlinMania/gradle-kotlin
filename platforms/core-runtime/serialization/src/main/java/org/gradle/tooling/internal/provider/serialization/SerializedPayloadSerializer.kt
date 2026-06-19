/*
 * Copyright 2025 the original author or authors.
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
package org.gradle.tooling.internal.provider.serialization

import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.DefaultSerializer
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.Serializer

class SerializedPayloadSerializer : Serializer<SerializedPayload> {
    private val javaSerializer: Serializer<Any?> = DefaultSerializer<Any?>()

    @Throws(Exception::class)
    override fun write(encoder: Encoder, value: SerializedPayload) {
        javaSerializer.write(encoder, value.header)
        encoder.writeSmallInt(value.serializedModel.size)
        for (bytes in value.serializedModel) {
            encoder.writeBinary(bytes)
        }
    }

    @Throws(Exception::class)
    override fun read(decoder: Decoder): SerializedPayload {
        val header = javaSerializer.read(decoder)
        val count = decoder.readSmallInt()
        val chunks: MutableList<ByteArray> = ArrayList<ByteArray>(count)
        for (i in 0..<count) {
            chunks.add(decoder.readBinary())
        }
        return SerializedPayload(header, chunks)
    }
}
