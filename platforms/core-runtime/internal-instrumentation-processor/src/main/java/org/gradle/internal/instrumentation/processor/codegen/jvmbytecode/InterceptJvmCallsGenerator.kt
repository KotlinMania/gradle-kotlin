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
package org.gradle.internal.instrumentation.processor.codegen.jvmbytecode

import org.gradle.internal.instrumentation.model.CallableInfo.hasKotlinDefaultMaskParam
import org.gradle.internal.instrumentation.model.CallableInfo.hasCallerClassNameParam
import org.gradle.internal.instrumentation.model.CallableInfo.hasInjectVisitorContextParam
import org.gradle.internal.instrumentation.model.CallableOwnerInfo.isInterceptSubtypes
import org.gradle.internal.instrumentation.processor.codegen.RequestGroupingInstrumentationClassSourceGenerator
import org.gradle.internal.instrumentation.model.CallInterceptionRequest
import org.gradle.internal.instrumentation.model.CallableInfo
import org.gradle.internal.instrumentation.model.RequestExtra
import org.gradle.internal.instrumentation.processor.codegen.HasFailures.FailureInfo
import org.gradle.internal.instrumentation.processor.codegen.jvmbytecode.InterceptJvmCallsGenerator
import org.gradle.internal.instrumentation.model.CallableOwnerInfo
import org.gradle.internal.instrumentation.processor.codegen.GradleReferencedType
import org.gradle.internal.instrumentation.processor.codegen.jvmbytecode.InterceptJvmCallsGenerator.InvocationGenerator
import org.gradle.internal.instrumentation.processor.codegen.JavadocUtils
import org.gradle.internal.instrumentation.model.CallableKindInfo
import org.gradle.internal.instrumentation.model.ParameterKindInfo
import org.gradle.internal.instrumentation.processor.codegen.InstrumentationResourceGenerator
import org.gradle.internal.instrumentation.processor.codegen.InstrumentationResourceGenerator.GenerationResult.CanGenerateResource

/**
 * Generates a single bytecode rewriter class.
 */
class InterceptJvmCallsGenerator : RequestGroupingInstrumentationClassSourceGenerator() {
    /**
     * Emits the code that generates interceptor method invocation.
     */
    private fun interface InvocationGenerator {
        fun generate(request: CallInterceptionRequest?, implTypeField: com.squareup.javapoet.FieldSpec?, code: com.squareup.javapoet.CodeBlock.Builder?)
    }

    /**
     * Creates the code block that checks if the invocation operation should be intercepted.
     */
    private fun interface InvocationMatcher {
        fun generate(info: CallableInfo?): com.squareup.javapoet.CodeBlock?
    }

    override fun classNameForRequest(request: CallInterceptionRequest): kotlin.String {
        return request.requestExtras.getByType(org.gradle.internal.instrumentation.model.RequestExtra.InterceptJvmCalls::class.java)
            .map(RequestExtra.InterceptJvmCalls::getImplementationClassName)
            .orElse(null)
    }

    override fun classContentForClass(
        className: kotlin.String,
        requestsClassGroup: kotlin.collections.MutableList<CallInterceptionRequest?>,
        onProcessedRequest: java.util.function.Consumer<in CallInterceptionRequest?>,
        onFailure: java.util.function.Consumer<in FailureInfo?>
    ): java.util.function.Consumer<com.squareup.javapoet.TypeSpec.Builder?> {
        val typeFieldByOwner: kotlin.collections.MutableMap<org.objectweb.asm.Type?, com.squareup.javapoet.FieldSpec?> =
            InterceptJvmCallsGenerator.Companion.generateFieldsForImplementationOwners(requestsClassGroup)
        val interceptorType: org.gradle.internal.instrumentation.api.types.BytecodeInterceptorType =
            requestsClassGroup.get(0).requestExtras.getByType(org.gradle.internal.instrumentation.model.RequestExtra.InterceptJvmCalls::class.java)
                .map(RequestExtra.InterceptJvmCalls::interceptionType)
                .orElseThrow({ java.lang.IllegalStateException(org.gradle.internal.instrumentation.model.RequestExtra.InterceptJvmCalls::class.java.getSimpleName() + " should be present at this stage!") })

        val requestsByOwner: kotlin.collections.MutableMap<CallableOwnerInfo?, kotlin.collections.MutableList<CallInterceptionRequest?>?> = requestsClassGroup.stream().collect(
            java.util.stream.Collectors.groupingBy(
                java.util.function.Function { it: CallInterceptionRequest? -> it.interceptedCallable.owner },
                java.util.function.Supplier { LinkedHashMap() },
                java.util.stream.Collectors.toList()
            )
        )

        val visitMethodInsnBuilder: com.squareup.javapoet.MethodSpec.Builder = InterceptJvmCallsGenerator.Companion.visitMethodInsnBuilder
        InterceptJvmCallsGenerator.Companion.generateVisitMethodInsnCode(visitMethodInsnBuilder, typeFieldByOwner, requestsByOwner, onProcessedRequest, onFailure)

        val findBridgeMethodBuilder: com.squareup.javapoet.MethodSpec.Builder = InterceptJvmCallsGenerator.Companion.findBridgeMethodBuilder
        InterceptJvmCallsGenerator.Companion.generateFindBridgeMethodBuilderCode(findBridgeMethodBuilder, typeFieldByOwner, requestsByOwner, onProcessedRequest, onFailure)

        val factoryClass: com.squareup.javapoet.TypeSpec = InterceptJvmCallsGenerator.Companion.generateFactoryClass(className, interceptorType)

        return java.util.function.Consumer { builder: com.squareup.javapoet.TypeSpec.Builder? ->
            builder.addMethod(constructor)
                .addAnnotation(GradleReferencedType.GENERATED_ANNOTATION.asClassName())
                .addModifiers(javax.lang.model.element.Modifier.PUBLIC) // generic stuff not related to the content:
                .addSuperinterface(com.squareup.javapoet.ClassName.get(org.gradle.internal.instrumentation.api.jvmbytecode.JvmBytecodeCallInterceptor::class.java))
                .addSuperinterface(com.squareup.javapoet.ClassName.get(interceptorType.interceptorMarkerInterface))
                .addMethod(InterceptJvmCallsGenerator.Companion.BINARY_CLASS_NAME_OF)
                .addMethod(InterceptJvmCallsGenerator.Companion.LOAD_BINARY_CLASS_NAME)
                .addField(InterceptJvmCallsGenerator.Companion.INTERCEPTORS_REQUEST_TYPE)
                .addField(InterceptJvmCallsGenerator.Companion.METADATA_FIELD)
                .addField(InterceptJvmCallsGenerator.Companion.CONTEXT_FIELD) // actual content:
                .addMethod(visitMethodInsnBuilder.build())
                .addMethod(findBridgeMethodBuilder.build())
                .addFields(typeFieldByOwner.values)
                .addType(factoryClass)
        }
    }

