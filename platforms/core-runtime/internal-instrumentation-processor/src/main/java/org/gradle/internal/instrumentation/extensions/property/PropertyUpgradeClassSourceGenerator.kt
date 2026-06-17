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
package org.gradle.internal.instrumentation.extensions.property

import com.squareup.javapoet.AnnotationSpec
import com.squareup.javapoet.CodeBlock
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterSpec
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.TypeSpec
import com.squareup.javapoet.TypeVariableName
import org.gradle.internal.instrumentation.api.annotations.ReplacedDeprecation
import org.gradle.internal.instrumentation.extensions.property.PropertyUpgradeRequestExtra.BridgedMethodInfo.BridgeType
import org.gradle.internal.instrumentation.model.CallInterceptionRequest
import org.gradle.internal.instrumentation.model.CallableInfo
import org.gradle.internal.instrumentation.model.CallableReturnTypeInfo
import org.gradle.internal.instrumentation.model.ImplementationInfo
import org.gradle.internal.instrumentation.model.ParameterInfo
import org.gradle.internal.instrumentation.processor.codegen.CodeGenUtils
import org.gradle.internal.instrumentation.processor.codegen.GradleLazyType
import org.gradle.internal.instrumentation.processor.codegen.GradleReferencedType
import org.gradle.internal.instrumentation.processor.codegen.HasFailures.FailureInfo
import org.gradle.internal.instrumentation.processor.codegen.RequestGroupingInstrumentationClassSourceGenerator
import org.gradle.internal.instrumentation.processor.codegen.TypeUtils
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Collectors
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeParameterElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.TypeMirror
import javax.lang.model.type.TypeVariable

class PropertyUpgradeClassSourceGenerator : RequestGroupingInstrumentationClassSourceGenerator() {
    override fun classNameForRequest(request: CallInterceptionRequest): String? {
        return request.requestExtras.getByType<PropertyUpgradeRequestExtra?>(PropertyUpgradeRequestExtra::class.java)
            .map<String?>(Function { obj: PropertyUpgradeRequestExtra? -> obj!!.getImplementationClassName() })
            .orElse(null)
    }

    override fun classContentForClass(
        className: String?,
        requestsClassGroup: MutableList<CallInterceptionRequest?>,
        onProcessedRequest: Consumer<in CallInterceptionRequest?>,
        onFailure: Consumer<in FailureInfo?>
    ): Consumer<TypeSpec.Builder?> {
        val methods = requestsClassGroup.stream()
            .map<MethodSpec?> { request: CallInterceptionRequest? -> Companion.mapToMethodSpec(request!!, onProcessedRequest, onFailure) }
            .collect(Collectors.toList())

        return Consumer { builder: TypeSpec.Builder? ->
            builder!!
                .addAnnotation(GradleReferencedType.GENERATED_ANNOTATION.asClassName())
                .addAnnotation(CodeGenUtils.SUPPRESS_DEPRECATIONS)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addJavadoc("Auto generated class. Should not be used directly.")
                .addMethods(methods)
        }
    }

