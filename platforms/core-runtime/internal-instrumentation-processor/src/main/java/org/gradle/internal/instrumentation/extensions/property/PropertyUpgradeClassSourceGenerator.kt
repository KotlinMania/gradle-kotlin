/*
 * Copyright 2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the \"License\");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an \"AS IS\" BASIS,
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
    override fun classNameForRequest(request: CallInterceptionRequest?): String? {
        if (request == null) {
            return null
        }
        return request.requestExtras.getByType(PropertyUpgradeRequestExtra::class.java)
            .map<String?>(Function { extra: PropertyUpgradeRequestExtra -> extra.implementationClassName })
            .orElse(null)
    }

    override fun classContentForClass(
        className: String?,
        requestsClassGroup: MutableList<CallInterceptionRequest?>?,
        onProcessedRequest: Consumer<in CallInterceptionRequest?>?,
        onFailure: Consumer<in FailureInfo?>?
    ): Consumer<TypeSpec.Builder?>? {
        val requests = requestsClassGroup ?: return null
        val onRequest = onProcessedRequest ?: return null
        val onFailureConsumer = onFailure ?: return null

        val methods = requests.stream()
            .filter { it != null }
            .map<MethodSpec> { request: CallInterceptionRequest? ->
                Companion.mapToMethodSpec(
                    request,
                    onRequest,
                    onFailureConsumer
                )
            }
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

        private fun mapToMethodSpec(
            request: CallInterceptionRequest?,
            onProcessedRequest: Consumer<in CallInterceptionRequest?>,
            onFailure: Consumer<in FailureInfo?>
        ): MethodSpec {
            val nonNullRequest = request ?: throw RuntimeException("Request should be present")
            val implementationExtra = nonNullRequest.requestExtras
                .getByType(PropertyUpgradeRequestExtra::class.java)
                .orElseThrow(
                    Supplier {
                        RuntimeException(PropertyUpgradeRequestExtra::class.java.getSimpleName() + " should be present at this stage!")
                    }
                )

            try {
                val implementation = nonNullRequest.implementationInfo ?: throw RuntimeException("Implementation info should be present")
                val callable = nonNullRequest.interceptedCallable ?: throw RuntimeException("Intercepted callable should be present")

                val spec: MethodSpec
                if (implementationExtra.bridgedMethodInfo != null) {
                    spec = mapToBridgedMethod(requireNotNull(implementation.name), implementationExtra, callable)
                } else {
                    val parameters = callable.parameters.orEmpty()
                        .map { parameter: ParameterInfo ->
                            ParameterSpec.builder(
                                TypeUtils.typeName(requireNotNull(parameter.parameterType)),
                                requireNotNull(parameter.name)
                            ).build()
                        }
                        spec = MethodSpec.methodBuilder(requireNotNull(implementation.name))
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .addParameter(requireNotNull(TypeUtils.typeName(requireNotNull(requireNotNull(callable.owner).type))), SELF_PARAMETER_NAME)
                        .addParameters(parameters)
                        .addCode(generateMethodBody(implementation, callable, implementationExtra))
                        .returns(requireNotNull(TypeUtils.typeName(requireNotNull(requireNotNull(callable.returnType).type))))
                        .addAnnotations(getAnnotations(implementationExtra))
                        .build()
                }

                onProcessedRequest.accept(nonNullRequest)
                return spec
            } catch (e: Exception) {
                onFailure.accept(FailureInfo(nonNullRequest, e.message))
                throw e
            }
        }

        private fun mapToBridgedMethod(methodName: String, implementationExtra: PropertyUpgradeRequestExtra, callable: CallableInfo): MethodSpec {
            val bridgedMethodInfo = requireNotNull(implementationExtra.bridgedMethodInfo)
            val bridgedMethod = requireNotNull(bridgedMethodInfo.bridgedMethod)
            val typeVariables = bridgedMethod.getTypeParameters().stream()
                .map<TypeVariableName?> { element: TypeParameterElement? -> TypeVariableName.get(element!!.asType() as TypeVariable?) }
                .collect(Collectors.toList())
            val parameters = bridgedMethod.getParameters().stream()
                .map<ParameterSpec?> { element: VariableElement? -> ParameterSpec.get(element!!) }
                .collect(Collectors.toList())
            val exceptions = bridgedMethod.getThrownTypes().stream()
                .map<TypeName?> { mirror: TypeMirror? -> TypeName.get(mirror!!) }
                .collect(Collectors.toList())
            val passedParameters = parameters.stream().map { parameterSpec: ParameterSpec? -> parameterSpec!!.name }.collect(Collectors.joining(", "))

            val bodyBuilder = CodeBlock.builder()
            if (implementationExtra.deprecationSpec?.isEnabled == true) {
                bodyBuilder.addStatement(getDeprecationCodeBlock(implementationExtra, callable))
            }

            val bridgeCall: CodeBlock
            val annotationSpecs = mutableListOf<AnnotationSpec>()
            if (bridgedMethodInfo.bridgeType == BridgeType.INSTANCE_METHOD_BRIDGE) {
                var type = TypeName.get(bridgedMethod.getEnclosingElement().asType())
                if (type is ParameterizedTypeName) {
                    // To simplify code generation we remove type parameters from the instance type, e.g.:
                    // if we have AbstractExecTask<T>, method parameter has type `AbstractExecTask` without generics
                    type = type.rawType
                    annotationSpecs.add(CodeGenUtils.SUPPRESS_UNCHECKED_AND_RAWTYPES)
                }
                parameters.add(0, ParameterSpec.builder(type, SELF_PARAMETER_NAME).build())
                bridgeCall = if (TypeName.get(bridgedMethod.getReturnType()) == TypeName.VOID) {
                    CodeBlock.of("\$N.\$N(\$L)", SELF_PARAMETER_NAME, bridgedMethod.getSimpleName(), passedParameters)
                } else {
                    CodeBlock.of("return \$N.\$N(\$L)", SELF_PARAMETER_NAME, bridgedMethod.getSimpleName(), passedParameters)
                }
            } else {
                bridgeCall = if (TypeName.get(bridgedMethod.getReturnType()) == TypeName.VOID) {
                    CodeBlock.of("\$T.\$N(\$L)", TypeName.get(bridgedMethod.getEnclosingElement().asType()), bridgedMethod.getSimpleName(), passedParameters)
                } else {
                    CodeBlock.of("return \$T.\$N(\$L)", TypeName.get(bridgedMethod.getEnclosingElement().asType()), bridgedMethod.getSimpleName(), passedParameters)
                }
            }
            bodyBuilder.addStatement(bridgeCall)

            return MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addAnnotations(annotationSpecs)
                .addTypeVariables(typeVariables)
                .addParameters(parameters)
                .returns(TypeName.get(bridgedMethod.getReturnType()))
                .addExceptions(exceptions)
                .varargs(bridgedMethod.isVarArgs())
                .addCode(bodyBuilder.build())
                .build()
        }

        private fun getAnnotations(implementationExtra: PropertyUpgradeRequestExtra): MutableList<AnnotationSpec> {
            val gradleLazyType = GradleLazyType.from(requireNotNull(implementationExtra.newPropertyType))
            return when (gradleLazyType) {
                GradleLazyType.LIST_PROPERTY, GradleLazyType.SET_PROPERTY, GradleLazyType.MAP_PROPERTY -> mutableListOf(CodeGenUtils.SUPPRESS_UNCHECKED_AND_RAWTYPES)
                GradleLazyType.PROVIDER -> if (implementationExtra.returnType is ParameterizedTypeName) mutableListOf(CodeGenUtils.SUPPRESS_UNCHECKED_AND_RAWTYPES) else mutableListOf()
                else -> mutableListOf()
            }
        }

        private fun generateMethodBody(
            implementation: ImplementationInfo,
            callableInfo: CallableInfo,
            implementationExtra: PropertyUpgradeRequestExtra
        ): CodeBlock {
            val propertyGetterName = requireNotNull(implementationExtra.methodName)
            val isSetter = implementation.name.orEmpty().startsWith("access_set_")
            val returnType = requireNotNull(callableInfo.returnType)
            val upgradedPropertyType = GradleLazyType.from(requireNotNull(implementationExtra.newPropertyType))

            val codeBlockBuilder = CodeBlock.builder()
            if (implementationExtra.deprecationSpec?.isEnabled == true) {
                codeBlockBuilder.addStatement(getDeprecationCodeBlock(implementationExtra, callableInfo))
            }

            val logic = if (isSetter) {
                generateSetCall(propertyGetterName, implementationExtra, upgradedPropertyType)
            } else {
                generateGetCall(propertyGetterName, implementationExtra, returnType, upgradedPropertyType)
            }
            return codeBlockBuilder.addStatement(logic).build()
        }

        private fun getDeprecationCodeBlock(requestExtra: PropertyUpgradeRequestExtra, callableInfo: CallableInfo): CodeBlock {
            val newPropertyName = requireNotNull(requestExtra.propertyName)
            val deprecatedPropertyName = requireNotNull(requestExtra.interceptedPropertyName)
            val deprecationSpec = requireNotNull(requestExtra.deprecationSpec)
            val className = requireNotNull(callableInfo.owner?.type).getClassName()
            val simpleClassName = className.substring(className.lastIndexOf(".") + 1)

            val deprecationBuilder: CodeBlock.Builder = when (deprecationSpec.removedIn) {
                ReplacedDeprecation.RemovedIn.GRADLE9 -> {
                    val message = String.format(
                        "The usage of %s.%s",
                        simpleClassName,
                        deprecatedPropertyName
                    )
                    CodeBlock.builder()
                        .add("\$T.deprecate(\$S)\n", GradleReferencedType.DEPRECATION_LOGGER.asClassName(), message)
                        .add(
                            ".withContext(\$S)\n",
                            String.format(
                                "Property '%s' was removed and this compatibility shim will be removed in Gradle 10. Please use '%s' property instead.",
                                deprecatedPropertyName,
                                newPropertyName
                            )
                        )
                        .add(".willBecomeAnErrorInGradle10()\\n")
                }
                ReplacedDeprecation.RemovedIn.UNSPECIFIED -> {
                    val builder = CodeBlock.builder()
                        .add(
                            "\$T.deprecateProperty(\$T.class, \$S)\n",
                            GradleReferencedType.DEPRECATION_LOGGER.asClassName(),
                            requireNotNull(TypeUtils.typeName(requireNotNull(requireNotNull(callableInfo.owner).type))),
                            deprecatedPropertyName
                        )
                        .add(".withContext(\$S)\n", "Property was automatically upgraded to the lazy version.")
                    if (newPropertyName != deprecatedPropertyName) {
                        builder.add(".replaceWith(\$S)\n", newPropertyName)
                    }
                    builder.add(".startingWithGradle10(\$S)\n", "this property is replaced with a lazy version")
                }
                else -> throw UnsupportedOperationException(
                    "Only " + ReplacedDeprecation.RemovedIn.UNSPECIFIED + " and " + ReplacedDeprecation.RemovedIn.GRADLE9 +
                        " are currently supported for removedIn, but was: " + deprecationSpec.removedIn
                )
            }

            if (deprecationSpec.withUpgradeGuideVersion != -1) {
                deprecationBuilder.add(".withUpgradeGuideSection(\$L, \$S)\n", deprecationSpec.withUpgradeGuideVersion, deprecationSpec.withUpgradeGuideSection)
            } else if (deprecationSpec.isWithDslReference) {
                deprecationBuilder.add(".withDslReference()\\n")
            } else {
                deprecationBuilder.add(".undocumented()\\n")
            }

            return deprecationBuilder.add(".nagUser()").build()
        }

        private fun generateGetCall(
            propertyGetterName: String,
            implementationExtra: PropertyUpgradeRequestExtra,
            returnType: CallableReturnTypeInfo,
            upgradedPropertyType: GradleLazyType
        ): CodeBlock {
            val returnAsmType = requireNotNull(returnType.type)
            return when (upgradedPropertyType) {
                GradleLazyType.REGULAR_FILE_PROPERTY, GradleLazyType.DIRECTORY_PROPERTY -> CodeBlock.of("return \$N.\$N().getAsFile().getOrNull()", SELF_PARAMETER_NAME, propertyGetterName)
                GradleLazyType.CONFIGURABLE_FILE_COLLECTION, GradleLazyType.FILE_COLLECTION -> CodeBlock.of("return \$N.\$N()", SELF_PARAMETER_NAME, propertyGetterName)
                GradleLazyType.LIST_PROPERTY -> CodeBlock.of("return new \$T<>(\$N.\$N())", GradleReferencedType.LIST_PROPERTY_LIST_VIEW.asClassName(), SELF_PARAMETER_NAME, propertyGetterName)
                GradleLazyType.SET_PROPERTY -> CodeBlock.of("return new \$T<>(\$N.\$N())", GradleReferencedType.SET_PROPERTY_SET_VIEW.asClassName(), SELF_PARAMETER_NAME, propertyGetterName)
                GradleLazyType.MAP_PROPERTY -> CodeBlock.of("return new \$T<>(\$N.\$N())", GradleReferencedType.MAP_PROPERTY_MAP_VIEW.asClassName(), SELF_PARAMETER_NAME, propertyGetterName)
                GradleLazyType.PROPERTY -> CodeBlock.of("return \$N.\$N().getOrElse(\$L)", SELF_PARAMETER_NAME, propertyGetterName, TypeUtils.getDefaultValue(returnAsmType))
                GradleLazyType.PROVIDER -> {
                    val providerParameter = TypeUtils.getTypeParameter(implementationExtra.newPropertyType, 0)
                    val mapsToFileSystemLocation =
                        if (providerParameter.isPresent && providerParameter.get() != null) {
                            GradleReferencedType.isAssignableToFileSystemLocation(providerParameter.get()!!)
                        } else {
                            false
                        }
                    if (mapsToFileSystemLocation) {
                        CodeBlock.of(
                            "return \$N.\$N().map(\$T::getAsFile).getOrNull()",
                            SELF_PARAMETER_NAME,
                            propertyGetterName,
                            GradleReferencedType.FILE_SYSTEM_LOCATION.asClassName()
                        )
                    } else {
                        CodeBlock.of("return \$N.\$N().getOrElse(\$L)", SELF_PARAMETER_NAME, propertyGetterName, TypeUtils.getDefaultValue(returnAsmType))
                    }
                }
                else -> throw UnsupportedOperationException("Generating get call for type: " + requireNotNull(upgradedPropertyType.asClassName()).reflectionName() + " is not supported")
            }
        }

        private fun generateSetCall(
            propertyGetterName: String,
            implementationExtra: PropertyUpgradeRequestExtra,
            upgradedPropertyType: GradleLazyType
        ): CodeBlock {
            val assignment = when (upgradedPropertyType) {
                GradleLazyType.REGULAR_FILE_PROPERTY, GradleLazyType.DIRECTORY_PROPERTY -> ".fileValue(arg0)"
                GradleLazyType.CONFIGURABLE_FILE_COLLECTION -> ".setFrom(arg0)"
                GradleLazyType.LIST_PROPERTY, GradleLazyType.SET_PROPERTY, GradleLazyType.MAP_PROPERTY, GradleLazyType.PROPERTY -> ".set(arg0)"
                GradleLazyType.PROVIDER -> throw UnsupportedOperationException("Generating set call for type: " + requireNotNull(upgradedPropertyType.asClassName()).reflectionName() + " is not supported")
                else -> throw UnsupportedOperationException("Generating set call for type: " + requireNotNull(upgradedPropertyType.asClassName()).reflectionName() + " is not supported")
            }
            return if (implementationExtra.returnType == TypeName.VOID) {
                CodeBlock.of("\$N.\$N()\$N", SELF_PARAMETER_NAME, propertyGetterName, assignment)
            } else {
                CodeBlock.of("\$N.\$N()\$N;\nreturn \$N", SELF_PARAMETER_NAME, propertyGetterName, assignment, SELF_PARAMETER_NAME)
            }
        }
    }
}
