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
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.TypeElement

/**
 * An annotation processor which can decide whether it is isolating, aggregating or non-incremental at runtime.
 * It needs to return its type through the [.getSupportedOptions] method in the format defined by
 * [IncrementalAnnotationProcessorType.getProcessorOption].
 */
class DynamicProcessor(delegate: Processor, result: AnnotationProcessorResult?) : DelegatingProcessor(delegate) {
    private val strategy: DynamicProcessingStrategy

    init {
        strategy = DynamicProcessingStrategy(delegate.javaClass.getName(), result)
    }

    override fun init(processingEnv: ProcessingEnvironment) {
        val incrementalFiler = IncrementalFiler(processingEnv.getFiler(), strategy)
        val incrementalEnvironment = IncrementalProcessingEnvironment(processingEnv, incrementalFiler)
        super.init(incrementalEnvironment)
        strategy.updateFromOptions(getSupportedOptions())
    }

    // it's a delegation
    override fun process(annotations: MutableSet<out TypeElement?>?, roundEnv: RoundEnvironment?): Boolean {
        strategy.recordProcessingInputs(getSupportedAnnotationTypes(), annotations, roundEnv)
        return super.process(annotations, roundEnv)
    }
}
