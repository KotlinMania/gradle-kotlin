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
package org.gradle.api.internal.artifacts.ivyservice.modulecache

import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.ComponentMetadata
import org.gradle.api.internal.artifacts.ModuleVersionIdentifierSerializer
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.UserProvidedMetadata
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.AttributeContainerSerializer
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.internal.serialize.AbstractSerializer
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import java.io.IOException

/**
 * This component metadata serializer is responsible for serializing metadata that comes out
 * of a [component metadata supplier][org.gradle.api.artifacts.ComponentMetadataSupplier] rule.
 * It does NOT contain full metadata, which can be confusing given the name of the class it's
 * supposed to serialize. This is, therefore, limited to the metadata necessary to perform selection
 * in a dynamic version resolver.
 */
@ServiceScope(Scope.Build::class)
class SuppliedComponentMetadataSerializer(private val moduleVersionIdentifierSerializer: ModuleVersionIdentifierSerializer, private val attributeContainerSerializer: AttributeContainerSerializer) :
    AbstractSerializer<ComponentMetadata?>() {
    @Throws(Exception::class)
    override fun read(decoder: Decoder): ComponentMetadata? {
        val id = moduleVersionIdentifierSerializer.read(decoder)
        val attributes: AttributeContainerInternal? = attributeContainerSerializer.read(decoder)
        val statusScheme = readStatusScheme(decoder)
        return UserProvidedMetadata(id, statusScheme, attributes!!.asImmutable())
    }

    @Throws(Exception::class)
    override fun write(encoder: Encoder, md: ComponentMetadata) {
        moduleVersionIdentifierSerializer.write(encoder, md.getId())
        attributeContainerSerializer.write(encoder, md.getAttributes())
        checkChangingFlag(md)
        writeStatusScheme(encoder, md)
    }

    private fun checkChangingFlag(md: ComponentMetadata) {
        val changing = md.isChanging()
        if (changing) {
            throw UnsupportedOperationException("User-supplied metadata shouldn't have changing=true")
        }
    }

    @Throws(IOException::class)
    private fun writeStatusScheme(encoder: Encoder, md: ComponentMetadata) {
        val statusScheme: MutableList<String?> = md.getStatusScheme()
        encoder.writeSmallInt(statusScheme.size)
        for (s in statusScheme) {
            encoder.writeString(s)
        }
    }

    @Throws(IOException::class)
    private fun readStatusScheme(decoder: Decoder): MutableList<String?> {
        val size = decoder.readSmallInt()
        val scheme = ImmutableList.builder<String?>()
        for (i in 0..<size) {
            scheme.add(decoder.readString())
        }
        return scheme.build()
    }
}
