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

import org.gradle.api.internal.tasks.compile.filter.AnnotationProcessorFilter
import org.gradle.api.internal.tasks.compile.incremental.processing.AnnotationProcessingResult
import org.gradle.api.internal.tasks.compile.incremental.processing.AnnotationProcessorResult
import org.gradle.api.internal.tasks.compile.incremental.processing.IncrementalAnnotationProcessorType
import org.gradle.api.internal.tasks.compile.processing.AggregatingProcessor
import org.gradle.api.internal.tasks.compile.processing.AnnotationProcessorDeclaration
import org.gradle.api.internal.tasks.compile.processing.DynamicProcessor
import org.gradle.api.internal.tasks.compile.processing.IsolatingProcessor
import org.gradle.api.internal.tasks.compile.processing.NonIncrementalProcessor
import org.gradle.api.internal.tasks.compile.processing.SupportedOptionsCollectingProcessor
import org.gradle.api.internal.tasks.compile.processing.TimeTrackingProcessor
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.concurrent.CompositeStoppable
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.net.URLClassLoader
import java.util.Locale
import javax.annotation.processing.Processor
import javax.tools.JavaCompiler

/**
 * Wraps another [JavaCompiler.CompilationTask] and sets up its annotation processors
 * according to the provided processor declarations and processor path. Incremental processors
 * are decorated in order to validate their behavior.
 *
 * This class also serves a purpose when incremental annotation processing is not active.
 * It replaces the normal processor discovery, which suffers from file descriptor leaks
 * on Java 8 and below. Our own discovery mechanism does not have that issue.
 *
 * This also prevents the Gradle API from leaking into the annotation processor classpath.
 */
internal open class AnnotationProcessingCompileTask(
    private val delegate: JavaCompiler.CompilationTask,
    private val processorDeclarations: MutableSet<AnnotationProcessorDeclaration>,
    private val annotationProcessorPath: MutableList<File?>?,
    private val result: AnnotationProcessingResult
) : JavaCompiler.CompilationTask {
    private var processorClassloader: ClassLoader? = null
    private var called = false

    override fun addModules(moduleNames: Iterable<String?>?) {
    }

    override fun setProcessors(processors: Iterable<out Processor?>?) {
        throw UnsupportedOperationException("This decorator already handles annotation processing")
    }

    override fun setLocale(locale: Locale?) {
        delegate.setLocale(locale)
    }

    override fun call(): Boolean? {
        check(!called) { "Cannot reuse a compilation task" }
        called = true
        try {
            setupProcessors()
            return delegate.call()
        } finally {
            cleanupProcessors()
        }
    }

    private fun setupProcessors() {
        processorClassloader = createProcessorClassLoader()
        val processors: MutableList<Processor?> = ArrayList<Processor?>(processorDeclarations.size)
        if (!processorDeclarations.isEmpty()) {
            val supportedOptionsCollectingProcessor = SupportedOptionsCollectingProcessor()
            for (declaredProcessor in processorDeclarations) {
                val processorResult = AnnotationProcessorResult(result, declaredProcessor.className)
                result.annotationProcessorResults.add(processorResult)

                val processorClass = loadProcessor(declaredProcessor)
                var processor = instantiateProcessor(processorClass)
                supportedOptionsCollectingProcessor.addProcessor(processor)
                processor = decorateForIncrementalProcessing(processor, declaredProcessor.type, processorResult)
                processor = decorateForTimeTracking(processor, processorResult)
                processors.add(processor)
            }
            processors.add(supportedOptionsCollectingProcessor)
        }
        delegate.setProcessors(processors)
    }

    open fun createProcessorClassLoader(): ClassLoader {
        return URLClassLoader(
            DefaultClassPath.of(annotationProcessorPath).getAsURLArray(),
            AnnotationProcessorFilter.getFilteredClassLoader(delegate.javaClass.getClassLoader())
        )
    }

    private fun loadProcessor(declaredProcessor: AnnotationProcessorDeclaration): Class<*> {
        try {
            return processorClassloader!!.loadClass(declaredProcessor.className)
        } catch (e: ClassNotFoundException) {
            throw IllegalArgumentException("Annotation processor '" + declaredProcessor.className + "' not found", unwrapCause(e))
        }
    }

    private fun instantiateProcessor(processorClass: Class<*>): Processor {
        try {
            return processorClass.getConstructor().newInstance() as Processor
        } catch (e: Exception) {
            throw IllegalArgumentException("Could not instantiate annotation processor '" + processorClass.getName() + "'", unwrapCause(e))
        }
    }

    private fun unwrapCause(throwable: Throwable?): Throwable? {
        if (throwable is InvocationTargetException) {
            return throwable.cause
        }
        return throwable
    }

    private fun decorateForIncrementalProcessing(processor: Processor?, type: IncrementalAnnotationProcessorType, processorResult: AnnotationProcessorResult?): Processor {
        when (type) {
            IncrementalAnnotationProcessorType.ISOLATING -> return IsolatingProcessor(processor, processorResult)
            IncrementalAnnotationProcessorType.AGGREGATING -> return AggregatingProcessor(processor, processorResult)
            IncrementalAnnotationProcessorType.DYNAMIC -> return DynamicProcessor(processor, processorResult)
            else -> return NonIncrementalProcessor(processor, processorResult)
        }
    }

    private fun decorateForTimeTracking(processor: Processor?, processorResult: AnnotationProcessorResult?): Processor {
        return TimeTrackingProcessor(processor, processorResult)
    }

    private fun cleanupProcessors() {
        CompositeStoppable.stoppable(processorClassloader!!).stop()
    }
}
