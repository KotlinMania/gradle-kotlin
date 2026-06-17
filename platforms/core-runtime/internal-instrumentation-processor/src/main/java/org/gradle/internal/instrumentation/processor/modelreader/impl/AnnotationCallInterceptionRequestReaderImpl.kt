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

import org.gradle.internal.Cast
import org.gradle.internal.instrumentation.api.annotations.CallableDefinition
import org.gradle.internal.instrumentation.api.annotations.CallableKind
import org.gradle.internal.instrumentation.api.annotations.InterceptInherited
import org.gradle.internal.instrumentation.api.annotations.ParameterKind
import org.gradle.internal.instrumentation.model.CallInterceptionRequestImpl
import org.gradle.internal.instrumentation.model.CallableInfo
import org.gradle.internal.instrumentation.model.CallableInfoImpl
import org.gradle.internal.instrumentation.model.CallableKindInfo
import org.gradle.internal.instrumentation.model.CallableOwnerInfo
import org.gradle.internal.instrumentation.model.CallableReturnTypeInfo
import org.gradle.internal.instrumentation.model.ImplementationInfoImpl
import org.gradle.internal.instrumentation.model.ParameterInfo
import org.gradle.internal.instrumentation.model.ParameterInfoImpl
import org.gradle.internal.instrumentation.model.ParameterKindInfo
import org.gradle.internal.instrumentation.model.RequestExtra
import org.gradle.internal.instrumentation.model.RequestExtra.OriginatingElement
import org.gradle.internal.instrumentation.processor.extensibility.AnnotatedMethodReaderExtension
import org.gradle.internal.instrumentation.processor.modelreader.api.CallInterceptionRequestReader
import org.gradle.internal.instrumentation.processor.modelreader.api.CallInterceptionRequestReader.ReadRequestContext
import org.gradle.internal.instrumentation.processor.modelreader.api.CallInterceptionRequestReader.Result.InvalidRequest
import org.objectweb.asm.Type
import java.util.Arrays
import java.util.Objects
import java.util.function.Supplier
import java.util.stream.Collectors
import java.util.stream.Stream
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.VariableElement
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror

class AnnotationCallInterceptionRequestReaderImpl : AnnotatedMethodReaderExtension {
    override fun readRequest(input: ExecutableElement, context: ReadRequestContext?): MutableCollection<CallInterceptionRequestReader.Result?>? {
        if (input.getKind() != ElementKind.METHOD) {
            return mutableListOf<CallInterceptionRequestReader.Result?>()
        }

        if (!input.getModifiers().containsAll(Arrays.asList<Modifier?>(Modifier.STATIC, Modifier.PUBLIC))) {
            return mutableListOf<CallInterceptionRequestReader.Result?>()
        }

        try {
            val callableInfo: CallableInfo = extractCallableInfo(input)
            val implementationInfo: ImplementationInfoImpl = extractImplementationInfo(input)
            val requestExtras = mutableListOf<RequestExtra?>(OriginatingElement(input))
            return mutableListOf<CallInterceptionRequestReader.Result?>(CallInterceptionRequestReader.Result.Success(CallInterceptionRequestImpl(callableInfo, implementationInfo, requestExtras)))
        } catch (e: Failure) {
            return mutableListOf<CallInterceptionRequestReader.Result?>(InvalidRequest(e.reason))
        }
    }

    private class Failure(val reason: String?) : RuntimeException()

