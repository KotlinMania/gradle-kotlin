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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.result

import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.model.NamedObjectInstantiator
import org.gradle.internal.serialize.AbstractSerializer
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.internal.snapshot.impl.CoercingStringValueSnapshot
import java.io.IOException

/**
 * A lossy attribute container serializer. It's lossy because it doesn't preserve the attribute
 * types: it will serialize the contents as strings, and read them as strings, only for reporting
 * purposes.
 */
@ServiceScope(Scope.BuildSession::class)
class DesugaredAttributeContainerSerializer(private val attributesFactory: AttributesFactory, private val instantiator: NamedObjectInstantiator) : AbstractSerializer<AttributeContainer?>(),
    AttributeContainerSerializer {
    @Throws(IOException::class)
    override fun read(decoder: Decoder): ImmutableAttributes {
        var attributes = ImmutableAttributes.EMPTY
        val count = decoder.readSmallInt()
        for (i in 0..<count) {
            val name = decoder.readString()
            val type = decoder.readByte()
            if (type == BOOLEAN_ATTRIBUTE) {
                attributes = attributesFactory.concat<Boolean>(attributes, Attribute.of<Boolean>(name!!, Boolean::class.java), decoder.readBoolean())
            } else if (type == INTEGER_ATTRIBUTE) {
                attributes = attributesFactory.concat<Int>(attributes, Attribute.of<Int>(name!!, Int::class.java), decoder.readInt())
            } else {
                val value = decoder.readString()
                attributes = attributesFactory.concat<String>(attributes, Attribute.of<String>(name!!, String::class.java), CoercingStringValueSnapshot(value!!, instantiator))
            }
        }
        return attributes
    }

    @Throws(IOException::class)
    override fun write(encoder: Encoder, container: AttributeContainer) {
        encoder.writeSmallInt(container.keySet().size)
        for (attribute in container.keySet()) {
            encoder.writeString(attribute.getName())
            val type: Class<*> = attribute.getType()
            if (type == Boolean::class.java) {
                encoder.writeByte(BOOLEAN_ATTRIBUTE)
                encoder.writeBoolean((container.getAttribute(attribute) as kotlin.Boolean?)!!)
            } else if (type == Int::class.java) {
                encoder.writeByte(INTEGER_ATTRIBUTE)
                encoder.writeInt((container.getAttribute(attribute) as kotlin.Int?)!!)
            } else {
                assert(type == String::class.java) { "Unexpected attribute type " + type + " : should be " + String::class.java.getSimpleName() }
                encoder.writeByte(STRING_ATTRIBUTE)
                encoder.writeString(container.getAttribute(attribute) as String?)
            }
        }
    }

    companion object {
        private const val STRING_ATTRIBUTE: Byte = 1
        private const val BOOLEAN_ATTRIBUTE: Byte = 2
        private const val INTEGER_ATTRIBUTE: Byte = 3
    }
}
