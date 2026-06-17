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
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import javax.tools.JavaFileManager

/**
 * The strategy for isolating annotation processors.
 *
 * @see IsolatingProcessor
 */
internal class IsolatingProcessingStrategy(result: AnnotationProcessorResult) : IncrementalProcessingStrategy(result) {
    init {
        result.setType(IncrementalAnnotationProcessorType.ISOLATING)
    }

    override fun recordProcessingInputs(supportedAnnotationTypes: MutableSet<String?>?, annotations: MutableSet<out TypeElement?>?, roundEnv: RoundEnvironment?) {
    }

    override fun recordGeneratedType(name: CharSequence, originatingElements: Array<Element?>?) {
        val generatedType = name.toString()
        val originatingTypes = ElementUtils.getTopLevelTypeNames(originatingElements)
        val size = originatingTypes.size
        if (size != 1) {
            result.setFullRebuildCause("the generated type '" + generatedType + "' must have exactly one originating element, but had " + size)
        }
        result.addGeneratedType(generatedType, originatingTypes)
    }

    override fun recordGeneratedResource(location: JavaFileManager.Location?, pkg: CharSequence, relativeName: CharSequence?, originatingElements: Array<Element?>?) {
        val resourceLocation: GeneratedResource.Location? = GeneratedResource.Location.Companion.from(location)
        if (resourceLocation == null) {
            result.setFullRebuildCause(location.toString() + " is not supported for incremental annotation processing")
            return
        }
        val generatedResource = GeneratedResource(resourceLocation, pkg, relativeName)

        val originatingTypes = ElementUtils.getTopLevelTypeNames(originatingElements)
        val size = originatingTypes.size
        if (size != 1) {
            result.setFullRebuildCause("the generated resource '" + generatedResource + "' must have exactly one originating element, but had " + size)
        }
        result.addGeneratedResource(generatedResource, originatingTypes)
    }
}
