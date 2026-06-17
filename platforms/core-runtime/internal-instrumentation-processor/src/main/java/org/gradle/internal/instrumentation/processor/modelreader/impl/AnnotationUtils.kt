/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.internal.instrumentation.processor.modelreader.impl

import java.util.Map
import java.util.Optional
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.Element
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.util.Elements

object AnnotationUtils {
    fun findMetaAnnotationMirror(element: Element, annotationClass: Class<out Annotation?>): Optional<out AnnotationMirror?> {
        return collectMetaAnnotations(element).stream().filter { it: AnnotationMirror -> AnnotationUtils.isAnnotationOfType(it, annotationClass) }.findFirst()
    }

    private fun collectMetaAnnotations(annotatedElement: Element): MutableSet<AnnotationMirror> {
        val result: MutableSet<AnnotationMirror> = LinkedHashSet()

        class Recurse {
            fun recurse(annotatedElement: Element) {
                annotatedElement.getAnnotationMirrors().forEach { annotationMirror: AnnotationMirror ->
                    val element = annotationMirror.getAnnotationType().asElement()
                    if (element is TypeElement && result.add(annotationMirror)) {
                        recurse(element)
                    }
                }
            }
        }

        Recurse().recurse(annotatedElement)
        return result
    }

    fun findAnnotationMirror(element: Element, annotationClass: Class<out Annotation?>): Optional<out AnnotationMirror?> {
        return element.getAnnotationMirrors().stream().filter { it: AnnotationMirror -> AnnotationUtils.isAnnotationOfType(it, annotationClass) }.findFirst()
    }

    @JvmStatic
    fun findAnnotationValue(annotation: AnnotationMirror, key: String): Optional<AnnotationValue> {
        for ((element, value) in annotation.getElementValues().entries) {
            if (element.getSimpleName().toString() == key) {
                return Optional.ofNullable(value)
            }
        }
        return Optional.empty()
    }

    @JvmStatic
    fun findAnnotationValueWithDefaults(elements: Elements, annotation: AnnotationMirror, key: String): Optional<AnnotationValue> {
        for ((element, value) in elements.getElementValuesWithDefaults(annotation).entries) {
            if (element.getSimpleName().toString() == key) {
                return Optional.ofNullable(value)
            }
        }
        return Optional.empty()
    }

    fun isAnnotationOfType(annotation: AnnotationMirror, type: Class<out Annotation>): Boolean {
        return annotation.getAnnotationType().toString() == type.getCanonicalName()
    }
}
