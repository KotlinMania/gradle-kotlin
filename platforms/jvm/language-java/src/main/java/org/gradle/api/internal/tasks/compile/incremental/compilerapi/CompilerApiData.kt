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
package org.gradle.api.internal.tasks.compile.incremental.compilerapi

import org.gradle.api.internal.tasks.compile.incremental.compilerapi.constants.ConstantToDependentsMapping
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.DependentSetSerializer
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.DependentsSet
import org.gradle.internal.serialize.AbstractSerializer
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.HierarchicalNameSerializer
import org.gradle.internal.serialize.MapSerializer
import org.gradle.internal.serialize.SetSerializer
import java.util.function.Supplier

class CompilerApiData private constructor(
    val isAvailable: Boolean,
    val isSupportsConstantsMapping: Boolean,
    val sourceToClassMapping: MutableMap<String?, MutableSet<String?>?>?,
    val constantToClassMapping: ConstantToDependentsMapping
) {
    fun getConstantDependentsForClass(constantOrigin: String?): DependentsSet? {
        return constantToClassMapping.getConstantDependentsForClass(constantOrigin)
    }

    class Serializer(private val classNameSerializerSupplier: Supplier<HierarchicalNameSerializer>) : AbstractSerializer<CompilerApiData?>() {
        private val dependentSetSerializer: DependentSetSerializer

        init {
            this.dependentSetSerializer = DependentSetSerializer(classNameSerializerSupplier)
        }

        @Throws(Exception::class)
        override fun read(decoder: Decoder): CompilerApiData {
            val isAvailable = decoder.readBoolean()
            if (!isAvailable) {
                return unavailable()
            }
            val nameSerializer = classNameSerializerSupplier.get()
            val sourceToClassSerializer = MapSerializer<String?, MutableSet<String?>?>(nameSerializer, SetSerializer<String?>(nameSerializer))

            val sourceToClassMapping = sourceToClassSerializer.read(decoder)
            if (!decoder.readBoolean()) {
                return withoutConstantsMapping(sourceToClassMapping)
            }

            val constantDependentsSerializer = MapSerializer<String?, DependentsSet?>(nameSerializer, dependentSetSerializer)
            val constantDependents = constantDependentsSerializer.read(decoder)
            return withConstantsMapping(sourceToClassMapping, ConstantToDependentsMapping(constantDependents))
        }

        @Throws(Exception::class)
        override fun write(encoder: Encoder, value: CompilerApiData) {
            encoder.writeBoolean(value.isAvailable)
            if (value.isAvailable) {
                val nameSerializer = classNameSerializerSupplier.get()
                val sourceToClassSerializer = MapSerializer<String?, MutableSet<String?>?>(nameSerializer, SetSerializer<String?>(nameSerializer))

                sourceToClassSerializer.write(encoder, value.sourceToClassMapping)
                val supportsConstantsMapping = value.isSupportsConstantsMapping
                encoder.writeBoolean(supportsConstantsMapping)
                if (supportsConstantsMapping) {
                    val constantDependentsSerializer = MapSerializer<String?, DependentsSet?>(nameSerializer, dependentSetSerializer)
                    constantDependentsSerializer.write(encoder, value.constantToClassMapping.constantDependents)
                }
            }
        }
    }

    companion object {
        fun unavailable(): CompilerApiData {
            return CompilerApiData(false, false, mutableMapOf<String?, MutableSet<String?>?>(), ConstantToDependentsMapping.empty())
        }

        fun withoutConstantsMapping(sourceToClassMapping: MutableMap<String?, MutableSet<String?>?>?): CompilerApiData {
            return CompilerApiData(true, false, sourceToClassMapping, ConstantToDependentsMapping.empty())
        }

        fun withConstantsMapping(sourceToClassMapping: MutableMap<String?, MutableSet<String?>?>?, constantToDependentsMapping: ConstantToDependentsMapping): CompilerApiData {
            return CompilerApiData(true, true, sourceToClassMapping, constantToDependentsMapping)
        }
    }
}
