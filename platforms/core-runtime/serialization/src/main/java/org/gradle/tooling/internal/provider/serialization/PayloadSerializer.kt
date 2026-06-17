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
package org.gradle.tooling.internal.provider.serialization

import org.apache.commons.io.IOUtils
import org.gradle.internal.Cast
import org.gradle.internal.UncheckedException
import org.gradle.internal.io.StreamByteBuffer
import org.gradle.internal.io.StreamByteBuffer.Companion.of
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import javax.annotation.concurrent.ThreadSafe

@ThreadSafe
@ServiceScope(Scope.UserHome::class)
class PayloadSerializer(private val classLoaderRegistry: PayloadClassLoaderRegistry) {
    fun serialize(payload: Any?): SerializedPayload {
        if (payload == null) {
            return SerializedPayload(null, mutableListOf<ByteArray?>())
        }

        val map = classLoaderRegistry.newSerializeSession()
        try {
            val buffer = StreamByteBuffer()
            val objectStream: ObjectOutputStream = PayloadSerializerObjectOutputStream(buffer.outputStream, map)

            try {
                objectStream.writeObject(payload)
            } finally {
                IOUtils.closeQuietly(objectStream)
            }

            val classLoaders: MutableMap<Short?, ClassLoaderDetails?> = HashMap<Short?, ClassLoaderDetails?>()
            map.collectClassLoaderDefinitions(classLoaders)
            return SerializedPayload(classLoaders, buffer.readAsListOfByteArrays())
        } catch (e: IOException) {
            throw UncheckedException.throwAsUncheckedException(e)
        }
    }

    fun deserialize(payload: SerializedPayload): Any? {
        if (payload.getSerializedModel().isEmpty()) {
            return null
        }

        val map = classLoaderRegistry.newDeserializeSession()
        try {
            val classLoaderDetails = Cast.uncheckedNonnullCast<MutableMap<Short?, ClassLoaderDetails?>?>(payload.getHeader())
            val buffer = of(payload.getSerializedModel())
            val objectStream: ObjectInputStream = PayloadSerializerObjectInputStream(buffer.inputStream, javaClass.getClassLoader(), classLoaderDetails, map)
            return objectStream.readObject()
        } catch (e: Exception) {
            throw UncheckedException.throwAsUncheckedException(e)
        }
    }
}
