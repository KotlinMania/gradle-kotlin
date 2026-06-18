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

import com.squareup.javapoet.ArrayTypeName
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.CodeBlock
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.TypeSpec
import com.squareup.javapoet.WildcardTypeName
import org.gradle.internal.instrumentation.api.groovybytecode.AbstractCallInterceptor
import org.gradle.internal.instrumentation.api.groovybytecode.FilterableCallInterceptor
import org.gradle.internal.instrumentation.api.groovybytecode.InterceptScope
import org.gradle.internal.instrumentation.api.groovybytecode.Invocation
import org.gradle.internal.instrumentation.api.groovybytecode.PropertyAwareCallInterceptor
import org.gradle.internal.instrumentation.api.groovybytecode.SignatureAwareCallInterceptor
import org.gradle.internal.instrumentation.api.types.BytecodeInterceptorType
import org.gradle.internal.instrumentation.model.CallInterceptionRequest
import org.gradle.internal.instrumentation.model.CallableInfo
import org.gradle.internal.instrumentation.model.CallableKindInfo
import org.gradle.internal.instrumentation.model.ParameterKindInfo
import org.gradle.internal.instrumentation.model.RequestExtra
import org.gradle.internal.instrumentation.processor.codegen.CodeGenUtils
import org.gradle.internal.instrumentation.processor.codegen.GradleReferencedType
import org.gradle.internal.instrumentation.processor.codegen.HasFailures.FailureInfo
import org.gradle.internal.instrumentation.processor.codegen.JavadocUtils
import org.gradle.internal.instrumentation.processor.codegen.RequestGroupingInstrumentationClassSourceGenerator
import org.gradle.internal.instrumentation.processor.codegen.TypeUtils
import org.gradle.internal.instrumentation.processor.codegen.groovy.CallInterceptorSpecs.CallInterceptorSpec.ConstructorInterceptorSpec
import org.gradle.internal.instrumentation.processor.codegen.groovy.CallInterceptorSpecs.CallInterceptorSpec.NamedCallableInterceptorSpec
import org.gradle.internal.instrumentation.util.NameUtil
import org.objectweb.asm.Type
import java.util.function.BinaryOperator
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Collectors
import javax.lang.model.element.Modifier

class InterceptGroovyCallsGenerator : RequestGroupingInstrumentationClassSourceGenerator() {
    override fun classNameForRequest(request: CallInterceptionRequest?): String? {
        return request?.requestExtras
            ?.getByType(RequestExtra.InterceptGroovyCalls::class.java)
            ?.orElse(null)
            ?.implementationClassName
    }

    override fun classContentForClass(
        className: String?,
        requestsClassGroup: MutableList<CallInterceptionRequest?>?,
        onProcessedRequest: Consumer<in CallInterceptionRequest?>?,
        onFailure: Consumer<in FailureInfo?>?
    ): Consumer<TypeSpec.Builder?> {
        val interceptorTypeSpecs: MutableList<TypeSpec?> = generateInterceptorClasses(requireNotNull(requestsClassGroup), requireNotNull(onFailure))

        return Consumer { builder: TypeSpec.Builder? ->
            builder!!
                .addAnnotation(GradleReferencedType.GENERATED_ANNOTATION.asClassName())
                .addModifiers(Modifier.PUBLIC)
                .addTypes(interceptorTypeSpecs)
        }
    }

