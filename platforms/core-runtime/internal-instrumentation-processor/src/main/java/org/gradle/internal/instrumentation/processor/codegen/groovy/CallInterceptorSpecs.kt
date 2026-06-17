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

internal class CallInterceptorSpecs(namedRequests: MutableCollection<NamedCallableInterceptorSpec?>, constructorRequests: MutableCollection<ConstructorInterceptorSpec?>) {
    val namedRequests: MutableList<NamedCallableInterceptorSpec?>
    val constructorRequests: MutableList<ConstructorInterceptorSpec?>

    init {
        this.namedRequests = ArrayList<NamedCallableInterceptorSpec?>(namedRequests)
        this.constructorRequests = ArrayList<ConstructorInterceptorSpec?>(constructorRequests)
    }

    internal interface CallInterceptorSpec {
        val className: String?

        val fullClassName: String?

        val interceptorType: BytecodeInterceptorType?

        val requests: MutableList<CallInterceptionRequest?>?

        class NamedCallableInterceptorSpec private constructor(
            val name: String?,
            private val className: String?,
            private val fullClassName: String?,
            private val requests: MutableList<CallInterceptionRequest?>?,
            private val interceptorType: BytecodeInterceptorType?
        ) : CallInterceptorSpec {
            override fun getClassName(): String? {
                return className
            }

            override fun getFullClassName(): String? {
                return fullClassName
            }

            override fun getInterceptorType(): BytecodeInterceptorType? {
                return interceptorType
            }

            override fun getRequests(): MutableList<CallInterceptionRequest?>? {
                return requests
            }

            companion object {
                fun of(implementationName: String?, name: String?, interceptorType: BytecodeInterceptorType?): NamedCallableInterceptorSpec {
                    val className = NameUtil.capitalize(name) + "CallInterceptor"
                    val fullClassName = implementationName + "$" + className
                    return NamedCallableInterceptorSpec(name, className, fullClassName, ArrayList<CallInterceptionRequest?>(), interceptorType)
                }
            }
        }

        class ConstructorInterceptorSpec private constructor(
            val constructorType: Type?,
            private val className: String?,
            private val fullClassName: String?,
            private val requests: MutableList<CallInterceptionRequest?>?,
            private val interceptorType: BytecodeInterceptorType?
        ) : CallInterceptorSpec {
            override fun getClassName(): String? {
                return className
            }

            override fun getFullClassName(): String? {
                return fullClassName
            }

            override fun getInterceptorType(): BytecodeInterceptorType? {
                return interceptorType
            }

            override fun getRequests(): MutableList<CallInterceptionRequest?>? {
                return requests
            }

            companion object {
                fun of(implementationName: String?, constructedType: Type, interceptorType: BytecodeInterceptorType?): ConstructorInterceptorSpec {
                    val className = ClassName.bestGuess(constructedType.getClassName()).simpleName() + "ConstructorCallInterceptor"
                    val fullClassName = implementationName + "$" + className
                    return ConstructorInterceptorSpec(constructedType, className, fullClassName, ArrayList<CallInterceptionRequest?>(), interceptorType)
                }
            }
        }
    }
}
