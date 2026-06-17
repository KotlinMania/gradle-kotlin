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
package org.gradle.internal.instrumentation.processor.features.withstaticreference

import org.gradle.internal.instrumentation.api.annotations.features.withstaticreference.WithExtensionReferences
import org.gradle.internal.instrumentation.model.CallInterceptionRequest
import org.gradle.internal.instrumentation.model.CallableKindInfo
import org.gradle.internal.instrumentation.model.RequestExtra.OriginatingElement
import org.gradle.internal.instrumentation.processor.extensibility.RequestPostProcessorExtension
import org.gradle.internal.instrumentation.processor.modelreader.impl.AnnotationUtils
import org.gradle.internal.instrumentation.processor.modelreader.impl.TypeUtils
import org.gradle.internal.instrumentation.util.NameUtil
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.ExecutableElement
import javax.lang.model.type.TypeMirror

class WithExtensionReferencesReader : RequestPostProcessorExtension {
    override fun postProcessRequest(originalRequest: CallInterceptionRequest?): MutableCollection<CallInterceptionRequest?>? {
        val request = checkNotNull(originalRequest)
        if (shouldPostProcess(request)) {
            request.requestExtras!!.getByType(OriginatingElement::class.java).ifPresent { originatingElement: OriginatingElement ->
                val element: ExecutableElement = originatingElement.element ?: return@ifPresent
                AnnotationUtils.findAnnotationMirror(element, WithExtensionReferences::class.java).ifPresent { annotation: AnnotationMirror ->
                    AnnotationUtils.findAnnotationValue(annotation, "toClass").ifPresent { value: AnnotationValue ->
                        val typeMirror = value.value as TypeMirror
                        val type = TypeUtils.extractType(typeMirror)
                        val methodName: String? = Companion.extractMethodName(request, annotation)
                        request.requestExtras!!.add(WithExtensionReferencesExtra(type, methodName))
                    }
                }
            }
        }
        return mutableListOf<CallInterceptionRequest?>(request)
    }

    companion object {
        private fun extractMethodName(originalRequest: CallInterceptionRequest, annotation: AnnotationMirror): String? {
            return AnnotationUtils.findAnnotationValue(annotation, "methodName")
                .map { it: AnnotationValue? -> it?.getValue() as String? }
                .filter { value: String? -> !value.isNullOrEmpty() }
                .orElse(NameUtil.interceptedJvmMethodName(checkNotNull(originalRequest.interceptedCallable)))
        }

        private fun shouldPostProcess(request: CallInterceptionRequest): Boolean {
            val kind = request.interceptedCallable!!.kind
            return kind == CallableKindInfo.INSTANCE_METHOD || kind == CallableKindInfo.GROOVY_PROPERTY_GETTER || kind == CallableKindInfo.GROOVY_PROPERTY_SETTER
        }
    }
}