    companion object {
        private fun generateInterceptorClasses(interceptionRequests: MutableCollection<CallInterceptionRequest?>, onFailure: Consumer<in FailureInfo?>): MutableList<TypeSpec?> {
            val result: MutableList<TypeSpec?> = ArrayList<TypeSpec?>(interceptionRequests.size / 2)

            val callInterceptorSpecs = GroovyClassGeneratorUtils.groupRequests(interceptionRequests)
            callInterceptorSpecs.namedRequests.stream()
                .peek { spec: NamedCallableInterceptorSpec -> validateRequests(spec.requests, onFailure) }
                .map<TypeSpec?> { spec: NamedCallableInterceptorSpec -> Companion.generateNamedCallableInterceptorClass(spec) }
                .collect(Collectors.toCollection(Supplier { result }))

            callInterceptorSpecs.constructorRequests.stream()
                .peek { spec: ConstructorInterceptorSpec -> validateRequests(spec.requests, onFailure) }
                .map<TypeSpec?> { spec: ConstructorInterceptorSpec -> Companion.generateConstructorInterceptorClass(spec) }
                .collect(Collectors.toCollection(Supplier { result }))

            return result
        }

        private fun generateNamedCallableInterceptorClass(spec: NamedCallableInterceptorSpec): TypeSpec {
            return Companion.generateInterceptorClass(spec.className, spec.interceptorType, namedCallableScopesArgs(spec.name, spec.requests)!!, spec.requests).build()
        }

        private fun generateConstructorInterceptorClass(spec: ConstructorInterceptorSpec): TypeSpec {
            return Companion.generateInterceptorClass(spec.className, spec.interceptorType, constructorScopeArg(TypeUtils.typeName(spec.constructorType))!!, spec.requests).build()
        }

        private fun signatureTreeFromRequests(requests: MutableCollection<CallInterceptionRequest>): SignatureTree {
            val result = SignatureTree()
            requests.forEach(Consumer { request: CallInterceptionRequest -> result.add(request) })
            return result
        }

        private fun generateInterceptorClass(className: String, interceptorType: BytecodeInterceptorType, scopes: CodeBlock, requests: MutableList<CallInterceptionRequest>): TypeSpec.Builder {
            val generatedClass: TypeSpec.Builder = TypeSpec.classBuilder(className)
                .addAnnotation(GradleReferencedType.GENERATED_ANNOTATION.asClassName())
                .superclass(CALL_INTERCEPTOR_CLASS)
                .addSuperinterface(SIGNATURE_AWARE_CALL_INTERCEPTOR_CLASS)
                .addSuperinterface(FILTERABLE_CALL_INTERCEPTOR)
                .addSuperinterface(ClassName.get(interceptorType.interceptorMarkerInterface))
                .addJavadoc(interceptorClassJavadoc(requests))
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)

            val constructor = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC).addStatement("super(\$L)", scopes).build()
            generatedClass.addMethod(constructor)

            val signatureTree: SignatureTree = signatureTreeFromRequests(requests)

            val interceptMethod: MethodSpec = MethodSpec.methodBuilder("intercept")
                .addAnnotation(Override::class.java)
                .addAnnotation(CodeGenUtils.SUPPRESS_UNCHECKED_AND_RAWTYPES)
                .addModifiers(Modifier.PUBLIC)
                .returns(Any::class.java)
                .addParameter(INVOCATION_CLASS, "invocation")
                .addParameter(String::class.java, "consumer")
                .addException(Throwable::class.java)
                .addCode(generateCodeFromInterceptorSignatureTree(signatureTree))
                .build()

            val classWildcard = ParameterizedTypeName.get(ClassName.get(Class::class.java), WildcardTypeName.subtypeOf(Any::class.java))
            val matchesSignature: MethodSpec = MethodSpec.methodBuilder("matchesMethodSignature")
                .addAnnotation(Override::class.java)
                .addModifiers(Modifier.PUBLIC)
                .returns(SIGNATURE_AWARE_CALL_INTERCEPTOR_SIGNATURE_MATCH)
                .addParameter(classWildcard, "receiverClass")
                .addParameter(ArrayTypeName.of(classWildcard), "argumentClasses")
                .addParameter(Boolean::class.javaPrimitiveType, "isStatic")
                .addCode(generateMatchesSignatureCodeFromInterceptorSignatureTree(signatureTree))
                .build()

            generatedClass.addMethod(interceptMethod)
            generatedClass.addMethod(matchesSignature)

            if (hasGroovyPropertyRequests(requests)) {
                generatedClass.addSuperinterface(PROPERTY_AWARE_CALL_INTERCEPTOR_CLASS)
                val matchesProperty: MethodSpec = MethodSpec.methodBuilder("matchesProperty")
                    .addAnnotation(Override::class.java)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(classWildcard)
                    .addParameter(classWildcard, "receiverClass")
                    .addCode(generateMatchesPropertyCode(requests))
                    .build()
                generatedClass.addMethod(matchesProperty)
            }

