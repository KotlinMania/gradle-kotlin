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

import org.gradle.api.internal.tasks.compile.incremental.processing.AnnotationProcessorResult
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import javax.tools.JavaFileManager

/**
 * The strategy that updates the processing result according to the type and runtime behavior of a processor.
 */
internal abstract class IncrementalProcessingStrategy(protected val result: AnnotationProcessorResult?) {
    abstract fun recordProcessingInputs(supportedAnnotationTypes: MutableSet<String?>?, annotations: MutableSet<out TypeElement?>?, roundEnv: RoundEnvironment?)

    abstract fun recordGeneratedType(name: CharSequence?, originatingElements: Array<Element?>?)

    abstract fun recordGeneratedResource(location: JavaFileManager.Location?, pkg: CharSequence?, relativeName: CharSequence?, originatingElements: Array<Element?>?)

    /**
     * We don't trigger a full recompile on resource reads, because we already trigger a full recompile when any
     * resource changes.
     */
    fun recordAccessedResource(location: JavaFileManager.Location?, pkg: CharSequence?, relativeName: CharSequence?) {
    }
}
