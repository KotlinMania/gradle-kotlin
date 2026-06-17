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

import org.gradle.api.internal.tasks.compile.incremental.processing.IncrementalAnnotationProcessorType
import java.io.Serializable

/**
 * Information about an annotation processor, based on its static metadata
 * in `META-INF/services/javax.annotation.processing.Processor` and
 * `META-INF/gradle/incremental.annotation.processors`
 */
class AnnotationProcessorDeclaration(@JvmField val className: String, @JvmField val type: IncrementalAnnotationProcessorType) : Serializable {
    override fun toString(): String {
        return className + " (type: " + type + ")"
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val that = o as AnnotationProcessorDeclaration

        if (className != that.className) {
            return false
        }
        return type == that.type
    }

    override fun hashCode(): Int {
        var result = className.hashCode()
        result = 31 * result + type.hashCode()
        return result
    }
}