    companion object {
        private fun extractCallableInfo(methodElement: ExecutableElement): CallableInfo {
            val kindInfo: CallableKindInfo = extractCallableKind(methodElement)
            val ownerType: Type = extractOwnerClass(methodElement)
            val interceptInherited: Boolean = isInterceptInherited(methodElement)
            val owner = CallableOwnerInfo(ownerType, interceptInherited)
            val callableName: String = getCallableName(methodElement, kindInfo)
            val returnType = CallableReturnTypeInfo(TypeUtils.extractReturnType(methodElement))
            val parameterInfos: MutableList<ParameterInfo?> = extractParameters(methodElement)
            return CallableInfoImpl(kindInfo, owner, callableName, returnType, parameterInfos)
        }

        private fun extractImplementationInfo(input: ExecutableElement): ImplementationInfoImpl {
            val implementationOwner = TypeUtils.extractType(input.getEnclosingElement().asType())
            val implementationName: String? = input.getSimpleName().toString()
            val implementationDescriptor = TypeUtils.extractMethodDescriptor(input)
            return ImplementationInfoImpl(implementationOwner, implementationName, implementationDescriptor)
        }

        private fun getCallableName(methodElement: ExecutableElement, kindInfo: CallableKindInfo?): String {
            val nameAnnotation = methodElement.getAnnotation<CallableDefinition.Name?>(CallableDefinition.Name::class.java)
            val nameFromPattern: String? = callableNameFromNamingConvention(methodElement.getSimpleName().toString())
            if (kindInfo == CallableKindInfo.AFTER_CONSTRUCTOR) {
                if (nameAnnotation != null) {
                    throw Failure("@" + CallableKind.AfterConstructor::class.java.getSimpleName() + " cannot be used with @" + CallableDefinition.Name::class.java.getSimpleName())
                }
                if (nameFromPattern != null) {
                    throw Failure("Constructor interceptors cannot follow the 'intercept_*' name pattern")
                }
                return "<init>"
            } else {
                if (nameAnnotation == null) {
                    if (nameFromPattern == null) {
                        throw Failure("Expected the interceptor method to be annotated with @" + CallableDefinition.Name::class.java.getSimpleName() + " or to have the 'intercept_*' pattern in the name")
                    }
                    return nameFromPattern
                } else {
                    if (nameFromPattern != null) {
                        throw Failure("@" + CallableDefinition.Name::class.java.getSimpleName() + " cannot be used with method names following the 'intercept_*' pattern")
                    }
                }
                return nameAnnotation.value
            }
        }

        private fun callableNameFromNamingConvention(methodName: String): String? {
            if (!isInterceptPatternName(methodName)) {
                return null
            }
            return methodName.replace(INTERCEPT_PREFIX, "")
        }

        private const val INTERCEPT_PREFIX = "intercept_"

        private fun isInterceptPatternName(methodName: String): Boolean {
            return methodName.startsWith(INTERCEPT_PREFIX)
        }

        private fun extractCallableKind(methodElement: ExecutableElement): CallableKindInfo {
            val kindAnnotations: MutableList<Annotation?> = Stream.of<Class<out Annotation?>?>(*CALLABLE_KIND_ANNOTATION_CLASSES)
                .map { annotationType: Class<A?>? -> methodElement.getAnnotation(annotationType) }
                .filter { obj: Any? -> Objects.nonNull(obj) }
                .collect(Collectors.toList())

            if (kindAnnotations.size > 1) {
                throw Failure("More than one callable kind annotations present: " + kindAnnotations)
            } else if (kindAnnotations.size == 0) {
                throw Failure(
                    "No callable kind annotation specified, expected one of " + Arrays.stream<Class<out Annotation?>?>(CALLABLE_KIND_ANNOTATION_CLASSES)
                        .map<String?> { obj: Class<out Annotation?>? -> obj!!.getSimpleName() }.collect(
                            Collectors.joining(", ")
                        )
                )
            } else {
                return CallableKindInfo.fromAnnotation(kindAnnotations.get(0))
            }
        }

        private fun isInterceptInherited(methodElement: ExecutableElement): Boolean {
            return methodElement.getAnnotation<InterceptInherited?>(InterceptInherited::class.java) != null
        }

        private fun extractParameters(methodElement: ExecutableElement): MutableList<ParameterInfo?> {
            val list: MutableList<ParameterInfo?> = ArrayList<ParameterInfo?>()
            val parameters = methodElement.getParameters()
            for (i in parameters.indices) {
                val variableElement: VariableElement = parameters.get(i)
                val isVararg = methodElement.isVarArgs() && i == parameters.size - 1
                val parameterInfo: ParameterInfo = extractParameter(variableElement, isVararg)
                list.add(parameterInfo)
            }
            return list
        }

        private fun extractParameter(parameterElement: VariableElement, isVararg: Boolean): ParameterInfo {
            val parameterType = TypeUtils.extractType(parameterElement.asType())
            val parameterKindInfo: ParameterKindInfo = extractParameterKind(parameterElement, isVararg)

            if (parameterKindInfo == ParameterKindInfo.VARARG_METHOD_PARAMETER) {
                if (parameterType.getSort() != Type.ARRAY) {
                    throw Failure("a @" + ParameterKind.VarargParameter::class.java.getSimpleName() + " parameter must have an array type")
                }
            }

            return ParameterInfoImpl(parameterElement.getSimpleName().toString(), parameterType, parameterKindInfo)
        }

        private fun extractParameterKind(parameterElement: VariableElement, isVararg: Boolean): ParameterKindInfo {
            val kindAnnotations: MutableList<Annotation?> = Stream.of<Class<out Annotation?>?>(*PARAMETER_KIND_ANNOTATION_CLASSES)
                .map { annotationType: Class<A?>? -> parameterElement.getAnnotation(annotationType) }
                .filter { obj: Any? -> Objects.nonNull(obj) }
                .collect(Collectors.toList())

            if (kindAnnotations.size > 1) {
                throw Failure("More than one parameter kind annotations present: " + kindAnnotations)
            } else if (kindAnnotations.size == 0) {
                return if (isVararg) ParameterKindInfo.VARARG_METHOD_PARAMETER else ParameterKindInfo.METHOD_PARAMETER
            } else {
                val parameterKindInfo = ParameterKindInfo.fromAnnotation(kindAnnotations.get(0))
                if (isVararg && parameterKindInfo != ParameterKindInfo.VARARG_METHOD_PARAMETER) {
                    throw Failure("a vararg parameter can only be @" + ParameterKind.VarargParameter::class.java.getSimpleName() + " (maybe implicitly)")
                }
                return parameterKindInfo
            }
        }

        private fun extractOwnerClass(executableElement: ExecutableElement): Type {
            val maybeStaticMethod = AnnotationUtils.findAnnotationMirror(executableElement, CallableKind.StaticMethod::class.java)
            val receivers = executableElement.getParameters().stream()
                .filter { it: VariableElement -> it.getAnnotation<ParameterKind.Receiver?>(ParameterKind.Receiver::class.java) != null }
                .collect(Collectors.toList())

            if (maybeStaticMethod.isPresent()) {
                if (receivers.size > 0) {
                    throw Failure("Static method interceptors should not declare @" + ParameterKind.Receiver::class.java.getSimpleName() + " parameters")
                }
                val staticMethodOwner =
                    AnnotationUtils.findAnnotationValue(maybeStaticMethod.get(), "ofClass").orElseThrow<IllegalStateException?>(Supplier { IllegalStateException("missing annotation value") })
                        .getValue() as TypeMirror
                return TypeUtils.extractType(staticMethodOwner)
            }

            if (receivers.size == 0) {
                throw Failure("Expected owner defined as a @" + ParameterKind.Receiver::class.java.getSimpleName() + " parameter or @" + CallableKind.StaticMethod::class.java.getSimpleName() + " annotation")
            }
            if (receivers.size > 1) {
                throw Failure("Only one parameter can be annotated with @" + ParameterKind.Receiver::class.java.getSimpleName())
            }
            val receiver = receivers.get(0)
            val receiverType = receiver.asType()
            if (receiverType.getKind() != TypeKind.DECLARED) {
                throw Failure("Receiver should be a class or interface, got " + receiverType)
            }
            return TypeUtils.extractType(receiverType)
        }

        private val CALLABLE_KIND_ANNOTATION_CLASSES: Array<Class<out Annotation?>?> = Cast.uncheckedNonnullCast<Array<Class<out Annotation?>?>?>(
            arrayOf<Class<*>>(
                CallableKind.InstanceMethod::class.java,
                CallableKind.StaticMethod::class.java,
                CallableKind.AfterConstructor::class.java,
                CallableKind.GroovyPropertyGetter::class.java,
                CallableKind.GroovyPropertySetter::class.java
            )
        )

        private val PARAMETER_KIND_ANNOTATION_CLASSES: Array<Class<out Annotation?>?> = Cast.uncheckedNonnullCast<Array<Class<out Annotation?>?>?>(
            arrayOf<Class<*>>(
                ParameterKind.Receiver::class.java,
                ParameterKind.CallerClassName::class.java,
                ParameterKind.KotlinDefaultMask::class.java,
                ParameterKind.VarargParameter::class.java,
                ParameterKind.InjectVisitorContext::class.java
            )
        )
    }
}
