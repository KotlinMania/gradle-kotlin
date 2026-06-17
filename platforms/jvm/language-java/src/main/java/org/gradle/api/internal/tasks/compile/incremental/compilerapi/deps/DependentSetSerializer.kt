/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps

import com.google.common.collect.ImmutableSet
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.DependentsSet.Companion.dependencyToAll
import org.gradle.internal.serialize.AbstractSerializer
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.HierarchicalNameSerializer
import java.util.function.Supplier

class DependentSetSerializer(private val hierarchicalNameSerializerSupplier: Supplier<HierarchicalNameSerializer>) : AbstractSerializer<DependentsSet?>() {
    @Throws(Exception::class)
    override fun read(decoder: Decoder): DependentsSet? {
        val nameSerializer = hierarchicalNameSerializerSupplier.get()
        val b = decoder.readByte()
        if (b.toInt() == 0) {
            return dependencyToAll(decoder.readString())
        }

        val privateBuilder = ImmutableSet.builder<String?>()
        var count = decoder.readSmallInt()
        for (i in 0..<count) {
            privateBuilder.add(nameSerializer.read(decoder))
        }

        val accessibleBuilder = ImmutableSet.builder<String?>()
        count = decoder.readSmallInt()
        for (i in 0..<count) {
            accessibleBuilder.add(nameSerializer.read(decoder))
        }

        val resourceBuilder = ImmutableSet.builder<GeneratedResource?>()
        count = decoder.readSmallInt()
        for (i in 0..<count) {
            val location: GeneratedResource.Location? = GeneratedResource.Location.values()[decoder.readSmallInt()]
            val path = nameSerializer.read(decoder)
            resourceBuilder.add(GeneratedResource(location, path))
        }
        return DependentsSet.dependents(privateBuilder.build(), accessibleBuilder.build(), resourceBuilder.build())
    }

    @Throws(Exception::class)
    override fun write(encoder: Encoder, dependentsSet: DependentsSet) {
        val nameSerializer = hierarchicalNameSerializerSupplier.get()
        if (dependentsSet.isDependencyToAll) {
            encoder.writeByte(0.toByte())
            encoder.writeString(dependentsSet.description)
        } else {
            encoder.writeByte(1.toByte())
            encoder.writeSmallInt(dependentsSet.privateDependentClasses.size())
            for (className in dependentsSet.privateDependentClasses!!) {
                nameSerializer.write(encoder, className)
            }
            encoder.writeSmallInt(dependentsSet.accessibleDependentClasses.size())
            for (className in dependentsSet.accessibleDependentClasses!!) {
                nameSerializer.write(encoder, className)
            }
            encoder.writeSmallInt(dependentsSet.dependentResources.size())
            for (resource in dependentsSet.dependentResources!!) {
                encoder.writeSmallInt(resource!!.location!!.ordinal)
                nameSerializer.write(encoder, resource.path)
            }
        }
    }
}
