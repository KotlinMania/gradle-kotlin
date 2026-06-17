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
package org.gradle.internal.instrumentation.processor.codegen

import com.squareup.javapoet.ClassName
import com.squareup.javapoet.CodeBlock
import org.gradle.internal.instrumentation.model.CallInterceptionRequest
import org.gradle.internal.instrumentation.model.CallableInfo
import org.gradle.internal.instrumentation.model.CallableKindInfo
import org.gradle.internal.instrumentation.model.ParameterInfo
import org.gradle.internal.instrumentation.model.ParameterKindInfo
import org.gradle.internal.instrumentation.util.NameUtil
import java.util.function.Consumer
import java.util.stream.Collectors

object JavadocUtils {
    fun callableKindForJavadoc(request: CallInterceptionRequest): String? {
        val interceptedCallable: CallableInfo = request.interceptedCallable!!
        val kind = interceptedCallable.kind
        return if (kind == CallableKindInfo.STATIC_METHOD) "static method" else if (kind == CallableKindInfo.INSTANCE_METHOD) "instance method" else if (kind == CallableKindInfo.AFTER_CONSTRUCTOR) "constructor (getting notified after it)" else if (kind == CallableKindInfo.GROOVY_PROPERTY_GETTER) "Groovy property getter" else if (kind == CallableKindInfo.GROOVY_PROPERTY_SETTER) "Groovy property setter" else null
    }

    fun interceptedCallableLink(request: CallInterceptionRequest): CodeBlock {
        val result = CodeBlock.builder()
        val interceptedCallable: CallableInfo = request.interceptedCallable!!
        val className = ClassName.bestGuess(interceptedCallable.owner!!.type!!.getClassName())
        val callableNameForDocComment = if (interceptedCallable.kind === CallableKindInfo.AFTER_CONSTRUCTOR) className.simpleName() else interceptedCallable.callableName
        val params: MutableList<ParameterInfo?> = request.interceptedCallable!!.parameters!!
        val methodParameters = params.stream().filter { parameter: ParameterInfo? -> parameter!!.kind.isSourceParameter() }.collect(Collectors.toList())
        result.add("{@link \$L#\$L", className, callableNameForDocComment)
        if (interceptedCallable.kind !== CallableKindInfo.GROOVY_PROPERTY_GETTER && interceptedCallable.kind !== CallableKindInfo.GROOVY_PROPERTY_SETTER) {
            result.add("(")
            methodParameters.forEach(Consumer { parameter: ParameterInfo? ->
                result.add("\$L", JavadocUtils.parameterTypeForJavadoc(parameter!!, true))
                if (parameter !== methodParameters.get(methodParameters.size - 1)) {
                    result.add(", ")
                }
            })
            result.add(")")
        }
        result.add("}")
        return result.build()
    }

    fun interceptorImplementationLink(request: CallInterceptionRequest): CodeBlock {
        val result = CodeBlock.builder()
        val params: MutableList<ParameterInfo?> = request.interceptedCallable!!.parameters!!
        result.add("{@link \$T#\$L(", NameUtil.getClassName(request.implementationInfo!!.owner.getClassName()), request.implementationInfo!!.name)
        params.forEach(Consumer { parameter: ParameterInfo? ->
            result.add("\$L", JavadocUtils.parameterTypeForJavadoc(parameter!!, false))
            if (parameter !== params.get(params.size - 1)) {
                result.add(", ")
            }
        })
        result.add(")}")
        return result.build()
    }

    private fun parameterTypeForJavadoc(parameterInfo: ParameterInfo, renderVararg: Boolean): CodeBlock? {
        if (parameterInfo.kind === ParameterKindInfo.VARARG_METHOD_PARAMETER && renderVararg) {
            return CodeBlock.of("\$T...", TypeUtils.typeName(parameterInfo.parameterType.getElementType()))
        }
        return CodeBlock.of("\$T", TypeUtils.typeName(parameterInfo.parameterType))
    }
}
