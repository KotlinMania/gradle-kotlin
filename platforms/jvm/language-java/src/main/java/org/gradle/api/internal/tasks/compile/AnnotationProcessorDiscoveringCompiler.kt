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
package org.gradle.api.internal.tasks.compile

import com.google.common.base.Joiner
import com.google.common.base.Splitter
import com.google.common.collect.Sets
import org.gradle.api.InvalidUserDataException
import org.gradle.api.internal.tasks.compile.incremental.processing.IncrementalAnnotationProcessorType
import org.gradle.api.internal.tasks.compile.processing.AnnotationProcessorDeclaration
import org.gradle.api.internal.tasks.compile.processing.AnnotationProcessorDetector
import org.gradle.api.tasks.WorkResult
import org.gradle.language.base.internal.compile.Compiler

/**
 * Sets up annotation processing before delegating to the actual Java compiler.
 */
class AnnotationProcessorDiscoveringCompiler<T : JavaCompileSpec?>(private val delegate: Compiler<T?>, private val annotationProcessorDetector: AnnotationProcessorDetector) : Compiler<T?> {
    override fun execute(spec: T?): WorkResult? {
        val annotationProcessors = getEffectiveAnnotationProcessors(spec!!)
        spec.setEffectiveAnnotationProcessors(annotationProcessors)
        return delegate.execute(spec)
    }

    /**
     * Scans the processor path for processor declarations. Filters them if the explicit `-processor` argument is given.
     * Treats explicit processors that didn't have a matching declaration on the path as non-incremental.
     */
    private fun getEffectiveAnnotationProcessors(spec: JavaCompileSpec): MutableSet<AnnotationProcessorDeclaration?> {
        val declarations = annotationProcessorDetector.detectProcessors(spec.annotationProcessorPath)
        val compilerArgs: MutableList<String?> = spec.compileOptions!!.compilerArgs!!
        val processorIndex = compilerArgs.lastIndexOf("-processor")
        if (processorIndex == -1) {
            return Sets.newLinkedHashSet<AnnotationProcessorDeclaration?>(declarations.values)
        }
        if (processorIndex == compilerArgs.size - 1) {
            throw InvalidUserDataException("No processor specified for compiler argument -processor in requested compiler args: " + Joiner.on(" ").join(compilerArgs))
        }
        val explicitProcessors: MutableCollection<String> = Splitter.on(',').splitToList(compilerArgs.get(processorIndex + 1)!!)
        val effectiveProcessors: MutableSet<AnnotationProcessorDeclaration?> = LinkedHashSet<AnnotationProcessorDeclaration?>()
        for (explicitProcessor in explicitProcessors) {
            val declaration = declarations.get(explicitProcessor)
            if (declaration != null) {
                effectiveProcessors.add(declaration)
            } else {
                effectiveProcessors.add(AnnotationProcessorDeclaration(explicitProcessor, IncrementalAnnotationProcessorType.UNKNOWN))
            }
        }
        return effectiveProcessors
    }
}
