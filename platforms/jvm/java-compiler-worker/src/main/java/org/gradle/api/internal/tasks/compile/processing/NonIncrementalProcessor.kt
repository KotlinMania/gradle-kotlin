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
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.TypeElement

/**
 * An annotation processor that did not opt into incremental processing.
 * Any use of such a processor will result in full recompilation.
 * As opposed to the other processor implementations, this one will not
 * decorate the processing environment, because there are some processors
 * that cast it to its implementation type, e.g. JavacProcessingEnvironment.
 */
class NonIncrementalProcessor(delegate: Processor, result: AnnotationProcessorResult?) : DelegatingProcessor(delegate) {
    private val strategy: NonIncrementalProcessingStrategy

    init {
        this.strategy = NonIncrementalProcessingStrategy(delegate.javaClass.getName(), result)
    }

    // it's a delegation
    override fun process(annotations: MutableSet<out TypeElement?>?, roundEnv: RoundEnvironment?): Boolean {
        strategy.recordProcessingInputs(getSupportedAnnotationTypes(), annotations, roundEnv)
        return super.process(annotations, roundEnv)
    }
}