            return generatedClass
        }

        private fun validateRequests(requests: MutableList<CallInterceptionRequest>, onFailure: Consumer<in FailureInfo?>) {
            for (request in requests) {
                val callableInfo: CallableInfo = request.interceptedCallable!!
                if (callableInfo.hasInjectVisitorContextParam()) {
                    onFailure.accept(FailureInfo(request, "Parameter with @InjectVisitorContext annotation is not supported for Groovy interception."))
                }
            }
        }

        private fun hasGroovyPropertyRequests(requests: MutableList<CallInterceptionRequest>): Boolean {
            return requests.stream()
                .anyMatch { it: CallInterceptionRequest? -> it!!.interceptedCallable!!.kind === CallableKindInfo.GROOVY_PROPERTY_GETTER || it.interceptedCallable!!.kind === CallableKindInfo.GROOVY_PROPERTY_SETTER }
        }

        private fun interceptorClassJavadoc(requests: MutableCollection<CallInterceptionRequest>): CodeBlock? {
            val result: MutableList<CodeBlock?> = ArrayList<CodeBlock?>()
            result.add(CodeBlock.of("Intercepts the following declarations:<ul>"))
            requests.stream().map<CodeBlock?> { request: CallInterceptionRequest ->
                CodeBlock.of(
                    "<li> \$L \$L\n     with \$L",
                    JavadocUtils.callableKindForJavadoc(request),
                    JavadocUtils.interceptedCallableLink(request),
                    JavadocUtils.interceptorImplementationLink(request)
                )
            }.collect(
                Collectors.toCollection(Supplier { result })
            )
            result.add(CodeBlock.of("</ul>"))
            return result.stream().collect(CodeBlock.joining("\n\n"))
        }

        private fun constructorScopeArg(constructedType: TypeName?): CodeBlock? {
            return CodeBlock.of("$1T.constructorsOf($2T.class)", INTERCEPTED_SCOPE_CLASS, constructedType)
        }

        private fun namedCallableScopesArgs(name: String?, requests: MutableList<CallInterceptionRequest>): CodeBlock? {
            val scopeExpressions: MutableList<CodeBlock?> = ArrayList<CodeBlock?>()

            requests.stream().filter { it: CallInterceptionRequest -> it.interceptedCallable!!.kind === CallableKindInfo.GROOVY_PROPERTY_GETTER }.forEach { request: CallInterceptionRequest ->
                val propertyName = requireNotNull(request.interceptedCallable!!.callableName)
                val getterName = NameUtil.getterName(propertyName, requireNotNull(request.interceptedCallable!!.returnType!!.type))
                scopeExpressions.add(CodeBlock.of("$1T.readsOfPropertiesNamed($2S)", INTERCEPTED_SCOPE_CLASS, propertyName))
                scopeExpressions.add(CodeBlock.of("$1T.methodsNamed($2S)", INTERCEPTED_SCOPE_CLASS, getterName))
            }
            requests.stream().filter { it: CallInterceptionRequest -> it.interceptedCallable!!.kind === CallableKindInfo.GROOVY_PROPERTY_SETTER }.forEach { request: CallInterceptionRequest ->
                val propertyName = requireNotNull(request.interceptedCallable!!.callableName)
                val setterName = NameUtil.setterName(propertyName)
                scopeExpressions.add(CodeBlock.of("$1T.writesOfPropertiesNamed($2S)", INTERCEPTED_SCOPE_CLASS, propertyName))
                scopeExpressions.add(CodeBlock.of("$1T.methodsNamed($2S)", INTERCEPTED_SCOPE_CLASS, setterName))
            }

            val callableKinds: MutableList<CallableKindInfo?> = requests.stream().map<CallableKindInfo?> { it: CallInterceptionRequest -> it.interceptedCallable!!.kind }.distinct().collect(Collectors.toList())
            if (callableKinds.contains(CallableKindInfo.STATIC_METHOD) || callableKinds.contains(CallableKindInfo.INSTANCE_METHOD)) {
                scopeExpressions.add(CodeBlock.of("\$T.methodsNamed(\$S)", INTERCEPTED_SCOPE_CLASS, name))
            }
            return scopeExpressions.stream().distinct().collect(CodeBlock.joining(", "))
        }

        private fun generateCodeFromInterceptorSignatureTree(tree: SignatureTree): CodeBlock {
            val result = CodeBlock.builder()
            result.addStatement("\$T receiver = invocation.getReceiver()", Any::class.java)

            CodeGeneratingSignatureTreeVisitor(result).visit(tree, -1)

            result.addStatement("return invocation.callNext()")
            return result.build()
        }

        private fun generateMatchesSignatureCodeFromInterceptorSignatureTree(tree: SignatureTree): CodeBlock {
            val result = CodeBlock.builder()
            MatchesSignatureGeneratingSignatureTreeVisitor(result).visit(tree, -1)
            result.addStatement("return null")
            return result.build()
        }

        private fun generateMatchesPropertyCode(requests: MutableCollection<CallInterceptionRequest>): CodeBlock {
            val result = CodeBlock.builder()
            val propertyTypeByReceiverType: java.util.LinkedHashMap<Type?, Type?> = requests.stream()
                .filter { request: CallInterceptionRequest? -> request!!.interceptedCallable!!.kind === CallableKindInfo.GROOVY_PROPERTY_GETTER || request.interceptedCallable!!.kind === CallableKindInfo.GROOVY_PROPERTY_SETTER }
                .collect(
                    Collectors.toMap(
                        Function { request: CallInterceptionRequest? -> Companion.propertyReceiverType(request!!) },
                        Function { request: CallInterceptionRequest? -> Companion.propertyValueType(request!!) },
                        BinaryOperator { a: Type?, b: Type? ->
                            require(a == b) {
                                "multiple requests to intercept a property on a single receiver type " +
                                        "with different property types: " + a + ", " + b
                            }
                            a
                        },
                        Supplier { LinkedHashMap() }
                    )
                )
            propertyTypeByReceiverType.forEach { (receiverType: Type?, propertyType: Type?) ->
                result.beginControlFlow("if (\$T.class.isAssignableFrom(receiverClass))", requireNotNull(TypeUtils.typeName(requireNotNull(receiverType))).box())
                result.addStatement("return \$T.class", requireNotNull(TypeUtils.typeName(requireNotNull(propertyType))).box())
                result.endControlFlow()
            }
            result.addStatement("return null")
            return result.build()
        }

        private fun propertyValueType(request: CallInterceptionRequest): Type {
            if (request.interceptedCallable!!.kind === CallableKindInfo.GROOVY_PROPERTY_GETTER) {
                return request.interceptedCallable!!.returnType!!.type!!
            } else if (request.interceptedCallable!!.kind === CallableKindInfo.GROOVY_PROPERTY_SETTER) {
                val newValueParameter =
                    request.interceptedCallable!!.parameters!!.stream().filter({ parameter -> parameter.kind === ParameterKindInfo.METHOD_PARAMETER }).findFirst()
                return requireNotNull(newValueParameter.orElseThrow<IllegalArgumentException?>(java.util.function.Supplier { java.lang.IllegalArgumentException("a setter interceptor must accept a parameter") })!!.parameterType)
            } else {
                throw IllegalArgumentException("expected a property interception request, got " + request)
            }
        }

        private fun propertyReceiverType(request: CallInterceptionRequest): Type {
            return requireNotNull(
                request.interceptedCallable!!.parameters!!.stream().filter({ it -> it.kind === ParameterKindInfo.RECEIVER }).findFirst()
                    .orElseThrow({ java.lang.IllegalArgumentException("a property interception request must have a receiver parameter") })!!.parameterType
            )
        }

        val FILTERABLE_CALL_INTERCEPTOR: ClassName = ClassName.get(FilterableCallInterceptor::class.java)
        private val CALL_INTERCEPTOR_CLASS: ClassName = ClassName.get(AbstractCallInterceptor::class.java)
        private val SIGNATURE_AWARE_CALL_INTERCEPTOR_CLASS: ClassName = ClassName.get(SignatureAwareCallInterceptor::class.java)
        val SIGNATURE_AWARE_CALL_INTERCEPTOR_SIGNATURE_MATCH: ClassName = ClassName.get(SignatureAwareCallInterceptor.SignatureMatch::class.java)
        private val PROPERTY_AWARE_CALL_INTERCEPTOR_CLASS: ClassName = ClassName.get(PropertyAwareCallInterceptor::class.java)
        private val INTERCEPTED_SCOPE_CLASS: ClassName = ClassName.get(InterceptScope::class.java)
        private val INVOCATION_CLASS: ClassName = ClassName.get(Invocation::class.java)
    }
}
