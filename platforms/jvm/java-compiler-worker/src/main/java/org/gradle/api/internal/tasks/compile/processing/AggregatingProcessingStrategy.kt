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

import org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.GeneratedResource
import org.gradle.api.internal.tasks.compile.incremental.processing.AnnotationProcessorResult
import org.gradle.api.internal.tasks.compile.incremental.processing.IncrementalAnnotationProcessorType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.util.Objects
import java.util.stream.Collectors
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import javax.tools.JavaFileManager

/**
 * The strategy used for aggregating annotation processors.
 * @see AggregatingProcessor
 */
internal class AggregatingProcessingStrategy(result: AnnotationProcessorResult) : IncrementalProcessingStrategy(result) {
    init {
        result.setType(IncrementalAnnotationProcessorType.AGGREGATING)
    }

    override fun recordProcessingInputs(supportedAnnotationTypes: MutableSet<String?>, annotations: MutableSet<out TypeElement>, roundEnv: RoundEnvironment) {
        validateAnnotations(annotations)
        recordAggregatedTypes(supportedAnnotationTypes, annotations, roundEnv)
    }

    private fun validateAnnotations(annotations: MutableSet<out TypeElement>) {
        for (annotation in annotations) {
            val retention = annotation.getAnnotation<Retention?>(Retention::class.java)
            if (retention != null && retention.value == RetentionPolicy.SOURCE) {
                result.setFullRebuildCause("'@" + annotation.getSimpleName() + "' has source retention. Aggregating annotation processors require class or runtime retention")
            }
        }
    }

    private fun recordAggregatedTypes(supportedAnnotationTypes: MutableSet<String?>, annotations: MutableSet<out TypeElement>, roundEnv: RoundEnvironment) {
        if (supportedAnnotationTypes.contains("*")) {
            result.getAggregatedTypes().addAll(namesOfElements(roundEnv.getRootElements()))
        } else {
            for (annotation in annotations) {
                result.getAggregatedTypes().addAll(namesOfElements(roundEnv.getElementsAnnotatedWith(annotation)))
            }
        }
    }

    override fun recordGeneratedType(name: CharSequence, originatingElements: Array<Element?>?) {
        result.getGeneratedAggregatingTypes().add(name.toString())
    }

    override fun recordGeneratedResource(location: JavaFileManager.Location?, pkg: CharSequence, relativeName: CharSequence?, originatingElements: Array<Element?>?) {
        val resourceLocation: GeneratedResource.Location? = GeneratedResource.Location.Companion.from(location)
        if (resourceLocation == null) {
            result.setFullRebuildCause(location.toString() + " is not supported for incremental annotation processing")
        } else {
            result.getGeneratedAggregatingResources().add(GeneratedResource(resourceLocation, pkg, relativeName))
        }
    }

    override fun toString(): String {
        return "Aggregating strategy for " + result.getClassName()
    }

    companion object {
        private fun namesOfElements(orig: MutableSet<out Element?>?): MutableSet<String?> {
            if (orig == null || orig.isEmpty()) {
                return mutableSetOf<String?>()
            }
            return orig
                .stream()
                .map<Element?> { obj: ElementUtils?, originatingElement: Element? -> ElementUtils.getTopLevelType(originatingElement) }
                .map<String?> { obj: Element? -> ElementUtils.getElementName() }
                .filter { obj: String? -> Objects.nonNull(obj) }
                .collect(Collectors.toSet())
        }
    }
}
