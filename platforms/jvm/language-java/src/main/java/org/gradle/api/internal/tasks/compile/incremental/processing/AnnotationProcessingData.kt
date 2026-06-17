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
package org.gradle.api.internal.tasks.compile.incremental.processing

import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.GeneratedResource
import org.gradle.internal.serialize.AbstractSerializer
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.HierarchicalNameSerializer
import org.gradle.internal.serialize.MapSerializer
import org.gradle.internal.serialize.SetSerializer
import java.util.function.Supplier

class AnnotationProcessingData @JvmOverloads constructor(
    generatedTypesByOrigin: MutableMap<String?, MutableSet<String?>?> = ImmutableMap.of<String?, MutableSet<String?>?>(),
    aggregatedTypes: MutableSet<String?> = ImmutableSet.of<String?>(),
    generatedTypesDependingOnAllOthers: MutableSet<String?> = ImmutableSet.of<String?>(),
    generatedResourcesByOrigin: MutableMap<String?, MutableSet<GeneratedResource?>?> = ImmutableMap.of<String?, MutableSet<GeneratedResource?>?>(),
    generatedResourcesDependingOnAllOthers: MutableSet<GeneratedResource?> = ImmutableSet.of<GeneratedResource?>(),
    val fullRebuildCause: String? = null
) {
    val generatedTypesByOrigin: MutableMap<String?, MutableSet<String?>?>
    val aggregatedTypes: MutableSet<String?>
    val generatedTypesDependingOnAllOthers: MutableSet<String?>
    val generatedResourcesByOrigin: MutableMap<String?, MutableSet<GeneratedResource?>?>
    val generatedResourcesDependingOnAllOthers: MutableSet<GeneratedResource?>

    init {
        this.generatedTypesByOrigin = ImmutableMap.copyOf<String?, MutableSet<String?>?>(generatedTypesByOrigin)
        this.aggregatedTypes = ImmutableSet.copyOf<String?>(aggregatedTypes)
        this.generatedTypesDependingOnAllOthers = ImmutableSet.copyOf<String?>(generatedTypesDependingOnAllOthers)
        this.generatedResourcesByOrigin = ImmutableMap.copyOf<String?, MutableSet<GeneratedResource?>?>(generatedResourcesByOrigin)
        this.generatedResourcesDependingOnAllOthers = ImmutableSet.copyOf<GeneratedResource?>(generatedResourcesDependingOnAllOthers)
    }

    class Serializer(private val classNameSerializerSupplier: Supplier<HierarchicalNameSerializer>) : AbstractSerializer<AnnotationProcessingData?>() {
        @Throws(Exception::class)
        override fun read(decoder: Decoder): AnnotationProcessingData {
            val hierarchicalNameSerializer = classNameSerializerSupplier.get()
            val typesSerializer = SetSerializer<String?>(hierarchicalNameSerializer)
            val generatedTypesSerializer = MapSerializer<String?, MutableSet<String?>?>(hierarchicalNameSerializer, typesSerializer)
            val resourceSerializer = GeneratedResourceSerializer(hierarchicalNameSerializer)
            val resourcesSerializer = SetSerializer<GeneratedResource?>(resourceSerializer)
            val generatedResourcesSerializer = MapSerializer<String?, MutableSet<GeneratedResource?>?>(hierarchicalNameSerializer, resourcesSerializer)


            val generatedTypes = generatedTypesSerializer.read(decoder)
            val aggregatedTypes = typesSerializer.read(decoder)
            val generatedTypesDependingOnAllOthers = typesSerializer.read(decoder)
            val fullRebuildCause = decoder.readNullableString()
            val generatedResources = generatedResourcesSerializer.read(decoder)
            val generatedResourcesDependingOnAllOthers = resourcesSerializer.read(decoder)

            return AnnotationProcessingData(generatedTypes, aggregatedTypes!!, generatedTypesDependingOnAllOthers!!, generatedResources, generatedResourcesDependingOnAllOthers!!, fullRebuildCause)
        }

        @Throws(Exception::class)
        override fun write(encoder: Encoder, value: AnnotationProcessingData) {
            val hierarchicalNameSerializer = classNameSerializerSupplier.get()
            val typesSerializer = SetSerializer<String?>(hierarchicalNameSerializer)
            val generatedTypesSerializer = MapSerializer<String?, MutableSet<String?>?>(hierarchicalNameSerializer, typesSerializer)
            val resourceSerializer = GeneratedResourceSerializer(hierarchicalNameSerializer)
            val resourcesSerializer = SetSerializer<GeneratedResource?>(resourceSerializer)
            val generatedResourcesSerializer = MapSerializer<String?, MutableSet<GeneratedResource?>?>(hierarchicalNameSerializer, resourcesSerializer)

            generatedTypesSerializer.write(encoder, value.generatedTypesByOrigin)
            typesSerializer.write(encoder, value.aggregatedTypes)
            typesSerializer.write(encoder, value.generatedTypesDependingOnAllOthers)
            encoder.writeNullableString(value.fullRebuildCause)
            generatedResourcesSerializer.write(encoder, value.generatedResourcesByOrigin)
            resourcesSerializer.write(encoder, value.generatedResourcesDependingOnAllOthers)
        }
    }
}
