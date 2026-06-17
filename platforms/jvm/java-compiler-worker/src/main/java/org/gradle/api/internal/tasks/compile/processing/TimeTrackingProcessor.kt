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

import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Stopwatch
import com.google.common.base.Ticker
import org.gradle.api.internal.tasks.compile.incremental.processing.AnnotationProcessorResult
import org.gradle.internal.Factory
import java.util.concurrent.TimeUnit
import javax.annotation.processing.Completion
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.Element
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement

class TimeTrackingProcessor @VisibleForTesting protected constructor(delegate: Processor?, private val result: AnnotationProcessorResult, ticker: Ticker) : DelegatingProcessor(delegate) {
    private val stopwatch: Stopwatch

    constructor(delegate: Processor?, result: AnnotationProcessorResult) : this(delegate, result, Ticker.systemTicker())

    init {
        this.stopwatch = Stopwatch.createUnstarted(ticker)
    }

    override fun getSupportedOptions(): MutableSet<String?>? {
        return track<MutableSet<String?>?>(object : Factory<MutableSet<String?>?> {
            override fun create(): MutableSet<String?>? {
                return super@TimeTrackingProcessor.getSupportedOptions()
            }
        })
    }

    override fun getSupportedAnnotationTypes(): MutableSet<String?>? {
        return track<MutableSet<String?>?>(object : Factory<MutableSet<String?>?> {
            override fun create(): MutableSet<String?>? {
                return super@TimeTrackingProcessor.getSupportedAnnotationTypes()
            }
        })
    }

    override fun getSupportedSourceVersion(): SourceVersion? {
        return track<SourceVersion?>(object : Factory<SourceVersion?> {
            override fun create(): SourceVersion? {
                return super@TimeTrackingProcessor.getSupportedSourceVersion()
            }
        })
    }

    override fun init(processingEnv: ProcessingEnvironment?) {
        track<Void?>(object : Factory<Void?> {
            override fun create(): Void? {
                super@TimeTrackingProcessor.init(processingEnv)
                return null
            }
        })
    }

    // it's a delegation
    override fun process(annotations: MutableSet<out TypeElement?>?, roundEnv: RoundEnvironment?): Boolean {
        return track<Boolean?>(object : Factory<Boolean?> {
            override fun create(): kotlin.Boolean? {
                return super@TimeTrackingProcessor.process(annotations, roundEnv)
            }
        })!!
    }

    override fun getCompletions(element: Element?, annotation: AnnotationMirror?, member: ExecutableElement?, userText: String?): Iterable<out Completion?>? {
        return track<Iterable<out Completion?>?>(object : Factory<Iterable<out Completion?>?> {
            override fun create(): Iterable<out Completion?>? {
                return super@TimeTrackingProcessor.getCompletions(element, annotation, member, userText)
            }
        })
    }

    private fun <T> track(factory: Factory<T?>): T? {
        stopwatch.start()
        try {
            return factory.create()
        } finally {
            stopwatch.stop()
            result.setExecutionTimeInMillis(stopwatch.elapsed(TimeUnit.MILLISECONDS))
        }
    }
}
