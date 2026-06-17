/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.language.base.sources

import org.gradle.api.Incubating
import org.gradle.api.model.ObjectFactory
import org.gradle.api.reflect.ObjectInstantiationException
import org.gradle.language.base.LanguageSourceSet
import org.gradle.language.base.internal.AbstractLanguageSourceSet
import org.gradle.platform.base.ModelInstantiationException
import org.gradle.platform.base.internal.ComponentSpecIdentifier

/**
 * Base class that may be used for custom [LanguageSourceSet] implementations. However, it is generally better to use an
 * interface annotated with [org.gradle.model.Managed] and not use an implementation class at all.
 */
@Incubating
open class BaseLanguageSourceSet private constructor(info: SourceSetInfo) :
    AbstractLanguageSourceSet(validate(info).identifier, info.publicType, info.objectFactory.sourceDirectorySet("source", "source")) {
    /**
     * This is here as a convenience for subclasses to create additional SourceDirectorySets
     *
     * @since 5.0
     */
    protected val objectFactory: ObjectFactory

    constructor() : this(NEXT_SOURCE_SET_INFO.get()!!)

    init {
        this.objectFactory = info.objectFactory
    }

    private class SourceSetInfo(private val identifier: ComponentSpecIdentifier?, private val publicType: Class<out LanguageSourceSet?>?, private val objectFactory: ObjectFactory)
    companion object {
        private val NEXT_SOURCE_SET_INFO = ThreadLocal<SourceSetInfo?>()

        /**
         * Create a source set instance.
         *
         * @since 5.0
         */
        fun <T : LanguageSourceSet?> create(publicType: Class<out LanguageSourceSet?>, implementationType: Class<T?>, componentId: ComponentSpecIdentifier?, objectFactory: ObjectFactory): T? {
            NEXT_SOURCE_SET_INFO.set(SourceSetInfo(componentId, publicType, objectFactory))
            try {
                try {
                    return objectFactory.newInstance<T?>(implementationType)
                } catch (e: ObjectInstantiationException) {
                    throw ModelInstantiationException(String.format("Could not create LanguageSourceSet of type %s", publicType.getSimpleName()), e.cause)
                }
            } finally {
                NEXT_SOURCE_SET_INFO.set(null)
            }
        }

        private fun validate(info: SourceSetInfo): SourceSetInfo {
            if (info == null) {
                throw ModelInstantiationException("Direct instantiation of a BaseLanguageSourceSet is not permitted. Use a @ComponentType rule instead.")
            }
            return info
        }
    }
}
