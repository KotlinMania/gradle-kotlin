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

import com.squareup.javapoet.ClassName
import org.gradle.internal.instrumentation.api.types.BytecodeInterceptorType
import org.gradle.internal.instrumentation.model.CallInterceptionRequest
import org.gradle.internal.instrumentation.processor.codegen.groovy.CallInterceptorSpecs.CallInterceptorSpec.ConstructorInterceptorSpec
import org.gradle.internal.instrumentation.processor.codegen.groovy.CallInterceptorSpecs.CallInterceptorSpec.NamedCallableInterceptorSpec
import org.gradle.internal.instrumentation.util.NameUtil
import org.objectweb.asm.Type

internal class CallInterceptorSpecs(namedRequests: MutableCollection<NamedCallableInterceptorSpec>, constructorRequests: MutableCollection<ConstructorInterceptorSpec>) {
    val namedRequests: MutableList<NamedCallableInterceptorSpec>
    val constructorRequests: MutableList<ConstructorInterceptorSpec>

    init {
        this.namedRequests = ArrayList(namedRequests)
        this.constructorRequests = ArrayList(constructorRequests)
    }

    internal interface CallInterceptorSpec {
        val className: String

        val fullClassName: String

        val interceptorType: BytecodeInterceptorType

        val requests: MutableList<CallInterceptionRequest>

        class NamedCallableInterceptorSpec private constructor(
            val name: String,
            override val className: String,
            override val fullClassName: String,
            override val requests: MutableList<CallInterceptionRequest>,
            override val interceptorType: BytecodeInterceptorType
        ) : CallInterceptorSpec {
            companion object {
                fun of(implementationName: String?, name: String?, interceptorType: BytecodeInterceptorType?): NamedCallableInterceptorSpec {
                    val className = NameUtil.capitalize(requireNotNull(name)) + "CallInterceptor"
                    val fullClassName = requireNotNull(implementationName) + "$" + className
                    return NamedCallableInterceptorSpec(name, className, fullClassName, ArrayList(), requireNotNull(interceptorType))
                }
            }
        }

        class ConstructorInterceptorSpec private constructor(
            val constructorType: Type,
            override val className: String,
            override val fullClassName: String,
            override val requests: MutableList<CallInterceptionRequest>,
            override val interceptorType: BytecodeInterceptorType
        ) : CallInterceptorSpec {
            companion object {
                fun of(implementationName: String?, constructedType: Type, interceptorType: BytecodeInterceptorType?): ConstructorInterceptorSpec {
                    val className = ClassName.bestGuess(constructedType.getClassName()).simpleName() + "ConstructorCallInterceptor"
                    val fullClassName = requireNotNull(implementationName) + "$" + className
                    return ConstructorInterceptorSpec(constructedType, className, fullClassName, ArrayList(), requireNotNull(interceptorType))
                }
            }
        }
    }
}
