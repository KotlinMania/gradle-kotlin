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

import com.squareup.javapoet.CodeBlock
import org.gradle.internal.instrumentation.model.CallInterceptionRequest
import org.gradle.internal.instrumentation.processor.codegen.JavadocUtils
import org.gradle.internal.instrumentation.processor.codegen.TypeUtils
import java.lang.reflect.Array

/**
 * Based on the [SignatureTree], generates a method body that checks the
 * `Class[] argumentClasses` parameter of the method for representing one of the
 * signatures in the signature tree.
 */
internal class MatchesSignatureGeneratingSignatureTreeVisitor(private val result: CodeBlock.Builder) {
    /**
     * @param paramIndex index of the parameter in the signatures, -1 stands for the receiver
     */
    fun visit(current: SignatureTree, paramIndex: Int) {
        val leafInCurrent = current.leafOrNull
        if (leafInCurrent != null) {
            returnTrueIfNoArgumentsLeft(paramIndex, leafInCurrent)
        }
        val children = current.getChildrenByMatchEntry()
        if (!children.isEmpty()) {
            val hasParamMatchers = children.keys.any { it.kind == ParameterMatchEntry.Kind.PARAMETER }
            if (hasParamMatchers) { // is not the receiver or vararg
                result.beginControlFlow("if (argumentClasses.length > \$L)", paramIndex)
                result.addStatement("Class<?> arg$1L = argumentClasses[$1L]", paramIndex)
            }
            // Visit non-vararg invocations first and varargs after:
            children.forEach { (entry: ParameterMatchEntry, child: SignatureTree) ->
                if (entry.kind != ParameterMatchEntry.Kind.VARARG) {
                    generateNormalCallChecksAndVisitSubtree(entry, child, paramIndex)
                }
            }
            if (hasParamMatchers) {
                result.endControlFlow()
            }
            children.forEach { (entry: ParameterMatchEntry, child: SignatureTree) ->
                if (entry.kind == ParameterMatchEntry.Kind.VARARG) {
                    generateVarargCheck(entry, child, paramIndex)
                }
            }
        }
    }

    private fun generateNormalCallChecksAndVisitSubtree(entry: ParameterMatchEntry, child: SignatureTree, paramIndex: Int) {
        val argExpr = if (entry.kind == ParameterMatchEntry.Kind.RECEIVER || entry.kind == ParameterMatchEntry.Kind.RECEIVER_AS_CLASS)
            CodeBlock.of("receiverClass")
        else
            CodeBlock.of("arg\$L", paramIndex)

        val childArgCount = paramIndex + 1
        val entryChildType = requireNotNull(TypeUtils.typeName(requireNotNull(entry.type)))
        val matchExpr = if (entry.kind == ParameterMatchEntry.Kind.RECEIVER_AS_CLASS) CodeBlock.of(
            "isStatic && \$T.class.isAssignableFrom(\$L)",
            entryChildType.box(),
            argExpr
        ) else if (entry.kind == ParameterMatchEntry.Kind.RECEIVER) CodeBlock.of(
            "!isStatic && ($2L == null || $1T.class.isAssignableFrom($2L))",
            entryChildType.box(),
            argExpr
        ) else CodeBlock.of("$2L == null || $1T.class.isAssignableFrom($2L)", entryChildType.box(), argExpr)
        // Vararg fits here, too:
        result.beginControlFlow("if (\$L)", matchExpr)
        visit(child, childArgCount)
        result.endControlFlow()
    }

    private fun generateVarargCheck(entry: ParameterMatchEntry, child: SignatureTree, paramIndex: Int) {
        val entryParamType = TypeUtils.typeName(requireNotNull(entry.type))
        val childRequest = requireNotNull(child.leafOrNull) { "vararg parameter must be the last in the signature" }

        result.add("// Trying to match the vararg invocation\n")
        val varargMatched = CodeBlock.of("varargMatched")

        val matchArgs: CodeBlock = Companion.argClassesExpression(childRequest)

        result.beginControlFlow(
            "if (argumentClasses.length == $1L && argumentClasses[$2L] != null && $3T[].class.isAssignableFrom($4T.newInstance(argumentClasses[$2L], 0).getClass()))",
            paramIndex + 1, paramIndex, entryParamType, Array::class.java
        )
        result.add("/** Matched \$L */\n", JavadocUtils.interceptedCallableLink(childRequest))
        result.addStatement("return new \$T(true, \$L)", InterceptGroovyCallsGenerator.Companion.SIGNATURE_AWARE_CALL_INTERCEPTOR_SIGNATURE_MATCH, matchArgs)
        result.endControlFlow()

        result.addStatement("boolean \$L = true", varargMatched)
        result.beginControlFlow("for (int argIndex = $1L; argIndex < argumentClasses.length; argIndex++)", paramIndex)
        val nextArg = CodeBlock.of("nextArg")
        result.addStatement("Class<?> \$L = argumentClasses[argIndex]", nextArg)
        result.beginControlFlow("if ($2L != null && !$1T.class.isAssignableFrom($2L))", entryParamType, nextArg)
        result.addStatement("\$L = false", varargMatched)
        result.addStatement("break")
        result.endControlFlow()
        result.endControlFlow()
        result.beginControlFlow("if (\$L)", varargMatched)
        result.add("/** Matched \$L */\n", JavadocUtils.interceptedCallableLink(childRequest))
        result.addStatement("return new \$T(true, \$L)", InterceptGroovyCallsGenerator.Companion.SIGNATURE_AWARE_CALL_INTERCEPTOR_SIGNATURE_MATCH, matchArgs)
        result.endControlFlow()
    }

    private fun returnTrueIfNoArgumentsLeft(argCount: Int, leafInCurrent: CallInterceptionRequest) {
        val argClasses: CodeBlock = argClassesExpression(leafInCurrent)

        result.beginControlFlow("if (argumentClasses.length == \$L)", argCount)
        result.add("/** Matched \$L */\n", JavadocUtils.interceptedCallableLink(leafInCurrent))
        result.addStatement("return new \$T(false, \$L)", InterceptGroovyCallsGenerator.Companion.SIGNATURE_AWARE_CALL_INTERCEPTOR_SIGNATURE_MATCH, argClasses)
        result.endControlFlow()
    }

    companion object {
        private fun argClassesExpression(leafInCurrent: CallInterceptionRequest): CodeBlock {
            return requireNotNull(leafInCurrent.interceptedCallable).parameters!!
                .stream()
                .filter { it.kind?.isSourceParameter == true }
                .map { CodeBlock.of("\$T.class", TypeUtils.typeName(requireNotNull(it.parameterType))) }
                .collect(CodeBlock.joining(", ", "new Class<?>[] {", "}"))
        }
    }
}
