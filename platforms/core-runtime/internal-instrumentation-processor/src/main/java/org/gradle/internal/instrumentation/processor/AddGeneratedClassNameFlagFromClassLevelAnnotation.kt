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
package org.gradle.internal.instrumentation.processor

import org.gradle.internal.instrumentation.api.types.BytecodeInterceptorType
import org.gradle.internal.instrumentation.model.CallInterceptionRequest
import org.gradle.internal.instrumentation.model.RequestExtra
import org.gradle.internal.instrumentation.model.RequestExtra.OriginatingElement
import org.gradle.internal.instrumentation.processor.extensibility.RequestPostProcessorExtension
import org.gradle.internal.instrumentation.processor.modelreader.impl.AnnotationUtils
import org.gradle.internal.instrumentation.processor.modelreader.impl.AnnotationUtils.findAnnotationMirror
import org.gradle.internal.instrumentation.processor.modelreader.impl.AnnotationUtils.findAnnotationValueWithDefaults
import org.gradle.internal.instrumentation.processor.modelreader.impl.AnnotationUtils.findMetaAnnotationMirror
import java.util.Optional
import java.util.function.BiFunction
import java.util.function.Predicate
import java.util.function.Supplier
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.ExecutableElement
import javax.lang.model.util.Elements

class AddGeneratedClassNameFlagFromClassLevelAnnotation(
    private val elements: Elements,
    private val shouldAddExtraToRequestPredicate: Predicate<in CallInterceptionRequest?>,
    private val generatedClassNameProvidingAnnotation: Class<out Annotation?>,
    private val produceFlagForGeneratedClassName: BiFunction<String?, BytecodeInterceptorType?, RequestExtra?>
) : RequestPostProcessorExtension {
    override fun postProcessRequest(originalRequest: CallInterceptionRequest): MutableCollection<CallInterceptionRequest?>? {
        val maybeOriginatingElement: Optional<ExecutableElement?> = originalRequest.requestExtras!!.getByType(OriginatingElement::class.java)
            .map(OriginatingElement::getElement)

        if (!maybeOriginatingElement.isPresent()) {
            return mutableListOf<CallInterceptionRequest?>(originalRequest)
        }

        val shouldPostProcess = shouldAddExtraToRequestPredicate.test(originalRequest)
        if (!shouldPostProcess) {
            return mutableListOf<CallInterceptionRequest?>(originalRequest)
        }

        val enclosingElement = maybeOriginatingElement.get().getEnclosingElement()
        findAnnotationMirror(enclosingElement, generatedClassNameProvidingAnnotation).ifPresent { annotationMirror: AnnotationMirror? ->
            val generatedClassName: AnnotationValue = AnnotationUtils.findAnnotationValue(annotationMirror!!, "generatedClassName")
                .orElseThrow<IllegalStateException?>(Supplier { IllegalStateException("Annotation " + generatedClassNameProvidingAnnotation + " does not have a generatedClassName attribute") })
            val interceptionType: AnnotationValue = findAnnotationValueWithDefaults(elements, annotationMirror, "type")
                .orElseThrow<IllegalStateException?>(Supplier { IllegalStateException("Annotation " + generatedClassNameProvidingAnnotation + " does not have a type attribute") })
            originalRequest.requestExtras!!.add(
                produceFlagForGeneratedClassName.apply(
                    generatedClassName.getValue() as String?,
                    BytecodeInterceptorType.valueOf(interceptionType.getValue().toString())
                )
            )
        }

        return mutableListOf<CallInterceptionRequest?>(originalRequest)
    }

    companion object {
        fun ifHasAnnotation(annotationType: Class<out Annotation?>): Predicate<CallInterceptionRequest?> {
            return Predicate { request: CallInterceptionRequest? ->
                val maybeOriginatingElement: Optional<ExecutableElement> = request!!.requestExtras!!
                    .getByType(OriginatingElement::class.java)
                    .map(OriginatingElement::getElement)
                if (!maybeOriginatingElement.isPresent()) {
                    return@Predicate false
                }

                val originatingElement = maybeOriginatingElement.get()
                findMetaAnnotationMirror(originatingElement, annotationType).isPresent()
            }
        }

        fun ifHasExtraOfType(extraType: Class<out RequestExtra?>?): Predicate<CallInterceptionRequest?> {
            return Predicate { request: CallInterceptionRequest? -> request!!.requestExtras!!.getByType(extraType).isPresent() }
        }
    }
}