    var constructor: com.squareup.javapoet.MethodSpec = com.squareup.javapoet.MethodSpec.constructorBuilder().addModifiers(javax.lang.model.element.Modifier.PRIVATE)
        .addParameter(org.gradle.internal.instrumentation.api.metadata.InstrumentationMetadata::class.java, "metadata")
        .addParameter(org.gradle.internal.instrumentation.api.types.BytecodeInterceptorFilter::class.java, "context")
        .addStatement("this.\$N = metadata", InterceptJvmCallsGenerator.Companion.METADATA_FIELD)
        .addStatement("this.\$N = context", InterceptJvmCallsGenerator.Companion.CONTEXT_FIELD)
        .build()

    private class Failure(val reason: kotlin.String?) : java.lang.RuntimeException()
    companion object {
        private fun generateFactoryClass(className: kotlin.String, interceptorType: org.gradle.internal.instrumentation.api.types.BytecodeInterceptorType): com.squareup.javapoet.TypeSpec {
            val method: com.squareup.javapoet.MethodSpec = com.squareup.javapoet.MethodSpec.methodBuilder("create")
                .addModifiers(javax.lang.model.element.Modifier.PUBLIC)
                .returns(org.gradle.internal.instrumentation.api.jvmbytecode.JvmBytecodeCallInterceptor::class.java)
                .addParameter(org.gradle.internal.instrumentation.api.metadata.InstrumentationMetadata::class.java, "metadata")
                .addParameter(org.gradle.internal.instrumentation.api.types.BytecodeInterceptorFilter::class.java, "context")
                .addStatement("return new \$L(\$N, \$N)", className, "metadata", "context")
                .addAnnotation(java.lang.Override::class.java)
                .build()
            return com.squareup.javapoet.TypeSpec.classBuilder("Factory")
                .addModifiers(javax.lang.model.element.Modifier.PUBLIC, javax.lang.model.element.Modifier.STATIC)
                .addSuperinterface(com.squareup.javapoet.ClassName.get(org.gradle.internal.instrumentation.api.jvmbytecode.JvmBytecodeCallInterceptor.Factory::class.java))
                .addSuperinterface(com.squareup.javapoet.ClassName.get(interceptorType.interceptorFactoryMarkerInterface))
                .addMethod(method)
                .build()
        }


        private fun generateVisitMethodInsnCode(
            method: com.squareup.javapoet.MethodSpec.Builder,
            typeFieldByOwner: kotlin.collections.MutableMap<org.objectweb.asm.Type?, com.squareup.javapoet.FieldSpec?>,
            requestsByOwner: kotlin.collections.MutableMap<CallableOwnerInfo?, kotlin.collections.MutableList<CallInterceptionRequest?>?>,
            onProcessedRequest: java.util.function.Consumer<in CallInterceptionRequest?>,
            onFailure: java.util.function.Consumer<in FailureInfo?>
        ) {
            val code: com.squareup.javapoet.CodeBlock.Builder = com.squareup.javapoet.CodeBlock.builder()
            requestsByOwner.forEach { (owner: CallableOwnerInfo?, requests: kotlin.collections.MutableList<CallInterceptionRequest>?) ->
                InterceptJvmCallsGenerator.Companion.generateCodeForOwner(
                    owner,
                    typeFieldByOwner,
                    requests,
                    code,
                    org.gradle.internal.instrumentation.processor.codegen.jvmbytecode.InterceptJvmCallsGenerator.InvocationMatcher { interceptedCallable: CallableInfo? ->
                        InterceptJvmCallsGenerator.Companion.matchOpcodeExpression(
                            interceptedCallable
                        )
                    },
                    InvocationGenerator { request: CallInterceptionRequest?, implTypeField: com.squareup.javapoet.FieldSpec?, method: com.squareup.javapoet.CodeBlock.Builder? ->
                        InterceptJvmCallsGenerator.Companion.generateInterceptedInvocation(
                            request,
                            implTypeField,
                            method
                        )
                    },
                    InvocationGenerator? { request: CallInterceptionRequest?, ownerTypeField: com.squareup.javapoet.FieldSpec?, method: com.squareup.javapoet.CodeBlock.Builder? ->
                    InterceptJvmCallsGenerator.Companion.generateKotlinDefaultInvocation(
                        request,
                        ownerTypeField,
                        method
                    )
                },
                onProcessedRequest,
                onFailure)
            }
            code.addStatement("return false")
            method.addCode(code.build())
        }

        private fun generateFindBridgeMethodBuilderCode(
            method: com.squareup.javapoet.MethodSpec.Builder,
            typeFieldByOwner: kotlin.collections.MutableMap<org.objectweb.asm.Type?, com.squareup.javapoet.FieldSpec?>,
            requestsByOwner: kotlin.collections.MutableMap<CallableOwnerInfo?, kotlin.collections.MutableList<CallInterceptionRequest?>?>,
            onProcessedRequest: java.util.function.Consumer<in CallInterceptionRequest?>,
            onFailure: java.util.function.Consumer<in FailureInfo?>
        ) {
            val code: com.squareup.javapoet.CodeBlock.Builder = com.squareup.javapoet.CodeBlock.builder()
            requestsByOwner.forEach { (owner: CallableOwnerInfo?, requests: kotlin.collections.MutableList<CallInterceptionRequest>?) ->
                InterceptJvmCallsGenerator.Companion.generateCodeForOwner(
                    owner,
                    typeFieldByOwner,
                    requests,
                    code,
                    org.gradle.internal.instrumentation.processor.codegen.jvmbytecode.InterceptJvmCallsGenerator.InvocationMatcher { callableInfo: CallableInfo? ->
                        InterceptJvmCallsGenerator.Companion.matchTagExpression(
                            callableInfo
                        )
                    },
                    InvocationGenerator { request: CallInterceptionRequest?, implTypeField: com.squareup.javapoet.FieldSpec?, method: com.squareup.javapoet.CodeBlock.Builder? ->
                        InterceptJvmCallsGenerator.Companion.generateBridgeMethodBuilder(
                            request,
                            implTypeField,
                            method
                        )
                    },
                    null,
                    onProcessedRequest,
                    onFailure
                )
            }
            code.addStatement("return null")
            method.addCode(code.build())
        }


        private fun generateBridgeMethodBuilder(request: CallInterceptionRequest, implTypeField: com.squareup.javapoet.FieldSpec?, method: com.squareup.javapoet.CodeBlock.Builder) {
            val interceptorName: kotlin.String? = request.implementationInfo.name
            val interceptorDesc: kotlin.String? = request.implementationInfo.descriptor
            method.addStatement(
                "$1T builder = $1T.create(tag, owner, descriptor, $2N, $3S, $4S)",
                org.gradle.internal.instrumentation.api.jvmbytecode.DefaultBridgeMethodBuilder::class.java,
                implTypeField,
                interceptorName,
                interceptorDesc
            )
            val callable: CallableInfo = request.interceptedCallable
            if (callable.hasKotlinDefaultMaskParam()) {
                method.addStatement("builder = builder.withKotlinDefaultMask()")
            }
            if (callable.hasCallerClassNameParam()) {
                method.addStatement("builder = builder.withClassName(className)")
            }
            if (callable.hasInjectVisitorContextParam()) {
                method.addStatement("builder = builder.withVisitorContext(context)")
            }
            method.addStatement("return builder")
        }

        private fun matchTagExpression(callableInfo: CallableInfo): com.squareup.javapoet.CodeBlock {
            when (callableInfo.kind) {
                INSTANCE_METHOD -> return com.squareup.javapoet.CodeBlock.of("(tag == $1T.H_INVOKEVIRTUAL || tag == $1T.H_INVOKEINTERFACE)", org.objectweb.asm.Opcodes::class.java)
                STATIC_METHOD -> return com.squareup.javapoet.CodeBlock.of("tag == \$T.H_INVOKESTATIC", org.objectweb.asm.Opcodes::class.java)
                AFTER_CONSTRUCTOR -> return com.squareup.javapoet.CodeBlock.of("tag == \$T.H_NEWINVOKESPECIAL", org.objectweb.asm.Opcodes::class.java)
                else -> throw org.gradle.internal.instrumentation.processor.codegen.jvmbytecode.InterceptJvmCallsGenerator.Failure("Unsupported kind " + callableInfo.kind)
            }
        }


        private fun generateFieldsForImplementationOwners(interceptionRequests: kotlin.collections.MutableCollection<CallInterceptionRequest?>): kotlin.collections.MutableMap<org.objectweb.asm.Type?, com.squareup.javapoet.FieldSpec?> {
            val knownSimpleNames: kotlin.collections.MutableSet<kotlin.String?> = java.util.HashSet<kotlin.String?>()
            return interceptionRequests.stream().map<kotlin.Any?> { it: CallInterceptionRequest? -> it.implementationInfo.owner }.distinct()
                .collect(java.util.stream.Collectors.toMap(java.util.function.Function.identity<kotlin.Any?>(), java.util.function.Function { implementationType: kotlin.Any? ->
                    val implementationClassName: com.squareup.javapoet.ClassName = org.gradle.internal.instrumentation.util.NameUtil.getClassName(implementationType.getClassName())
                    val fieldTypeName: kotlin.String? =
                        if (knownSimpleNames.add(implementationClassName.simpleName())) implementationClassName.simpleName() else implementationClassName.reflectionName()
                    val fullFieldName: kotlin.String = org.gradle.internal.instrumentation.util.NameUtil.camelToUpperUnderscoreCase(fieldTypeName) + "_TYPE"
                    com.squareup.javapoet.FieldSpec.builder(
                        kotlin.String::class.java,
                        fullFieldName,
                        javax.lang.model.element.Modifier.PRIVATE,
                        javax.lang.model.element.Modifier.STATIC,
                        javax.lang.model.element.Modifier.FINAL
                    )
                        .initializer("\$S", implementationClassName.reflectionName().replace(".", "/"))
                        .build()
                }, java.util.function.BinaryOperator { u: com.squareup.javapoet.FieldSpec?, v: com.squareup.javapoet.FieldSpec? -> u }, java.util.function.Supplier { LinkedHashMap() }))
        }

        private val METHOD_VISITOR_PARAM: com.squareup.javapoet.ParameterSpec = com.squareup.javapoet.ParameterSpec.builder(org.gradle.model.internal.asm.MethodVisitorScope::class.java, "mv").build()

        private val visitMethodInsnBuilder: MethodSpec.Builder
            get() = com.squareup.javapoet.MethodSpec.methodBuilder("visitMethodInsn")
                .addAnnotation(java.lang.Override::class.java)
                .addModifiers(javax.lang.model.element.Modifier.PUBLIC)
                .returns(kotlin.Boolean::class.javaPrimitiveType)
                .addParameter(InterceptJvmCallsGenerator.Companion.METHOD_VISITOR_PARAM)
                .addParameter(kotlin.String::class.java, "className")
                .addParameter(kotlin.Int::class.javaPrimitiveType, "opcode")
                .addParameter(kotlin.String::class.java, "owner")
                .addParameter(kotlin.String::class.java, "name")
                .addParameter(kotlin.String::class.java, "descriptor")
                .addParameter(kotlin.Boolean::class.javaPrimitiveType, "isInterface")
                .addParameter(com.squareup.javapoet.ParameterizedTypeName.get(java.util.function.Supplier::class.java, org.objectweb.asm.tree.MethodNode::class.java), "readMethodNode")

        private val findBridgeMethodBuilder: MethodSpec.Builder
            get() = com.squareup.javapoet.MethodSpec.methodBuilder("findBridgeMethodBuilder")
                .addAnnotation(java.lang.Override::class.java)
                .addModifiers(javax.lang.model.element.Modifier.PUBLIC)
                .returns(org.gradle.internal.instrumentation.api.jvmbytecode.BridgeMethodBuilder::class.java)
                .addParameter(kotlin.String::class.java, "className")
                .addParameter(kotlin.Int::class.javaPrimitiveType, "tag")
                .addParameter(kotlin.String::class.java, "owner")
                .addParameter(kotlin.String::class.java, "name")
                .addParameter(kotlin.String::class.java, "descriptor")

        private val BINARY_CLASS_NAME_OF: com.squareup.javapoet.MethodSpec = com.squareup.javapoet.MethodSpec.methodBuilder("binaryClassNameOf")
            .addModifiers(javax.lang.model.element.Modifier.PRIVATE, javax.lang.model.element.Modifier.STATIC)
            .returns(kotlin.String::class.java)
            .addParameter(kotlin.String::class.java, "className")
            .addStatement("return \$T.getObjectType(className).getClassName()", org.objectweb.asm.Type::class.java)
            .build()

        private val INTERCEPTORS_REQUEST_TYPE: com.squareup.javapoet.FieldSpec = com.squareup.javapoet.FieldSpec.builder(
            org.objectweb.asm.Type::class.java,
            "INTERCEPTORS_REQUEST_TYPE",
            javax.lang.model.element.Modifier.PRIVATE,
            javax.lang.model.element.Modifier.STATIC,
            javax.lang.model.element.Modifier.FINAL
        )
            .initializer("\$T.getType(\$T.class)", org.objectweb.asm.Type::class.java, org.gradle.internal.instrumentation.api.types.BytecodeInterceptorFilter::class.java)
            .build()

        private val LOAD_BINARY_CLASS_NAME: com.squareup.javapoet.MethodSpec = com.squareup.javapoet.MethodSpec.methodBuilder("loadOwnerBinaryClassName")
            .addModifiers(javax.lang.model.element.Modifier.PRIVATE)
            .returns(Void.TYPE)
            .addParameter(InterceptJvmCallsGenerator.Companion.METHOD_VISITOR_PARAM)
            .addParameter(kotlin.String::class.java, "className")
            .addStatement("$1N._LDC($2N(className))", InterceptJvmCallsGenerator.Companion.METHOD_VISITOR_PARAM, InterceptJvmCallsGenerator.Companion.BINARY_CLASS_NAME_OF)
            .build()

        private val METADATA_FIELD: com.squareup.javapoet.FieldSpec = com.squareup.javapoet.FieldSpec.builder(
            org.gradle.internal.instrumentation.api.metadata.InstrumentationMetadata::class.java,
            "metadata",
            javax.lang.model.element.Modifier.PRIVATE,
            javax.lang.model.element.Modifier.FINAL
        ).build()

        private val CONTEXT_FIELD: com.squareup.javapoet.FieldSpec = com.squareup.javapoet.FieldSpec.builder(
            org.gradle.internal.instrumentation.api.types.BytecodeInterceptorFilter::class.java,
            "context",
            javax.lang.model.element.Modifier.PRIVATE,
            javax.lang.model.element.Modifier.FINAL
        ).build()

        private fun generateCodeForOwner(
            owner: CallableOwnerInfo,
            implTypeFields: kotlin.collections.MutableMap<org.objectweb.asm.Type?, com.squareup.javapoet.FieldSpec?>,
            requestsForOwner: kotlin.collections.MutableList<CallInterceptionRequest>,
            code: com.squareup.javapoet.CodeBlock.Builder,
            invocationMatcher: org.gradle.internal.instrumentation.processor.codegen.jvmbytecode.InterceptJvmCallsGenerator.InvocationMatcher,
            interceptStandard: InvocationGenerator,
            interceptKotlinDefault: InvocationGenerator?,
            onProcessedRequest: java.util.function.Consumer<in CallInterceptionRequest?>,
            onFailure: java.util.function.Consumer<in FailureInfo?>
        ) {
            if (owner.isInterceptSubtypes) {
                code.beginControlFlow("if (\$N.isInstanceOf(owner, \$S))", InterceptJvmCallsGenerator.Companion.METADATA_FIELD, owner.type.getInternalName())
            } else {
                code.beginControlFlow("if (owner.equals(\$S))", owner.type.getInternalName())
            }
            for (request in requestsForOwner) {
                val nested: com.squareup.javapoet.CodeBlock.Builder = com.squareup.javapoet.CodeBlock.builder()
                try {
                    InterceptJvmCallsGenerator.Companion.generateCodeForRequest(
                        request,
                        implTypeFields.get(request.implementationInfo.owner),
                        nested,
                        invocationMatcher,
                        interceptStandard,
                        interceptKotlinDefault
                    )
                } catch (failure: org.gradle.internal.instrumentation.processor.codegen.jvmbytecode.InterceptJvmCallsGenerator.Failure) {
                    onFailure.accept(FailureInfo(request, failure.reason))
                }
                onProcessedRequest.accept(request)
                code.add(nested.build())
            }
            code.endControlFlow()
        }

        private fun generateCodeForRequest(
            request: CallInterceptionRequest,
            implTypeField: com.squareup.javapoet.FieldSpec?,
            code: com.squareup.javapoet.CodeBlock.Builder,
            invocationMatcher: org.gradle.internal.instrumentation.processor.codegen.jvmbytecode.InterceptJvmCallsGenerator.InvocationMatcher,
            interceptStandard: InvocationGenerator,
            interceptKotlinDefault: InvocationGenerator?
        ) {
            val callableName: kotlin.String = request.interceptedCallable.callableName
            val interceptedCallable: CallableInfo = request.interceptedCallable
            val interceptedCallableDescriptor: kotlin.String = InterceptJvmCallsGenerator.Companion.standardCallableDescriptor(interceptedCallable)
            InterceptJvmCallsGenerator.Companion.validateSignature(interceptedCallable)

            val matchInvocationOperation: com.squareup.javapoet.CodeBlock? = invocationMatcher.generate(interceptedCallable)

            InterceptJvmCallsGenerator.Companion.documentInterceptorGeneratedCode(request, code)
            InterceptJvmCallsGenerator.Companion.matchAndInterceptStandardCallableSignature(
                request,
                implTypeField,
                code,
                callableName,
                interceptedCallableDescriptor,
                matchInvocationOperation,
                interceptStandard
            )

            if (interceptKotlinDefault != null && interceptedCallable.hasKotlinDefaultMaskParam()) {
                InterceptJvmCallsGenerator.Companion.matchAndInterceptKotlinDefaultSignature(
                    request,
                    implTypeField,
                    code,
                    callableName,
                    interceptedCallable,
                    matchInvocationOperation,
                    interceptKotlinDefault
                )
            }
        }

        private fun matchAndInterceptStandardCallableSignature(
            request: CallInterceptionRequest?,
            implTypeField: com.squareup.javapoet.FieldSpec?,
            code: com.squareup.javapoet.CodeBlock.Builder,
            callableName: kotlin.String,
            callableDescriptor: kotlin.String?,
            matchOpcodeExpression: com.squareup.javapoet.CodeBlock?,
            invocationGenerator: InvocationGenerator
        ) {
            code.beginControlFlow("if (name.equals(\$S) && descriptor.equals(\$S) && \$L)", callableName, callableDescriptor, matchOpcodeExpression)
            invocationGenerator.generate(request, implTypeField, code)
            code.endControlFlow()
        }

        private fun matchAndInterceptKotlinDefaultSignature(
            request: CallInterceptionRequest?,
            ownerTypeField: com.squareup.javapoet.FieldSpec?,
            code: com.squareup.javapoet.CodeBlock.Builder,
            callableName: kotlin.String?,
            interceptedCallable: CallableInfo,
            matchOpcodeExpression: com.squareup.javapoet.CodeBlock?,
            invocationGenerator: InvocationGenerator
        ) {
            code.add("// Additionally intercept the signature with the Kotlin default mask and marker:\n")
            val callableDescriptorKotlinDefault: kotlin.String = InterceptJvmCallsGenerator.Companion.kotlinDefaultFunctionDescriptor(interceptedCallable)
            val defaultMethodName: kotlin.String = callableName + "\$default"
            code.beginControlFlow("if (name.equals(\$S) && descriptor.equals(\$S) && \$L)", defaultMethodName, callableDescriptorKotlinDefault, matchOpcodeExpression)
            invocationGenerator.generate(request, ownerTypeField, code)
            code.endControlFlow()
        }

        private fun documentInterceptorGeneratedCode(request: CallInterceptionRequest, code: com.squareup.javapoet.CodeBlock.Builder) {
            code.add("/** \n * Intercepting \$L: \$L\n", JavadocUtils.callableKindForJavadoc(request), JavadocUtils.interceptedCallableLink(request))
            code.add(" * Intercepted by \$L\n*/\n", JavadocUtils.interceptorImplementationLink(request))
        }

        private fun matchOpcodeExpression(interceptedCallable: CallableInfo): com.squareup.javapoet.CodeBlock {
            when (interceptedCallable.kind) {
                STATIC_METHOD -> return com.squareup.javapoet.CodeBlock.of("opcode == \$T.INVOKESTATIC", org.objectweb.asm.Opcodes::class.java)
                INSTANCE_METHOD -> return com.squareup.javapoet.CodeBlock.of("(opcode == $1T.INVOKEVIRTUAL || opcode == $1T.INVOKEINTERFACE)", org.objectweb.asm.Opcodes::class.java)
                AFTER_CONSTRUCTOR -> return com.squareup.javapoet.CodeBlock.of("opcode == \$T.INVOKESPECIAL", org.objectweb.asm.Opcodes::class.java)
                else -> throw org.gradle.internal.instrumentation.processor.codegen.jvmbytecode.InterceptJvmCallsGenerator.Failure("Could not determine the opcode for intercepting the call")
            }
        }

        // TODO: move validation earlier?
        private fun generateInterceptedInvocation(request: CallInterceptionRequest, implTypeField: com.squareup.javapoet.FieldSpec?, method: com.squareup.javapoet.CodeBlock.Builder) {
            val callable: CallableInfo = request.interceptedCallable
            val implementationName: kotlin.String? = request.implementationInfo.name
            val implementationDescriptor: kotlin.String = request.implementationInfo.descriptor

            if (callable.kind === CallableKindInfo.STATIC_METHOD || callable.kind === CallableKindInfo.INSTANCE_METHOD) {
                InterceptJvmCallsGenerator.Companion.generateNormalInterceptedInvocation(implTypeField, callable, implementationName, implementationDescriptor, method)
            } else if (callable.kind === CallableKindInfo.AFTER_CONSTRUCTOR) {
                InterceptJvmCallsGenerator.Companion.generateInvocationAfterConstructor(implTypeField, method, callable, implementationName, implementationDescriptor)
            }
            method.addStatement("return true")
        }

        private fun generateInvocationAfterConstructor(
            implOwnerField: com.squareup.javapoet.FieldSpec?,
            code: com.squareup.javapoet.CodeBlock.Builder,
            callable: CallableInfo,
            implementationName: kotlin.String?,
            implementationDescriptor: kotlin.String
        ) {
            kotlin.require(callable.kind === CallableKindInfo.AFTER_CONSTRUCTOR) { "expected after-constructor interceptor" }

            val parameters: kotlin.collections.MutableList<org.gradle.internal.instrumentation.model.ParameterInfo?> = callable.parameters
            if (parameters.get(0).kind !== ParameterKindInfo.RECEIVER) {
                throw org.gradle.internal.instrumentation.processor.codegen.jvmbytecode.InterceptJvmCallsGenerator.Failure("Expected @" + org.gradle.internal.instrumentation.api.annotations.ParameterKind.Receiver::class.java.getSimpleName() + " first parameter in @" + org.gradle.internal.instrumentation.api.annotations.CallableKind.AfterConstructor::class.java.getSimpleName())
            }
            if (org.objectweb.asm.Type.getReturnType(implementationDescriptor) != org.objectweb.asm.Type.VOID_TYPE) {
                throw org.gradle.internal.instrumentation.processor.codegen.jvmbytecode.InterceptJvmCallsGenerator.Failure("@" + org.gradle.internal.instrumentation.api.annotations.CallableKind.AfterConstructor::class.java.getSimpleName() + " handlers can only return void")
            }

            val maxLocalsVar: com.squareup.javapoet.CodeBlock = com.squareup.javapoet.CodeBlock.of("maxLocals")
            code.addStatement("int \$L = readMethodNode.get().maxLocals", maxLocalsVar)

            // Store the constructor arguments in local variables, so that we can duplicate them for both the constructor and the interceptor:
            val params: kotlin.Array<org.objectweb.asm.Type?> = org.objectweb.asm.Type.getArgumentTypes(InterceptJvmCallsGenerator.Companion.standardCallableDescriptor(callable))
            for (i in params.indices.reversed()) {
                code.addStatement("$1T type$2L = $1T.getType($3T.class)", org.objectweb.asm.Type::class.java, i, org.gradle.internal.instrumentation.processor.codegen.TypeUtils.typeName(params[i]))
                code.addStatement("int var$1L = $2L + $3L", i, maxLocalsVar, i * 2 /* in case it's long or double */)
                code.addStatement("$1N.visitVarInsn(type$2L.getOpcode($3T.ISTORE), var$2L)", InterceptJvmCallsGenerator.Companion.METHOD_VISITOR_PARAM, i, org.objectweb.asm.Opcodes::class.java)
            }
            // Duplicate the receiver without storing it into a local variable, then prepare the arguments for the original invocation:
            code.addStatement("\$N._DUP()", InterceptJvmCallsGenerator.Companion.METHOD_VISITOR_PARAM)
            for (i in params.indices) {
                code.addStatement("$1N.visitVarInsn(type$2L.getOpcode($3T.ILOAD), var$2L)", InterceptJvmCallsGenerator.Companion.METHOD_VISITOR_PARAM, i, org.objectweb.asm.Opcodes::class.java)
            }
            // Put the arguments to the stack again, for the "interceptor" invocation:
            code.addStatement("\$N._INVOKESPECIAL(owner, name, descriptor)", InterceptJvmCallsGenerator.Companion.METHOD_VISITOR_PARAM)
            for (i in params.indices) {
                code.addStatement("$1N.visitVarInsn(type$2L.getOpcode($3T.ILOAD), var$2L)", InterceptJvmCallsGenerator.Companion.METHOD_VISITOR_PARAM, i, org.objectweb.asm.Opcodes::class.java)
            }
            InterceptJvmCallsGenerator.Companion.maybeGenerateLoadBinaryClassNameCall(code, callable)
            InterceptJvmCallsGenerator.Companion.maybeGenerateGetStaticInjectVisitorContext(code, callable)
            code.addStatement("\$N._INVOKESTATIC(\$N, \$S, \$S)", InterceptJvmCallsGenerator.Companion.METHOD_VISITOR_PARAM, implOwnerField, implementationName, implementationDescriptor)
        }

        private fun generateNormalInterceptedInvocation(
            ownerTypeField: com.squareup.javapoet.FieldSpec?,
            callable: CallableInfo,
            implementationName: kotlin.String?,
            implementationDescriptor: kotlin.String?,
            code: com.squareup.javapoet.CodeBlock.Builder
        ) {
            kotlin.require(!(callable.kind === CallableKindInfo.GROOVY_PROPERTY_GETTER || callable.kind === CallableKindInfo.GROOVY_PROPERTY_SETTER)) { "cannot generate invocation for Groovy property" }

            val parameters: kotlin.collections.MutableList<org.gradle.internal.instrumentation.model.ParameterInfo?> = callable.parameters
            if (parameters.size > 1 && parameters.get(parameters.size - 2).kind === ParameterKindInfo.KOTLIN_DEFAULT_MASK) {
                // push the default mask equal to zero, meaning that no parameters have the default values
                code.add("// The interceptor expects a Kotlin default mask, add a zero argument:\n")
                code.addStatement("\$N._ICONST_0()", InterceptJvmCallsGenerator.Companion.METHOD_VISITOR_PARAM)
            }
            InterceptJvmCallsGenerator.Companion.maybeGenerateLoadBinaryClassNameCall(code, callable)
            InterceptJvmCallsGenerator.Companion.maybeGenerateGetStaticInjectVisitorContext(code, callable)
            code.addStatement("\$N._INVOKESTATIC(\$N, \$S, \$S)", InterceptJvmCallsGenerator.Companion.METHOD_VISITOR_PARAM, ownerTypeField, implementationName, implementationDescriptor)
        }

        private fun generateKotlinDefaultInvocation(request: CallInterceptionRequest, ownerTypeField: com.squareup.javapoet.FieldSpec?, method: com.squareup.javapoet.CodeBlock.Builder) {
            val interceptedCallable: CallableInfo = request.interceptedCallable
            kotlin.require(!(interceptedCallable.kind === CallableKindInfo.GROOVY_PROPERTY_GETTER || interceptedCallable.kind === CallableKindInfo.GROOVY_PROPERTY_SETTER)) { "cannot generate invocation for Groovy property" }

            val implementationName: kotlin.String? = request.implementationInfo.name
            val implementationDescriptor: kotlin.String? = request.implementationInfo.descriptor

            method.addStatement("\$N._POP()", InterceptJvmCallsGenerator.Companion.METHOD_VISITOR_PARAM) // pops the default method signature marker
            InterceptJvmCallsGenerator.Companion.maybeGenerateLoadBinaryClassNameCall(method, interceptedCallable)
            InterceptJvmCallsGenerator.Companion.maybeGenerateGetStaticInjectVisitorContext(method, interceptedCallable)
            method.addStatement("\$N._INVOKESTATIC(\$N, \$S, \$S)", InterceptJvmCallsGenerator.Companion.METHOD_VISITOR_PARAM, ownerTypeField, implementationName, implementationDescriptor)
            method.addStatement("return true")
        }

        private fun validateSignature(callable: CallableInfo) {
            if (callable.kind === CallableKindInfo.GROOVY_PROPERTY_GETTER || callable.kind === CallableKindInfo.GROOVY_PROPERTY_SETTER) {
                throw org.gradle.internal.instrumentation.processor.codegen.jvmbytecode.InterceptJvmCallsGenerator.Failure("Groovy property access cannot be intercepted in JVM calls")
            }

            val hasInjectVisitorContext: kotlin.Boolean = callable.hasInjectVisitorContextParam()
            if (hasInjectVisitorContext) {
                val lastParameter: org.gradle.internal.instrumentation.model.ParameterInfo = callable.parameters.get(callable.parameters.size() - 1)
                if (lastParameter.kind !== ParameterKindInfo.INJECT_VISITOR_CONTEXT) {
                    throw org.gradle.internal.instrumentation.processor.codegen.jvmbytecode.InterceptJvmCallsGenerator.Failure("The interceptor's @" + org.gradle.internal.instrumentation.api.annotations.ParameterKind.InjectVisitorContext::class.java.getSimpleName() + " parameter should be last parameter")
                }
                if (!lastParameter.parameterType.getClassName().equals(org.gradle.internal.instrumentation.api.types.BytecodeInterceptorFilter::class.java.getName())) {
                    throw org.gradle.internal.instrumentation.processor.codegen.jvmbytecode.InterceptJvmCallsGenerator.Failure("The interceptor's @" + org.gradle.internal.instrumentation.api.annotations.ParameterKind.InjectVisitorContext::class.java.getSimpleName() + " parameter should be of type " + org.gradle.internal.instrumentation.api.types.BytecodeInterceptorFilter::class.java.getName() + " but was " + lastParameter.parameterType.getClassName())
                }
                if (callable.parameters.stream().filter({ it -> it.kind === ParameterKindInfo.INJECT_VISITOR_CONTEXT }).count() > 1) {
                    throw org.gradle.internal.instrumentation.processor.codegen.jvmbytecode.InterceptJvmCallsGenerator.Failure("An interceptor may not have more than one @" + org.gradle.internal.instrumentation.api.annotations.ParameterKind.InjectVisitorContext::class.java.getSimpleName() + " parameter")
                }
            }

            val hasCallerClassName: kotlin.Boolean = callable.hasCallerClassNameParam()
            if (hasCallerClassName) {
                val expectedIndex: kotlin.Int = if (hasInjectVisitorContext) callable.parameters.size() - 2 else callable.parameters.size() - 1
                if (callable.parameters.get(expectedIndex).kind !== ParameterKindInfo.CALLER_CLASS_NAME) {
                    throw org.gradle.internal.instrumentation.processor.codegen.jvmbytecode.InterceptJvmCallsGenerator.Failure("The interceptor's @" + org.gradle.internal.instrumentation.api.annotations.ParameterKind.CallerClassName::class.java.getSimpleName() + " parameter should be last or just before @" + org.gradle.internal.instrumentation.api.annotations.ParameterKind.InjectVisitorContext::class.java.getSimpleName() + " if that parameter is present")
                }
                if (callable.parameters.stream().filter({ it -> it.kind === ParameterKindInfo.CALLER_CLASS_NAME }).count() > 1) {
                    throw org.gradle.internal.instrumentation.processor.codegen.jvmbytecode.InterceptJvmCallsGenerator.Failure("An interceptor may not have more than one @" + org.gradle.internal.instrumentation.api.annotations.ParameterKind.CallerClassName::class.java.getSimpleName() + " parameter")
                }
            }

            if (callable.hasKotlinDefaultMaskParam()) {
                // TODO support @AfterConstructor with Kotlin default mask? Kotlin constructors have a special DefaultConstructorMarker as the last argument
                if (callable.kind !== CallableKindInfo.STATIC_METHOD && callable.kind !== CallableKindInfo.INSTANCE_METHOD) {
                    throw org.gradle.internal.instrumentation.processor.codegen.jvmbytecode.InterceptJvmCallsGenerator.Failure(
                        "Only @" + org.gradle.internal.instrumentation.api.annotations.CallableKind.StaticMethod::class.java.getSimpleName() + " or @" + org.gradle.internal.instrumentation.api.annotations.CallableKind.InstanceMethod::class.java.getSimpleName() + " can use Kotlin default parameters"
                    )
                }

                val expectedKotlinDefaultMaskIndex: kotlin.Int = callable.parameters.size() - (if (hasCallerClassName) 2 else 1)
                if (callable.parameters.get(expectedKotlinDefaultMaskIndex).kind !== ParameterKindInfo.KOTLIN_DEFAULT_MASK) {
                    throw org.gradle.internal.instrumentation.processor.codegen.jvmbytecode.InterceptJvmCallsGenerator.Failure(
                        "@" + org.gradle.internal.instrumentation.api.annotations.ParameterKind.KotlinDefaultMask::class.java.getSimpleName() + " should be the last parameter of may be followed only by @" + org.gradle.internal.instrumentation.api.annotations.ParameterKind.CallerClassName::class.java.getSimpleName()
                    )
                }
            }

            if (callable.owner.isInterceptSubtypes() && !callable.owner.type.getInternalName().startsWith("org/gradle")) {
                throw org.gradle.internal.instrumentation.processor.codegen.jvmbytecode.InterceptJvmCallsGenerator.Failure("Intercepting inherited methods is supported only for Gradle types for now, but type was: " + callable.owner.type.getInternalName())
            }
        }

        private fun maybeGenerateLoadBinaryClassNameCall(code: com.squareup.javapoet.CodeBlock.Builder, callableInfo: CallableInfo) {
            if (callableInfo.hasCallerClassNameParam()) {
                code.addStatement("\$N(\$N, className)", InterceptJvmCallsGenerator.Companion.LOAD_BINARY_CLASS_NAME, InterceptJvmCallsGenerator.Companion.METHOD_VISITOR_PARAM)
            }
        }

        private fun maybeGenerateGetStaticInjectVisitorContext(code: com.squareup.javapoet.CodeBlock.Builder, callableInfo: CallableInfo) {
            if (callableInfo.hasInjectVisitorContextParam()) {
                code.addStatement(
                    "\$N._GETSTATIC(\$N, context.name(), \$N.getDescriptor())",
                    InterceptJvmCallsGenerator.Companion.METHOD_VISITOR_PARAM,
                    InterceptJvmCallsGenerator.Companion.INTERCEPTORS_REQUEST_TYPE,
                    InterceptJvmCallsGenerator.Companion.INTERCEPTORS_REQUEST_TYPE
                )
            }
        }

        private fun standardCallableDescriptor(callableInfo: CallableInfo): kotlin.String {
            val parameterTypes: kotlin.Array<org.objectweb.asm.Type?> = callableInfo.parameters.stream()
                .filter({ it -> it.kind.isSourceParameter() })
                .map(org.gradle.internal.instrumentation.model.ParameterInfo::getParameterType).toArray({ _Dummy_.__Array__() })
            val returnType: org.objectweb.asm.Type = callableInfo.returnType.type
            return org.objectweb.asm.Type.getMethodDescriptor(returnType, *parameterTypes)
        }

        private fun kotlinDefaultFunctionDescriptor(callableInfo: CallableInfo): kotlin.String {
            if (callableInfo.kind !== CallableKindInfo.INSTANCE_METHOD && callableInfo.kind !== CallableKindInfo.STATIC_METHOD) {
                throw java.lang.UnsupportedOperationException("Kotlin default parameters are not yet supported for " + callableInfo.kind)
            }

            val standardDescriptor: kotlin.String = InterceptJvmCallsGenerator.Companion.standardCallableDescriptor(callableInfo)
            val returnType: org.objectweb.asm.Type = org.objectweb.asm.Type.getReturnType(standardDescriptor)
            val argumentTypes: kotlin.Array<org.objectweb.asm.Type?> = org.objectweb.asm.Type.getArgumentTypes(standardDescriptor)
            val argumentTypesWithDefault: kotlin.Array<org.objectweb.asm.Type?> = java.util.stream.Stream.concat<org.objectweb.asm.Type?>(
                java.util.Arrays.stream<org.objectweb.asm.Type?>(argumentTypes),
                java.util.stream.Stream.of<org.objectweb.asm.Type?>(org.objectweb.asm.Type.getType(kotlin.Int::class.javaPrimitiveType), org.objectweb.asm.Type.getType(kotlin.Any::class.java))
            ).toArray<org.objectweb.asm.Type?> { _Dummy_.__Array__() }
            return org.objectweb.asm.Type.getMethodDescriptor(returnType, *argumentTypesWithDefault)
        }
    }
}
