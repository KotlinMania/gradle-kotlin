/*
 * Copyright 2019 the original author or authors.
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

import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement

/**
 * Collects all registered processors' supported options.
 *
 *
 * This is a workaround for https://bugs.openjdk.java.net/browse/JDK-8162455, which
 * will be triggered a lot more during incremental compilations, and can fail builds
 * when combined with `-Werror`.
 *
 *
 * This processor needs to be added last to make sure that all other processors
 * have been [initialized][Processor.init] when
 * [.getSupportedOptions] is called.
 */
class SupportedOptionsCollectingProcessor : AbstractProcessor() {
    private val processors: MutableList<Processor> = ArrayList<Processor>()

    fun addProcessor(processor: Processor?) {
        processors.add(processor!!)
    }

    override fun getSupportedAnnotationTypes(): MutableSet<String?> {
        return mutableSetOf<String?>("*")
    }

    override fun getSupportedOptions(): MutableSet<String?> {
        val supportedOptions: MutableSet<String?> = HashSet<String?>()
        for (processor in processors) {
            supportedOptions.addAll(processor.getSupportedOptions())
        }
        return supportedOptions
    }

    override fun getSupportedSourceVersion(): SourceVersion? {
        return SourceVersion.latestSupported()
    }

    override fun process(set: MutableSet<out TypeElement?>?, roundEnvironment: RoundEnvironment?): Boolean {
        return false
    }
}
