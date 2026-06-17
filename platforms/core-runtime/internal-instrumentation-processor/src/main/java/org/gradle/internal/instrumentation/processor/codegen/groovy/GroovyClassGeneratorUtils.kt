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
package org.gradle.internal.instrumentation.processor.codegen.groovy

import org.gradle.internal.instrumentation.model.CallInterceptionRequest
import org.gradle.internal.instrumentation.model.CallableInfo
import org.gradle.internal.instrumentation.model.CallableKindInfo
import org.gradle.internal.instrumentation.model.RequestExtra
import org.gradle.internal.instrumentation.processor.codegen.groovy.CallInterceptorSpecs.CallInterceptorSpec.ConstructorInterceptorSpec
import org.gradle.internal.instrumentation.processor.codegen.groovy.CallInterceptorSpecs.CallInterceptorSpec.NamedCallableInterceptorSpec
import org.gradle.internal.instrumentation.util.NameUtil
import org.objectweb.asm.Type
import java.util.function.Consumer

internal object GroovyClassGeneratorUtils {
    fun groupRequests(interceptionRequests: MutableCollection<CallInterceptionRequest?>): CallInterceptorSpecs {
        val namedRequests: MutableMap<String?, NamedCallableInterceptorSpec?> = LinkedHashMap<String?, NamedCallableInterceptorSpec?>()
        val constructorRequests: MutableMap<String?, ConstructorInterceptorSpec?> = LinkedHashMap<String?, ConstructorInterceptorSpec?>()
        interceptionRequests.forEach(Consumer { request: CallInterceptionRequest? ->
            if (request!!.requestExtras!!.getByType(RequestExtra.InterceptGroovyCalls::class.java).isPresent()) {
                val implementationName: String? = request.requestExtras!!.getByType(RequestExtra.InterceptGroovyCalls::class.java)
                    .map(RequestExtra.InterceptGroovyCalls::getImplementationClassName)
                    .orElseThrow({ IllegalStateException("Implementation class name is not set for " + request.interceptedCallable!!.owner!!.type) })
                val interceptorType = request.requestExtras!!.getByType(RequestExtra.InterceptGroovyCalls::class.java)
                    .map(RequestExtra.InterceptGroovyCalls::interceptionType)
                    .orElseThrow({ IllegalStateException("Interception type name is not set for " + request.interceptedCallable!!.owner!!.type) })
                val callable: CallableInfo = request.interceptedCallable!!
                val kind = callable.kind
                if (kind == CallableKindInfo.AFTER_CONSTRUCTOR) {
                    val constructedType: Type = request.interceptedCallable!!.owner!!.type!!
                    val typeKey = implementationName + ":" + constructedType
                    constructorRequests.computeIfAbsent(typeKey) { k: kotlin.String? -> ConstructorInterceptorSpec.Companion.of(implementationName, constructedType, interceptorType) }!!.getRequests()
                        .add(request)
                } else {
                    val name = NameUtil.interceptedJvmMethodName(callable)
                    val nameKey = implementationName + ":" + NameUtil.interceptedJvmMethodName(callable)
                    namedRequests.computeIfAbsent(nameKey) { k: kotlin.String? -> NamedCallableInterceptorSpec.Companion.of(implementationName, name, interceptorType) }!!.getRequests().add(request)
                }
            }
        })

        return CallInterceptorSpecs(namedRequests.values, constructorRequests.values)
    }
}
