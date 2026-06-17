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
package org.gradle.api.internal.tasks.compile.incremental.recomp

import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.CompilerApiData
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassSetAnalysisData
import org.gradle.api.internal.tasks.compile.incremental.processing.AnnotationProcessingData
import org.gradle.internal.serialize.AbstractSerializer
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.HierarchicalNameSerializer
import java.util.function.Supplier

class PreviousCompilationData(
    val outputSnapshot: ClassSetAnalysisData,
    val annotationProcessingData: AnnotationProcessingData,
    val classpathSnapshot: ClassSetAnalysisData,
    val compilerApiData: CompilerApiData
) {
    class Serializer(private val interner: StringInterner) : AbstractSerializer<PreviousCompilationData?>() {
        @Throws(Exception::class)
        override fun read(decoder: Decoder): PreviousCompilationData? {
            val hierarchicalNameSerializer = HierarchicalNameSerializer(interner)
            val classNameSerializerSupplier: Supplier<HierarchicalNameSerializer?> = Supplier { hierarchicalNameSerializer }
            val analysisSerializer = ClassSetAnalysisData.Serializer(classNameSerializerSupplier)
            val annotationProcessingDataSerializer = AnnotationProcessingData.Serializer(classNameSerializerSupplier)
            val compilerApiDataSerializer = CompilerApiData.Serializer(classNameSerializerSupplier)

            val outputSnapshot = analysisSerializer.read(decoder)
            val annotationProcessingData: AnnotationProcessingData = annotationProcessingDataSerializer.read(decoder)!!
            val classpathSnapshot = analysisSerializer.read(decoder)
            val compilerApiData = compilerApiDataSerializer.read(decoder)
            return PreviousCompilationData(outputSnapshot!!, annotationProcessingData, classpathSnapshot!!, compilerApiData!!)
        }

        @Throws(Exception::class)
        override fun write(encoder: Encoder, value: PreviousCompilationData) {
            val hierarchicalNameSerializer = HierarchicalNameSerializer(interner)
            val classNameSerializerSupplier: Supplier<HierarchicalNameSerializer?> = Supplier { hierarchicalNameSerializer }
            val analysisSerializer = ClassSetAnalysisData.Serializer(classNameSerializerSupplier)
            val annotationProcessingDataSerializer = AnnotationProcessingData.Serializer(classNameSerializerSupplier)
            val compilerApiDataSerializer = CompilerApiData.Serializer(classNameSerializerSupplier)

            analysisSerializer.write(encoder, value.outputSnapshot)
            annotationProcessingDataSerializer.write(encoder, value.annotationProcessingData)
            analysisSerializer.write(encoder, value.classpathSnapshot)
            compilerApiDataSerializer.write(encoder, value.compilerApiData)
        }
    }
}
