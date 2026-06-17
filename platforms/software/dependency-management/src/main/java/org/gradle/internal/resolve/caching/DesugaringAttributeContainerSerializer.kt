/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.internal.resolve.caching

import org.gradle.api.Named
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.AttributeContainerSerializer
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.model.NamedObjectInstantiator
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.snapshot.impl.CoercingStringValueSnapshot
import java.io.IOException

/**
 * A thread-safe and reusable attribute container serializer that will desugar typed attributes.
 *
 * Attributes that are of types different than `String` or `boolean` will be desugared
 * before serialization. The process requires the attribute type to implement [Named].
 */
class DesugaringAttributeContainerSerializer(private val attributesFactory: AttributesFactory, private val namedObjectInstantiator: NamedObjectInstantiator?) : AttributeContainerSerializer {
    @Throws(IOException::class)
    override fun read(decoder: Decoder): ImmutableAttributes {
        var attributes = ImmutableAttributes.EMPTY
        val count = decoder.readSmallInt()
        for (i in 0..<count) {
            val name = decoder.readString()
            val type = decoder.readByte()
            if (type == BOOLEAN_ATTRIBUTE) {
                attributes = attributesFactory.concat<Boolean?>(attributes, Attribute.of<Boolean?>(name, Boolean::class.java), decoder.readBoolean())
            } else if (type == STRING_ATTRIBUTE) {
                val value = decoder.readString()
                attributes = attributesFactory.concat<String?>(attributes, Attribute.of<String?>(name, String::class.java), value)
            } else if (type == INTEGER_ATTRIBUTE) {
                val value = decoder.readInt()
                attributes = attributesFactory.concat<Int?>(attributes, Attribute.of<Int?>(name, Int::class.java), value)
            } else if (type == DESUGARED_ATTRIBUTE) {
                val value = decoder.readString()
                attributes = attributesFactory.concat<String?>(attributes, Attribute.of<String?>(name, String::class.java), CoercingStringValueSnapshot(value!!, namedObjectInstantiator!!))
            }
        }
        return attributes
    }

    @Throws(IOException::class)
    override fun write(encoder: Encoder, container: AttributeContainer) {
        encoder.writeSmallInt(container.keySet().size)
        for (attribute in container.keySet()) {
            encoder.writeString(attribute.getName())
            if (attribute.getType() == Boolean::class.java) {
                encoder.writeByte(BOOLEAN_ATTRIBUTE)
                encoder.writeBoolean((container.getAttribute(attribute) as kotlin.Boolean?)!!)
            } else if (attribute.getType() == String::class.java) {
                encoder.writeByte(STRING_ATTRIBUTE)
                encoder.writeString(container.getAttribute(attribute) as String?)
            } else if (attribute.getType() == Int::class.java) {
                encoder.writeByte(INTEGER_ATTRIBUTE)
                encoder.writeInt((container.getAttribute(attribute) as kotlin.Int?)!!)
            } else {
                assert(Named::class.java.isAssignableFrom(attribute.getType()))
                val attributeValue = container.getAttribute(attribute) as Named?
                encoder.writeByte(DESUGARED_ATTRIBUTE)
                encoder.writeString(attributeValue!!.getName())
            }
        }
    }

    companion object {
        private const val STRING_ATTRIBUTE: Byte = 1
        private const val BOOLEAN_ATTRIBUTE: Byte = 2
        private const val DESUGARED_ATTRIBUTE: Byte = 3
        private const val INTEGER_ATTRIBUTE: Byte = 4
    }
}