    companion object {
        private const val SELF_PARAMETER_NAME = "self"

        private fun mapToMethodSpec(request: CallInterceptionRequest, onProcessedRequest: Consumer<in CallInterceptionRequest?>, onFailure: Consumer<in FailureInfo?>): MethodSpec {
            val implementationExtra = request.requestExtras
                .getByType<PropertyUpgradeRequestExtra>(PropertyUpgradeRequestExtra::class.java)
                .orElseThrow<RuntimeException?>(Supplier { RuntimeException(PropertyUpgradeRequestExtra::class.java.getSimpleName() + " should be present at this stage!") })

            try {
                val implementation = request.implementationInfo
                val callable = request.interceptedCallable

                val spec: MethodSpec
                if (implementationExtra.getBridgedMethodInfo() != null) {
                    spec = mapToBridgedMethod(implementation.name, implementationExtra, callable)
                } else {
                    val parameters = callable.parameters.stream()
                        .map<ParameterSpec?> { parameter: ParameterInfo? -> ParameterSpec.builder(TypeUtils.typeName(parameter!!.parameterType), parameter.name).build() }
                        .collect(Collectors.toList())
                    spec = MethodSpec.methodBuilder(implementation.name)
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .addParameter(TypeUtils.typeName(callable.owner.type), SELF_PARAMETER_NAME)
                        .addParameters(parameters)
                        .addCode(generateMethodBody(implementation, callable, implementationExtra))
                        .returns(TypeUtils.typeName(callable.returnType.type))
                        .addAnnotations(getAnnotations(implementationExtra))
                        .build()
                }

                onProcessedRequest.accept(request)
                return spec
            } catch (e: Exception) {
                onFailure.accept(FailureInfo(request, e.message))
                throw e
            }
        }

        private fun mapToBridgedMethod(methodName: String, implementationExtra: PropertyUpgradeRequestExtra, callable: CallableInfo): MethodSpec {
            val bridgedMethodInfo = implementationExtra.getBridgedMethodInfo()
            val bridgedMethod = bridgedMethodInfo.getBridgedMethod()
            val typeVariables = bridgedMethod.getTypeParameters().stream()
                .map<TypeVariableName?> { element: TypeParameterElement? -> TypeVariableName.get(element!!.asType() as TypeVariable?) }
                .collect(Collectors.toList())
            val parameters = bridgedMethod.getParameters().stream()
                .map<ParameterSpec?> { element: VariableElement? -> ParameterSpec.get(element) }
                .collect(Collectors.toList())
            val exceptions = bridgedMethod.getThrownTypes().stream()
                .map<TypeName?> { mirror: TypeMirror? -> TypeName.get(mirror) }
                .collect(Collectors.toList())
            val passedParameters = parameters.stream()
                .map<String?> { parameterSpec: ParameterSpec? -> parameterSpec!!.name }
                .collect(Collectors.joining(", "))

            val bodyBuilder = CodeBlock.builder()
            if (implementationExtra.getDeprecationSpec().isEnabled()) {
                bodyBuilder.addStatement(getDeprecationCodeBlock(implementationExtra, callable))
            }

            val bridgeCall: CodeBlock?
            var annotationSpecs = mutableListOf<AnnotationSpec?>()
            if (bridgedMethodInfo.getBridgeType() == BridgeType.INSTANCE_METHOD_BRIDGE) {
                var type = TypeName.get(bridgedMethod.getEnclosingElement().asType())
                if (type is ParameterizedTypeName) {
                    // To simplify code generation we remove type parameters from the instance type, e.g.:
                    // if we have AbstractExecTask<T>, method parameter has type `AbstractExecTask` without generics
                    type = type.rawType
                    annotationSpecs = mutableListOf<AnnotationSpec?>(CodeGenUtils.SUPPRESS_UNCHECKED_AND_RAWTYPES)
                }
                parameters.add(0, ParameterSpec.builder(type, SELF_PARAMETER_NAME).build())
                bridgeCall = if (TypeName.get(bridgedMethod.getReturnType()) == TypeName.VOID)
                    CodeBlock.of("\$L.\$N(\$L)", SELF_PARAMETER_NAME, bridgedMethod.getSimpleName(), passedParameters)
                else
                    CodeBlock.of("return \$L.\$N(\$L)", SELF_PARAMETER_NAME, bridgedMethod.getSimpleName(), passedParameters)
            } else {
                bridgeCall = if (TypeName.get(bridgedMethod.getReturnType()) == TypeName.VOID)
                    CodeBlock.of("\$T.\$N(\$L)", TypeName.get(bridgedMethod.getEnclosingElement().asType()), bridgedMethod.getSimpleName(), passedParameters)
                else
                    CodeBlock.of("return \$T.\$N(\$L)", TypeName.get(bridgedMethod.getEnclosingElement().asType()), bridgedMethod.getSimpleName(), passedParameters)
            }
            bodyBuilder.addStatement(bridgeCall)

            return MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addAnnotations(annotationSpecs)
                .addTypeVariables(typeVariables)
                .addParameters(parameters)
                .returns(TypeName.get(bridgedMethod.getReturnType()))
                .varargs(bridgedMethod.isVarArgs())
                .addExceptions(exceptions)
                .addCode(bodyBuilder.build())
                .build()
        }

        private fun getAnnotations(implementationExtra: PropertyUpgradeRequestExtra): MutableList<AnnotationSpec?> {
            val gradleLazyType = GradleLazyType.from(implementationExtra.getNewPropertyType())
            when (gradleLazyType) {
                GradleLazyType.LIST_PROPERTY, GradleLazyType.SET_PROPERTY, GradleLazyType.MAP_PROPERTY -> return mutableListOf<AnnotationSpec?>(CodeGenUtils.SUPPRESS_UNCHECKED_AND_RAWTYPES)
                GradleLazyType.PROVIDER -> return if (implementationExtra.getReturnType() is ParameterizedTypeName) mutableListOf<AnnotationSpec?>(CodeGenUtils.SUPPRESS_UNCHECKED_AND_RAWTYPES) else mutableListOf<AnnotationSpec?>()
                else -> return mutableListOf<AnnotationSpec?>()
            }
        }

        private fun generateMethodBody(implementation: ImplementationInfo, callableInfo: CallableInfo, implementationExtra: PropertyUpgradeRequestExtra): CodeBlock {
            val propertyGetterName = implementationExtra.getMethodName()
            val isSetter = implementation.name.startsWith("access_set_")
            val returnType = callableInfo.returnType
            val upgradedPropertyType = GradleLazyType.from(implementationExtra.getNewPropertyType())

            val codeBlockBuilder = CodeBlock.builder()
            if (implementationExtra.getDeprecationSpec().isEnabled()) {
                codeBlockBuilder.addStatement(getDeprecationCodeBlock(implementationExtra, callableInfo))
            }

            val logic: CodeBlock? = if (isSetter)
                generateSetCall(propertyGetterName, implementationExtra, upgradedPropertyType)
            else
                generateGetCall(propertyGetterName, implementationExtra, returnType, upgradedPropertyType)

            return codeBlockBuilder.addStatement(logic).build()
        }

        private fun getDeprecationCodeBlock(requestExtra: PropertyUpgradeRequestExtra, callableInfo: CallableInfo): CodeBlock {
            val newPropertyName = requestExtra.getPropertyName()
            val deprecatedPropertyName = requestExtra.getInterceptedPropertyName()
            val deprecationSpec = requestExtra.getDeprecationSpec()

            val deprecationBuilder: CodeBlock.Builder?
            when (deprecationSpec.getRemovedIn()) {
                ReplacedDeprecation.RemovedIn.GRADLE9 -> {
                    val className = callableInfo.owner.type.getClassName()
                    val simpleClassName = className.substring(className.lastIndexOf(".") + 1)
                    deprecationBuilder = CodeBlock.builder()
                        .add("\$T.deprecate(\$S)\n", GradleReferencedType.DEPRECATION_LOGGER.asClassName(), String.format("The usage of %s.%s", simpleClassName, deprecatedPropertyName))
                        .add(
                            ".withContext(\$S)\n",
                            String.format(
                                "Property '%s' was removed and this compatibility shim will be removed in Gradle 10. Please use '%s' property instead.",
                                deprecatedPropertyName,
                                newPropertyName
                            )
                        )
                        .add(".willBecomeAnErrorInGradle10()\n")
                }

                ReplacedDeprecation.RemovedIn.UNSPECIFIED -> {
                    deprecationBuilder = CodeBlock.builder()
                        .add(
                            "\$T.deprecateProperty(\$T.class, \$S)\n",
                            GradleReferencedType.DEPRECATION_LOGGER.asClassName(),
                            TypeUtils.typeName(callableInfo.owner.type),
                            deprecatedPropertyName
                        )
                        .add(".withContext(\$S)\n", "Property was automatically upgraded to the lazy version.")
                    if (newPropertyName != deprecatedPropertyName) {
                        deprecationBuilder.add(".replaceWith(\$S)\n", newPropertyName)
                    }
                    deprecationBuilder.add(".startingWithGradle10(\$S)\n", "this property is replaced with a lazy version")
                }

                else -> throw UnsupportedOperationException("Only " + ReplacedDeprecation.RemovedIn.UNSPECIFIED + " and " + ReplacedDeprecation.RemovedIn.GRADLE9 + " are currently supported for removedIn, but was: " + deprecationSpec.getRemovedIn())
            }

            if (deprecationSpec.getWithUpgradeGuideVersion() != -1) {
                deprecationBuilder.add(".withUpgradeGuideSection(\$L, \$S)\n", deprecationSpec.getWithUpgradeGuideVersion(), deprecationSpec.getWithUpgradeGuideSection())
            } else if (deprecationSpec.isWithDslReference()) {
                deprecationBuilder.add(".withDslReference()\n")
            } else {
                deprecationBuilder.add(".undocumented()\n")
            }

            return deprecationBuilder.add(".nagUser()")
                .build()
        }

        private fun generateGetCall(
            propertyGetterName: String?,
            implementationExtra: PropertyUpgradeRequestExtra,
            returnType: CallableReturnTypeInfo,
            upgradedPropertyType: GradleLazyType
        ): CodeBlock? {
            when (upgradedPropertyType) {
                GradleLazyType.REGULAR_FILE_PROPERTY, GradleLazyType.DIRECTORY_PROPERTY -> return CodeBlock.of("return \$N.\$N().getAsFile().getOrNull()", SELF_PARAMETER_NAME, propertyGetterName)
                GradleLazyType.CONFIGURABLE_FILE_COLLECTION, GradleLazyType.FILE_COLLECTION -> return CodeBlock.of("return \$N.\$N()", SELF_PARAMETER_NAME, propertyGetterName)
                GradleLazyType.LIST_PROPERTY -> return CodeBlock.of("return new \$T<>(\$N.\$N())", GradleReferencedType.LIST_PROPERTY_LIST_VIEW.asClassName(), SELF_PARAMETER_NAME, propertyGetterName)
                GradleLazyType.SET_PROPERTY -> return CodeBlock.of("return new \$T<>(\$N.\$N())", GradleReferencedType.SET_PROPERTY_SET_VIEW.asClassName(), SELF_PARAMETER_NAME, propertyGetterName)
                GradleLazyType.MAP_PROPERTY -> return CodeBlock.of("return new \$T<>(\$N.\$N())", GradleReferencedType.MAP_PROPERTY_MAP_VIEW.asClassName(), SELF_PARAMETER_NAME, propertyGetterName)
                GradleLazyType.PROPERTY -> return CodeBlock.of("return \$N.\$N().getOrElse(\$L)", SELF_PARAMETER_NAME, propertyGetterName, TypeUtils.getDefaultValue(returnType.type))
                GradleLazyType.PROVIDER -> {
                    val providerParameter = TypeUtils.getTypeParameter(implementationExtra.getNewPropertyType(), 0)
                    return if (providerParameter.map<Boolean?>(Function { typeName: TypeName? -> GradleReferencedType.isAssignableToFileSystemLocation(typeName) }).orElse(false))
                        CodeBlock.of("return \$N.\$N().map(\$T::getAsFile).getOrNull()", SELF_PARAMETER_NAME, propertyGetterName, GradleReferencedType.FILE_SYSTEM_LOCATION.asClassName())
                    else
                        CodeBlock.of("return \$N.\$N().getOrElse(\$L)", SELF_PARAMETER_NAME, propertyGetterName, TypeUtils.getDefaultValue(returnType.type))
                }

                else -> throw UnsupportedOperationException("Generating get call for type: " + upgradedPropertyType.asClassName().reflectionName() + " is not supported")
            }
        }

        private fun generateSetCall(propertyGetterName: String?, implementationExtra: PropertyUpgradeRequestExtra, upgradedPropertyType: GradleLazyType): CodeBlock? {
            val assignment: String?
            when (upgradedPropertyType) {
                GradleLazyType.REGULAR_FILE_PROPERTY, GradleLazyType.DIRECTORY_PROPERTY -> assignment = ".fileValue(arg0)"
                GradleLazyType.CONFIGURABLE_FILE_COLLECTION -> assignment = ".setFrom(arg0)"
                GradleLazyType.LIST_PROPERTY, GradleLazyType.SET_PROPERTY, GradleLazyType.MAP_PROPERTY, GradleLazyType.PROPERTY -> assignment = ".set(arg0)"
                GradleLazyType.PROVIDER -> throw UnsupportedOperationException("Generating set call for type: " + upgradedPropertyType.asClassName().reflectionName() + " is not supported")
                else -> throw UnsupportedOperationException("Generating set call for type: " + upgradedPropertyType.asClassName().reflectionName() + " is not supported")
            }
            if (implementationExtra.getReturnType() == TypeName.VOID) {
                return CodeBlock.of("\$N.\$N()\$N", SELF_PARAMETER_NAME, propertyGetterName, assignment)
            } else {
                return CodeBlock.of("\$N.\$N()\$N;\nreturn \$N", SELF_PARAMETER_NAME, propertyGetterName, assignment, SELF_PARAMETER_NAME)
            }
        }
    }
}
