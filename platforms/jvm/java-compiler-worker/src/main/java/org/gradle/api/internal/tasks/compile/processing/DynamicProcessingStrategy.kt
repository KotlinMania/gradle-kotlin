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
import org.gradle.api.internal.tasks.compile.incremental.processing.IncrementalAnnotationProcessorType
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import javax.tools.JavaFileManager

/**
 * The strategy used for dynamic processors.
 *
 * @see DynamicProcessor
 */
class DynamicProcessingStrategy internal constructor(processorName: String?, result: AnnotationProcessorResult?) : IncrementalProcessingStrategy(result) {
    private var delegate: IncrementalProcessingStrategy

    init {
        this.delegate = NonIncrementalProcessingStrategy(processorName, result)
    }

    fun updateFromOptions(supportedOptions: MutableSet<String?>) {
        if (supportedOptions.contains(IncrementalAnnotationProcessorType.ISOLATING.getProcessorOption())) {
            delegate = IsolatingProcessingStrategy(result)
        } else if (supportedOptions.contains(IncrementalAnnotationProcessorType.AGGREGATING.getProcessorOption())) {
            delegate = AggregatingProcessingStrategy(result)
        }
    }

    override fun recordProcessingInputs(supportedAnnotationTypes: MutableSet<String?>?, annotations: MutableSet<out TypeElement?>?, roundEnv: RoundEnvironment?) {
        delegate.recordProcessingInputs(supportedAnnotationTypes, annotations, roundEnv)
    }

    override fun recordGeneratedType(name: CharSequence?, originatingElements: Array<Element?>?) {
        delegate.recordGeneratedType(name, originatingElements)
    }

    override fun recordGeneratedResource(location: JavaFileManager.Location?, pkg: CharSequence?, relativeName: CharSequence?, originatingElements: Array<Element?>?) {
        delegate.recordGeneratedResource(location, pkg, relativeName, originatingElements)
    }
}
