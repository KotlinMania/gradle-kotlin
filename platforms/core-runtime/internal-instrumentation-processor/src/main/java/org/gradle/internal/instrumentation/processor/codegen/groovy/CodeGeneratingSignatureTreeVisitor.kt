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
import com.squareup.javapoet.TypeName
import org.gradle.internal.instrumentation.model.CallInterceptionRequest
import org.gradle.internal.instrumentation.model.CallableInfo
import org.gradle.internal.instrumentation.model.CallableKindInfo
import org.gradle.internal.instrumentation.processor.codegen.TypeUtils
import org.objectweb.asm.Type
import java.util.Arrays
import java.util.Stack
import java.util.function.Function
import java.util.stream.Stream

internal class CodeGeneratingSignatureTreeVisitor(private val result: CodeBlock.Builder) {
    private val paramVariablesStack = Stack<CodeBlock?>()

    /**
     * @param paramIndex index of the parameter in the signatures, -1 stands for the receiver
     */
    fun visit(current: SignatureTree, paramIndex: Int) {
        val leafInCurrent = current.leafOrNull
        if (leafInCurrent != null) {
            generateInvocationWhenArgsMatched(leafInCurrent, paramIndex)
        }
        val children = current.getChildrenByMatchEntry()
        if (!children.isEmpty()) {
            val hasParamMatchers = children.keys.any { it.kind == ParameterMatchEntry.Kind.PARAMETER }
            if (hasParamMatchers) { // is not the receiver or vararg
                result.beginControlFlow("if (invocation.getArgsCount() > \$L)", paramIndex)
                result.addStatement("Object arg$1L = invocation.getArgument($1L)", paramIndex)
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
                    generateVarargCheckAndInvocation(entry, child, paramIndex)
                }
            }
        }
    }

    private fun generateInvocationWhenArgsMatched(request: CallInterceptionRequest, argCount: Int) {
        result.beginControlFlow("if (invocation.getArgsCount() == \$L)", argCount)
        val argsCode = prepareInvocationArgs(request)
        emitInvocationCodeWithReturn(request, argsCode)
        result.endControlFlow()
    }

    private fun prepareInvocationArgs(request: CallInterceptionRequest): CodeBlock? {
        val hasKotlinDefaultMask = request.interceptedCallable!!.hasKotlinDefaultMaskParam()
        val callableInfo: CallableInfo = request.interceptedCallable!!
        val hasCallerClassName = callableInfo.hasCallerClassNameParam()
        val maybeZeroForKotlinDefault = if (hasKotlinDefaultMask) Stream.of<CodeBlock?>(CodeBlock.of("0")) else Stream.empty<CodeBlock?>()
        val maybeCallerClassName = if (hasCallerClassName) Stream.of<CodeBlock?>(CodeBlock.of("consumer")) else Stream.empty<CodeBlock?>()
        return Stream.of<Stream<CodeBlock?>?>(
            paramVariablesStack.stream(),
            maybeZeroForKotlinDefault,
            maybeCallerClassName
        ).flatMap<CodeBlock?>(Function.identity<Stream<CodeBlock?>?>()).collect(CodeBlock.joining(", "))
    }

    private fun emitInvocationCodeWithReturn(request: CallInterceptionRequest, argsCode: CodeBlock?) {
        val implementationOwner = TypeUtils.typeName(requireNotNull(request.implementationInfo!!.owner))
        val implementationName = request.implementationInfo!!.name
        if (request.interceptedCallable!!.kind === CallableKindInfo.AFTER_CONSTRUCTOR) {
            result.addStatement("$1T result = new $1T($2L)", TypeUtils.typeName(requireNotNull(request.interceptedCallable!!.owner!!.type)), paramVariablesStack.stream().collect(CodeBlock.joining(", ")))
            val interceptorArgs = CodeBlock.join(Arrays.asList<CodeBlock?>(CodeBlock.of("result"), argsCode), ", ")
            result.addStatement("\$T.\$L(\$L)", implementationOwner, implementationName, interceptorArgs)
            result.addStatement("return result")
        } else if (request.interceptedCallable!!.returnType!!.type!!.equals(Type.VOID_TYPE)) {
            result.addStatement("\$T.\$L(\$L)", implementationOwner, implementationName, argsCode)
            result.addStatement("return null")
        } else {
            result.addStatement("return \$T.\$L(\$L)", implementationOwner, implementationName, argsCode)
        }
    }

    private fun generateVarargCheckAndInvocation(entry: ParameterMatchEntry, child: SignatureTree, paramIndex: Int) {
        val entryParamType = TypeUtils.typeName(requireNotNull(entry.type))

        result.add("// Trying to match the vararg invocation\n")
        val varargVariable = CodeBlock.of("varargValues")
        result.addStatement("$1T[] $2L = new $1T[invocation.getArgsCount() - $3L]", entryParamType, varargVariable, paramIndex)
        val varargMatched = CodeBlock.of("varargMatched")
        result.addStatement("boolean \$L = true", varargMatched)
        result.beginControlFlow("for (int argIndex = $1L; argIndex < invocation.getArgsCount(); argIndex++)", paramIndex)

        val nextArg = CodeBlock.of("nextArg")
        result.addStatement("Object \$L = invocation.getArgument(argIndex)", nextArg)
        result.beginControlFlow("if ($1L == null || $1L instanceof $2T)", nextArg, entryParamType)
        if (entryParamType == TypeName.OBJECT) {
            result.addStatement("$1L[argIndex - $2L] = $3L", varargVariable, paramIndex, nextArg)
        } else {
            result.addStatement("$1L[argIndex - $2L] = ($3T) $4L", varargVariable, paramIndex, entryParamType, nextArg)
        }
        result.nextControlFlow("else")
        result.addStatement("\$L = false", varargMatched)
        result.addStatement("break")
        result.endControlFlow()

        result.endControlFlow()
        result.beginControlFlow("if (\$L)", varargMatched)
        paramVariablesStack.push(varargVariable)
        val request = requireNotNull(child.leafOrNull)
        emitInvocationCodeWithReturn(request, prepareInvocationArgs(request))
        paramVariablesStack.pop()
        result.endControlFlow()
    }

    private fun generateNormalCallChecksAndVisitSubtree(entry: ParameterMatchEntry, child: SignatureTree, paramIndex: Int) {
        val argExpr = if (entry.kind == ParameterMatchEntry.Kind.RECEIVER || entry.kind == ParameterMatchEntry.Kind.RECEIVER_AS_CLASS)
            CodeBlock.of("receiver")
        else
            CodeBlock.of("arg\$L", paramIndex)

        val childArgCount = paramIndex + 1
        val entryChildType = requireNotNull(TypeUtils.typeName(requireNotNull(entry.type)))
        val matchExpr = if (entry.kind == ParameterMatchEntry.Kind.RECEIVER_AS_CLASS) CodeBlock.of("\$L.equals(\$T.class)", argExpr, entryChildType) else  // Vararg fits here, too:
            CodeBlock.of("$1L == null || $1L instanceof $2T", argExpr, entryChildType.box())
        result.beginControlFlow("if (\$L)", matchExpr)
        var shouldPopParameter = false
        if (entry.kind != ParameterMatchEntry.Kind.RECEIVER_AS_CLASS) {
            shouldPopParameter = true
            val paramVariable = CodeBlock.of("\$LTyped", argExpr)
            if (entryChildType != TypeName.OBJECT) {
                result.addStatement("$2T $1L = ($2T) $3L", paramVariable, entryChildType, argExpr)
            } else {
                result.addStatement("$2T $1L = $3L", paramVariable, entryChildType, argExpr)
            }
            paramVariablesStack.push(paramVariable)
        }
        visit(child, childArgCount)
        if (shouldPopParameter) {
            paramVariablesStack.pop()
        }
        result.endControlFlow()
    }
}
