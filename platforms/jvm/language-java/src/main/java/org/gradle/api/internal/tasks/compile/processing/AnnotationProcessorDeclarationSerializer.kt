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
package org.gradle.api.internal.tasks.compile.processing

import org.gradle.api.internal.tasks.compile.incremental.processing.IncrementalAnnotationProcessorType
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.Serializer
import java.io.EOFException

internal class AnnotationProcessorDeclarationSerializer private constructor() : Serializer<AnnotationProcessorDeclaration?> {
    @Throws(EOFException::class, Exception::class)
    override fun read(decoder: Decoder): AnnotationProcessorDeclaration? {
        val name = decoder.readString()
        val type = IncrementalAnnotationProcessorType.values()[decoder.readSmallInt()]
        return AnnotationProcessorDeclaration(name!!, type)
    }

    @Throws(Exception::class)
    override fun write(encoder: Encoder, value: AnnotationProcessorDeclaration) {
        encoder.writeString(value.className)
        encoder.writeSmallInt(value.type.ordinal)
    }

    companion object {
        val INSTANCE: AnnotationProcessorDeclarationSerializer = AnnotationProcessorDeclarationSerializer()
    }
}
