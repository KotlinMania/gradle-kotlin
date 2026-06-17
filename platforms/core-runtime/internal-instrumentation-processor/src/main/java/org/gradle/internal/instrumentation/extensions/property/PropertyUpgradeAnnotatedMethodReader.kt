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

import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Multimap
import com.google.common.collect.Multimaps
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeName
import org.gradle.internal.instrumentation.api.annotations.BytecodeUpgrade
import org.gradle.internal.instrumentation.api.annotations.ReplacedAccessor
import org.gradle.internal.instrumentation.api.annotations.ReplacedDeprecation
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty
import org.gradle.internal.instrumentation.api.declarations.InterceptorDeclaration.GROOVY_INTERCEPTORS_GENERATED_CLASS_NAME_FOR_PROPERTY_UPGRADES
import org.gradle.internal.instrumentation.api.declarations.InterceptorDeclaration.GROOVY_INTERCEPTORS_GENERATED_CLASS_NAME_FOR_PROPERTY_UPGRADES_REPORT
import org.gradle.internal.instrumentation.api.declarations.InterceptorDeclaration.JVM_BYTECODE_GENERATED_CLASS_NAME_FOR_PROPERTY_UPGRADES
import org.gradle.internal.instrumentation.api.declarations.InterceptorDeclaration.JVM_BYTECODE_GENERATED_CLASS_NAME_FOR_PROPERTY_UPGRADES_REPORT
import org.gradle.internal.instrumentation.api.types.BytecodeInterceptorType
import org.gradle.internal.instrumentation.extensions.property.PropertyUpgradeAnnotatedMethodReader.Companion.getPropertyName
import org.gradle.internal.instrumentation.extensions.property.PropertyUpgradeRequestExtra.BridgedMethodInfo
import org.gradle.internal.instrumentation.extensions.property.PropertyUpgradeRequestExtra.BridgedMethodInfo.BridgeType
import org.gradle.internal.instrumentation.model.CallInterceptionRequest
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
import org.gradle.internal.instrumentation.processor.AbstractInstrumentationProcessor
import org.gradle.internal.instrumentation.processor.codegen.GradleLazyType
import org.gradle.internal.instrumentation.processor.codegen.GradleReferencedType
import org.gradle.internal.instrumentation.processor.extensibility.AnnotatedMethodReaderExtension
import org.gradle.internal.instrumentation.processor.modelreader.api.CallInterceptionRequestReader
import org.gradle.internal.instrumentation.processor.modelreader.api.CallInterceptionRequestReader.ReadRequestContext
import org.gradle.internal.instrumentation.processor.modelreader.api.CallInterceptionRequestReader.Result.InvalidRequest
import org.gradle.internal.instrumentation.processor.modelreader.api.CallInterceptionRequestReader.Result.Success
import org.gradle.internal.instrumentation.processor.modelreader.impl.AnnotationUtils
import org.gradle.internal.instrumentation.processor.modelreader.impl.TypeUtils
import org.objectweb.asm.Type
import java.io.File
import java.util.Arrays
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Collectors
import java.util.stream.Stream
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.Element
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.VariableElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Elements
import javax.lang.model.util.Types
import kotlin.collections.dropLastWhile
import kotlin.collections.mutableListOf
import kotlin.collections.plus
import kotlin.collections.toTypedArray
import kotlin.plus
import kotlin.sequences.plus
import kotlin.text.format
import kotlin.text.isEmpty
import kotlin.text.lowercaseChar
import kotlin.text.plus
import kotlin.text.replaceFirst
import kotlin.text.split
import kotlin.text.startsWith
import kotlin.text.substring
import kotlin.text.toRegex
import kotlin.text.uppercase

class PropertyUpgradeAnnotatedMethodReader(processingEnv: ProcessingEnvironment) : AnnotatedMethodReaderExtension {
    private val projectName: String?
    private val elements: Elements
    private val types: Types

    init {
        this.projectName = getProjectName(processingEnv)
        this.elements = processingEnv.getElementUtils()
        this.types = processingEnv.getTypeUtils()
    }

    private fun getGroovyInterceptorsClassName(interceptorType: BytecodeInterceptorType): String {
        when (interceptorType) {
            BytecodeInterceptorType.BYTECODE_UPGRADE -> return GROOVY_INTERCEPTORS_GENERATED_CLASS_NAME_FOR_PROPERTY_UPGRADES + "_" + projectName
            BytecodeInterceptorType.BYTECODE_UPGRADE_REPORT -> return GROOVY_INTERCEPTORS_GENERATED_CLASS_NAME_FOR_PROPERTY_UPGRADES_REPORT + "_" + projectName
            BytecodeInterceptorType.INSTRUMENTATION -> throw IllegalArgumentException("Unsupported interceptor type: " + interceptorType)
            else -> throw IllegalArgumentException("Unsupported interceptor type: " + interceptorType)
        }
    }

    private fun getJavaInterceptorsClassName(interceptorType: BytecodeInterceptorType): String {
        when (interceptorType) {
            BytecodeInterceptorType.BYTECODE_UPGRADE -> return JVM_BYTECODE_GENERATED_CLASS_NAME_FOR_PROPERTY_UPGRADES + "_" + projectName
            BytecodeInterceptorType.BYTECODE_UPGRADE_REPORT -> return JVM_BYTECODE_GENERATED_CLASS_NAME_FOR_PROPERTY_UPGRADES_REPORT + "_" + projectName
            BytecodeInterceptorType.INSTRUMENTATION -> throw IllegalArgumentException("Unsupported interceptor type: " + interceptorType)
            else -> throw IllegalArgumentException("Unsupported interceptor type: " + interceptorType)
        }
    }

    override fun readRequest(method: ExecutableElement?, context: ReadRequestContext?): MutableCollection<CallInterceptionRequestReader.Result?>? {
        if (method == null || context == null) {
            return mutableSetOf()
        }

        var annotation = AnnotationUtils.findAnnotationMirror(method, ReplacesEagerProperty::class.java)
        if (!annotation.isPresent()) {
            annotation = AnnotationUtils.findAnnotationMirror(method, ToBeReplacedByLazyProperty::class.java)
        }
        if (!annotation.isPresent()) {
            return mutableSetOf()
        }

        val annotationMirror: AnnotationMirror = annotation.get()
        if (projectName == null) {
            // We validate project name here because we want to fail only if there is an @ReplacesEagerProperty annotation used in the project
            return mutableListOf<CallInterceptionRequestReader.Result?>(InvalidRequest("Project name is not specified or is empty. Use -A" + AbstractInstrumentationProcessor.PROJECT_NAME_OPTIONS + "=<projectName> compiler option to set the project name."))
        } else if (AnnotationUtils.isAnnotationOfType(annotationMirror, ReplacesEagerProperty::class.java) && (!method.getParameters().isEmpty() || !method.getSimpleName().toString()
                .startsWith("get"))
        ) {
            return mutableListOf<CallInterceptionRequestReader.Result?>(
                InvalidRequest(
                    String.format(
                        "Method '%s.%s' annotated with @ReplacesEagerProperty should be a simple getter: name should start with 'get' and method should not have any parameters.",
                        method.getEnclosingElement(),
                        method
                    )
                )
            )
        }

        try {
            val accessorSpecs = readAccessorSpecsFromReplacesEagerProperty(method, annotationMirror, context)
            val requests: MutableList<CallInterceptionRequest> = mutableListOf()
            val groovyUpgradeAccessorSpecs: MutableMap<String, MutableList<AccessorSpec>> = LinkedHashMap<String, MutableList<AccessorSpec>>()
            for (accessorSpec in accessorSpecs) {
                if (accessorSpec.interceptorType == BytecodeInterceptorType.BYTECODE_UPGRADE) {
                    groovyUpgradeAccessorSpecs.computeIfAbsent(accessorSpec.propertyName) { java.util.ArrayList() }.add(accessorSpec)
                }
                requests.add(createJvmInterceptionRequest(method, accessorSpec))
            }
            val groovyRequests = groovyUpgradeAccessorSpecs.values.stream()
                .flatMap { specs: MutableList<AccessorSpec> -> createGroovyPropertyInterceptionRequests(specs, method).stream() }
                .collect(Collectors.toList())
            requests.addAll(groovyRequests)
            return requests.map { request -> Success(request) }.toMutableList()
        } catch (failure: IllegalArgumentException) {
            return mutableListOf<CallInterceptionRequestReader.Result?>(InvalidRequest(failure.message))
        }
    }

    private fun createGroovyPropertyInterceptionRequests(accessors: MutableList<AccessorSpec>, method: ExecutableElement): MutableList<CallInterceptionRequest> {
        val requests: MutableList<CallInterceptionRequest> = mutableListOf()

        val groovyPropertyGetter = accessors.stream()
            .filter { accessor: AccessorSpec -> Companion.isGroovyPropertyGetter(accessor, accessors) }
            .findFirst()
        groovyPropertyGetter.ifPresent(Consumer { getter: AccessorSpec -> requests.add(createGroovyPropertyInterceptionRequest(getter, CallableKindInfo.GROOVY_PROPERTY_GETTER, method)) })

        for (accessor in accessors) {
            if (groovyPropertyGetter.isPresent() && accessor === groovyPropertyGetter.get()) {
                continue
            }
            // Identify property setters only methods that match a property getter
            val callableKindInfo =
                if (groovyPropertyGetter.isPresent() && isGroovyPropertySetter(accessor, groovyPropertyGetter.get())) CallableKindInfo.GROOVY_PROPERTY_SETTER else CallableKindInfo.INSTANCE_METHOD
            requests.add(createGroovyPropertyInterceptionRequest(accessor, callableKindInfo, method))
        }

        return requests
    }

    private fun createGroovyPropertyInterceptionRequest(accessor: AccessorSpec, callableKindInfo: CallableKindInfo, method: ExecutableElement): CallInterceptionRequest {
        val callableMethodName = if (callableKindInfo == CallableKindInfo.GROOVY_PROPERTY_GETTER || callableKindInfo == CallableKindInfo.GROOVY_PROPERTY_SETTER)
            accessor.propertyName
        else
            accessor.methodName
        val implementationMethodPrefix = if (accessor.accessorType == ReplacedAccessor.AccessorType.GETTER) "get" else "set"
        val interceptorsClassName = getGroovyInterceptorsClassName(accessor.interceptorType)
        val extras = Arrays.asList<RequestExtra?>(OriginatingElement(method), RequestExtra.InterceptGroovyCalls(interceptorsClassName, accessor.interceptorType))
        val callableParameters: MutableList<ParameterInfo> = prependReceiverParameter(accessor.parameters, TypeUtils.extractType(method.getEnclosingElement().asType()))
        val returnType = requireNotNull(TypeUtils.extractRawType(accessor.returnType))
        return CallInterceptionRequestImpl(
            extractCallableInfo(callableKindInfo, method, returnType, callableMethodName, callableParameters),
            extractImplementationInfo(accessor, method, returnType, accessor.methodName, implementationMethodPrefix, accessor.parameters),
            extras
        )
    }

    private fun readAccessorSpecsFromReplacesEagerProperty(method: ExecutableElement, annotationMirror: AnnotationMirror, context: ReadRequestContext): MutableList<AccessorSpec> {
        if (AnnotationUtils.isAnnotationOfType(annotationMirror, ToBeReplacedByLazyProperty::class.java)) {
            return readAccessorSpecsFromToBeReplacedByLazyProperty(method, annotationMirror, context)
        }

        val element = AnnotationUtils.findAnnotationValueWithDefaults(elements, annotationMirror, "adapter")
            .map<Element> { v: AnnotationValue? -> types.asElement(v!!.getValue() as TypeMirror?) }
            .orElseThrow<IllegalArgumentException?>(Supplier { IllegalArgumentException("Missing adapter value") })
        if (element.getSimpleName().toString() != ReplacesEagerProperty.DefaultValue::class.java.getSimpleName()) {
            return readAccessorSpecsFromAdapter(element, method.getEnclosingElement(), annotationMirror)
        }

        val replacedAccessors = AnnotationUtils.findAnnotationValueWithDefaults(elements, annotationMirror, "replacedAccessors")
            .map<MutableList<AnnotationMirror?>> { v: AnnotationValue? -> v!!.getValue() as MutableList<AnnotationMirror?>? }
            .orElseThrow<AnnotationReadFailure?>(Supplier { AnnotationReadFailure(String.format("Missing 'replacedAccessors' attribute in @%s", ReplacesEagerProperty::class.java.getSimpleName())) })
        if (!replacedAccessors.isEmpty()) {
            val parentDeprecationSpec = readDeprecationSpec(annotationMirror)
            val parentBinaryCompatibility = readBinaryCompatibility(annotationMirror)
            return replacedAccessors.stream()
                .map<AccessorSpec> { annotation: AnnotationMirror? ->
                    getAccessorSpec(method, annotation!!, parentDeprecationSpec, parentBinaryCompatibility)
                }
                .collect(Collectors.toList())
        }

        // Provider has only a getter, no setter
        if (GradleLazyType.PROVIDER.isEqualToRawTypeOf(TypeName.get(method.getReturnType()))) {
            return mutableListOf<AccessorSpec>(getAccessorSpec(method, ReplacedAccessor.AccessorType.GETTER, annotationMirror))
        }
        return Arrays.asList<AccessorSpec>(
            getAccessorSpec(method, ReplacedAccessor.AccessorType.GETTER, annotationMirror),
            getAccessorSpec(method, ReplacedAccessor.AccessorType.SETTER, annotationMirror)
        )
    }

    private fun readAccessorSpecsFromToBeReplacedByLazyProperty(annotatedMethod: ExecutableElement, annotation: AnnotationMirror, context: ReadRequestContext): MutableList<AccessorSpec> {
        val skipForReport = AnnotationUtils.findAnnotationValueWithDefaults(elements, annotation, "unreported")
            .map<Boolean> { v: AnnotationValue? -> v!!.getValue() as Boolean }
            .orElseThrow<AnnotationReadFailure?>(Supplier { AnnotationReadFailure(String.format("Missing 'unreported' attribute in @%s", ToBeReplacedByLazyProperty::class.java.getSimpleName())) })
        if (skipForReport == true) {
            return mutableListOf<AccessorSpec>()
        }

        val propertyName: String = getPropertyName(annotatedMethod)
        val settersKey: String = TO_BE_REPLACED_SETTERS_KEY_PREFIX + annotatedMethod.getEnclosingElement().asType().toString()
        val propertySettersVisitedKey: String = TO_BE_REPLACED_SETTERS_VISITED_KEY_PREFIX + annotatedMethod.getEnclosingElement().asType().toString()
        val setters: MutableCollection<ExecutableElement?>
        val propertySettersVisited: MutableSet<String> = context.computeIfAbsent<HashSet<String>>(propertySettersVisitedKey, Function { key: String? -> HashSet<String>() })
        if (isSetterMethodName(annotatedMethod.getSimpleName().toString()) || !propertySettersVisited.add(propertyName)) {
            // If setter is annotated we should not visit other setters
            // also some booleans have two getters, is and get getter, so lets visit setters only once.
            setters = mutableListOf<ExecutableElement?>()
        } else {
            setters = context.computeIfAbsent<Multimap<String, ExecutableElement?>>(settersKey, Function { key: String? -> getAllSetters(annotatedMethod.getEnclosingElement()) }).get(propertyName)
        }

        val deprecationSpec = DeprecationSpec(false, ReplacedDeprecation.RemovedIn.UNSPECIFIED, -1, "", false)
        val generatedClassName = "org.gradle.internal.classpath.generated." + annotatedMethod.getEnclosingElement().getSimpleName() + "_ReportingAdapter"
        return Stream.concat<ExecutableElement?>(Stream.of<ExecutableElement?>(annotatedMethod), setters.stream())
            .filter { method: ExecutableElement? -> method != null }
            .map<AccessorSpec> { method: ExecutableElement? ->
                Companion.bridgedMethodToAccessorSpec(
                    method!!,
                    generatedClassName,
                    BridgeType.INSTANCE_METHOD_BRIDGE,
                    deprecationSpec,
                    ReplacesEagerProperty.BinaryCompatibility.ACCESSORS_KEPT,
                    BytecodeInterceptorType.BYTECODE_UPGRADE_REPORT
                )
            }
            .collect(Collectors.toList())
    }

    private fun readAccessorSpecsFromAdapter(adapter: Element, upgradedElement: Element, annotationMirror: AnnotationMirror): MutableList<AccessorSpec> {
        val bridgedMethods = TypeUtils.getExecutableElementsFromElements(Stream.of<Element>(adapter)).stream()
            .filter { method: ExecutableElement? -> method != null && method.getAnnotation<BytecodeUpgrade?>(BytecodeUpgrade::class.java) != null }
            .map { method: ExecutableElement? -> method!! }
            .collect(Collectors.toList())
        validateBridgedMethods(adapter, upgradedElement, bridgedMethods)

        return bridgedMethods.stream()
            .map<AccessorSpec> { method: ExecutableElement -> adapterBridgedMethodToAccessorSpec(method, annotationMirror) }
            .collect(Collectors.toList())
    }

    private fun adapterBridgedMethodToAccessorSpec(method: ExecutableElement, annotationMirror: AnnotationMirror): AccessorSpec {
        val innerClass = method.getEnclosingElement()
        val topClass = innerClass.getEnclosingElement()
        val packageElement = elements.getPackageOf(innerClass)

        // Using $$, since internal classes types has $ and due to
        // that we have some problems translating from asm Type to javapoet TypeName
        val generatedClassName = String.format(
            "%s.$\$BridgeFor$$%s$$%s",
            packageElement.getQualifiedName().toString(),
            topClass.getSimpleName().toString(),
            innerClass.getSimpleName().toString()
        )

        val deprecationSpec = readDeprecationSpec(annotationMirror)
        val binaryCompatibility = readBinaryCompatibility(annotationMirror)
        return bridgedMethodToAccessorSpec(
            method,
            generatedClassName,
            BridgeType.ADAPTER_METHOD_BRIDGE,
            deprecationSpec,
            binaryCompatibility,
            BytecodeInterceptorType.BYTECODE_UPGRADE
        )
    }

    private fun readDeprecationSpec(annotation: AnnotationMirror): DeprecationSpec {
        val deprecation = AnnotationUtils.findAnnotationValueWithDefaults(elements, annotation, "deprecation")
            .map<AnnotationMirror> { v: AnnotationValue? -> v!!.getValue() as AnnotationMirror? }
            .orElseThrow<AnnotationReadFailure?>(Supplier { AnnotationReadFailure(String.format("Missing 'deprecation' attribute in @%s", ReplacesEagerProperty::class.java.getSimpleName())) })
        val enabled = AnnotationUtils.findAnnotationValueWithDefaults(elements, deprecation, "enabled")
            .map<Boolean> { annotationValue: AnnotationValue? -> annotationValue!!.getValue() as Boolean }
            .orElseThrow<AnnotationReadFailure?>(Supplier { AnnotationReadFailure("Missing 'enabled' attribute in @ReplacedDeprecation") })
        val removedIn = AnnotationUtils.findAnnotationValueWithDefaults(elements, deprecation, "removedIn")
            .map<ReplacedDeprecation.RemovedIn> { v: AnnotationValue? -> ReplacedDeprecation.RemovedIn.valueOf(v!!.getValue().toString()) }
            .orElseThrow<AnnotationReadFailure?>(Supplier { AnnotationReadFailure("Missing 'removedIn' attribute in @ReplacedDeprecation") })
        val withUpgradeGuideVersion = AnnotationUtils.findAnnotationValueWithDefaults(elements, deprecation, "withUpgradeGuideMajorVersion")
            .map<Int> { annotationValue: AnnotationValue? -> annotationValue!!.getValue() as Int }
            .orElseThrow<AnnotationReadFailure?>(Supplier { AnnotationReadFailure("Missing 'withUpgradeGuideMajorVersion' attribute in @ReplacedDeprecation") })
        val withUpgradeGuideSection = AnnotationUtils.findAnnotationValueWithDefaults(elements, deprecation, "withUpgradeGuideSection")
            .map<String> { annotationValue: AnnotationValue? -> annotationValue!!.getValue() as String? }
            .orElseThrow<AnnotationReadFailure?>(Supplier { AnnotationReadFailure("Missing 'withUpgradeGuideSection' attribute in @ReplacedDeprecation") })
        val withDslReference = AnnotationUtils.findAnnotationValueWithDefaults(elements, deprecation, "withDslReference")
            .map<Boolean> { annotationValue: AnnotationValue? -> annotationValue!!.getValue() as Boolean }
            .orElseThrow<AnnotationReadFailure?>(Supplier { AnnotationReadFailure("Missing 'withDslReference' attribute in @ReplacedDeprecation") })
        return DeprecationSpec(enabled, removedIn, withUpgradeGuideVersion, withUpgradeGuideSection, withDslReference)
    }

    private fun readBinaryCompatibility(annotation: AnnotationMirror): ReplacesEagerProperty.BinaryCompatibility {
        return AnnotationUtils.findAnnotationValueWithDefaults(elements, annotation, "binaryCompatibility")
            .map<ReplacesEagerProperty.BinaryCompatibility> { v: AnnotationValue? -> ReplacesEagerProperty.BinaryCompatibility.valueOf(v!!.getValue().toString()) }
            .orElseThrow<AnnotationReadFailure?>(Supplier { AnnotationReadFailure("Missing 'binaryCompatibility' attribute in @ReplacedAccessor") })
    }

    private fun getAccessorSpec(
        method: ExecutableElement,
        annotation: AnnotationMirror,
        parentDeprecationSpec: DeprecationSpec?,
        binaryCompatibility: ReplacesEagerProperty.BinaryCompatibility?
    ): AccessorSpec {
        val methodName = AnnotationUtils.findAnnotationValue(annotation, "name")
            .map<String> { v: AnnotationValue? -> v!!.getValue() as String }
            .orElseThrow<AnnotationReadFailure?>(Supplier { AnnotationReadFailure("Missing 'name' attribute in @ReplacedAccessor") })
        val accessorType = AnnotationUtils.findAnnotationValue(annotation, "value")
            .map<ReplacedAccessor.AccessorType> { v: AnnotationValue? -> ReplacedAccessor.AccessorType.valueOf(v!!.getValue().toString()) }
            .orElseThrow<AnnotationReadFailure?>(Supplier { AnnotationReadFailure("Missing 'value' attribute in @ReplacedAccessor") })
        val originalType: TypeName = extractOriginalType(method, annotation)
        return getAccessorSpec(method, accessorType, methodName, originalType, annotation, parentDeprecationSpec, binaryCompatibility)
    }

    private fun getAccessorSpec(method: ExecutableElement, accessorType: ReplacedAccessor.AccessorType, annotation: AnnotationMirror): AccessorSpec {
        val propertyName: String = getPropertyName(method)
        val originalType: TypeName = extractOriginalType(method, annotation)
        val methodName: String?
        when (accessorType) {
            ReplacedAccessor.AccessorType.GETTER -> {
                val capitalize = propertyName.substring(0, 1).uppercase() + propertyName.substring(1)
                methodName = if (originalType == TypeName.BOOLEAN) "is" + capitalize else "get" + capitalize
            }

            ReplacedAccessor.AccessorType.SETTER -> methodName = method.getSimpleName().toString().replaceFirst("get".toRegex(), "set")
            else -> throw IllegalArgumentException("Unsupported accessor type: " + accessorType)
        }
        val deprecationSpec = readDeprecationSpec(annotation)
        val binaryCompatibility = readBinaryCompatibility(annotation)
        return getAccessorSpec(method, accessorType, methodName, originalType, annotation, deprecationSpec, binaryCompatibility)
    }

    private fun getAccessorSpec(
        method: ExecutableElement,
        accessorType: ReplacedAccessor.AccessorType,
        methodName: String,
        originalType: TypeName,
        annotation: AnnotationMirror,
        deprecationSpec: DeprecationSpec?,
        binaryCompatibility: ReplacesEagerProperty.BinaryCompatibility?
    ): AccessorSpec {
        val returnType: TypeName
        val parameters: MutableList<ParameterInfo>
        when (accessorType) {
            ReplacedAccessor.AccessorType.GETTER -> {
                parameters = ArrayList<ParameterInfo>()
                returnType = originalType
            }

            ReplacedAccessor.AccessorType.SETTER -> {
                parameters = mutableListOf(ParameterInfoImpl("arg0", TypeUtils.extractRawType(originalType), ParameterKindInfo.METHOD_PARAMETER))
                val isFluentSetter = AnnotationUtils.findAnnotationValueWithDefaults(elements, annotation, "fluentSetter")
                    .map<Boolean> { v: AnnotationValue? -> v!!.getValue() as Boolean }
                    .orElseThrow<AnnotationReadFailure?>(Supplier { AnnotationReadFailure("Missing 'fluentSetter' attribute") })
                returnType = if (isFluentSetter) TypeName.get(method.getEnclosingElement().asType()) else ClassName.VOID
            }

            else -> throw IllegalArgumentException("Unsupported accessor type: " + accessorType)
        }
        val propertyName: String = getPropertyName(methodName)
        val generatedClassName = "org.gradle.internal.classpath.generated." + method.getEnclosingElement().getSimpleName() + "_Adapter"
        return AccessorSpec(
            generatedClassName,
            accessorType,
            propertyName,
            methodName,
            returnType,
            parameters,
            deprecationSpec,
            binaryCompatibility,
            BytecodeInterceptorType.BYTECODE_UPGRADE,
            null
        )
    }

    private fun createJvmInterceptionRequest(method: ExecutableElement, accessorSpec: AccessorSpec): CallInterceptionRequest {
        when (accessorSpec.accessorType) {
            ReplacedAccessor.AccessorType.GETTER -> return createJvmGetterInterceptionRequest(accessorSpec, method)
            ReplacedAccessor.AccessorType.SETTER -> return createJvmSetterInterceptionRequest(accessorSpec, method)
            else -> throw IllegalArgumentException("Unsupported accessor type: " + accessorSpec.accessorType)
        }
    }

    private fun createJvmGetterInterceptionRequest(accessor: AccessorSpec, method: ExecutableElement): CallInterceptionRequest {
        val extras = getJvmRequestExtras(accessor, method, accessor.binaryCompatibility)
        val callableName = accessor.methodName
        val returnType = requireNotNull(TypeUtils.extractRawType(accessor.returnType))
        return CallInterceptionRequestImpl(
            Companion.extractCallableInfo(CallableKindInfo.INSTANCE_METHOD, method, returnType, callableName, mutableListOf<ParameterInfo>()),
            Companion.extractImplementationInfo(accessor, method, returnType, accessor.methodName, "get", mutableListOf<ParameterInfo>()),
            extras
        )
    }

    private fun createJvmSetterInterceptionRequest(accessor: AccessorSpec, method: ExecutableElement): CallInterceptionRequest {
        val returnType = TypeUtils.extractRawType(accessor.returnType)
            ?: throw IllegalArgumentException("Accessor return type should not be null")
        val callableName = accessor.methodName
        val parameters = accessor.parameters
        val binaryCompatibility = accessor.binaryCompatibility
        val extras = getJvmRequestExtras(accessor, method, binaryCompatibility)
        return CallInterceptionRequestImpl(
            extractCallableInfo(CallableKindInfo.INSTANCE_METHOD, method, returnType, callableName, parameters),
            extractImplementationInfo(accessor, method, returnType, accessor.methodName, "set", parameters),
            extras
        )
    }

    private fun getJvmRequestExtras(accessor: AccessorSpec, method: ExecutableElement, binaryCompatibility: ReplacesEagerProperty.BinaryCompatibility?): MutableList<RequestExtra?> {
        val interceptorsClassName = getJavaInterceptorsClassName(accessor.interceptorType)
        val extras: MutableList<RequestExtra?> = ArrayList<RequestExtra?>()
        extras.add(OriginatingElement(method))
        extras.add(RequestExtra.InterceptJvmCalls(interceptorsClassName, accessor.interceptorType))
        val implementationClass = accessor.generatedClassName
        val newPropertyType = TypeName.get(method.getReturnType())
        val propertyName: String = getPropertyName(method)
        val methodDescriptor = TypeUtils.extractMethodDescriptor(method)
        extras.add(
            PropertyUpgradeRequestExtra(
                propertyName,
                method.getSimpleName().toString(),
                methodDescriptor,
                accessor.returnType,
                implementationClass,
                accessor.propertyName,
                accessor.methodName,
                newPropertyType,
                accessor.deprecationSpec,
                binaryCompatibility,
                accessor.bridgedMethod
            )
        )
        return extras
    }

    // TODO Consolidate with AnnotationCallInterceptionRequestReaderImpl#Failure
    private class AnnotationReadFailure(reason: String?) : IllegalArgumentException(reason)

    private class AccessorSpec(
        val generatedClassName: String,
        val accessorType: ReplacedAccessor.AccessorType,
        val propertyName: String,
        val methodName: String,
        val returnType: TypeName,
        val parameters: MutableList<ParameterInfo>,
        val deprecationSpec: DeprecationSpec?,
        val binaryCompatibility: ReplacesEagerProperty.BinaryCompatibility?,
        val interceptorType: BytecodeInterceptorType,
        val bridgedMethod: BridgedMethodInfo?
    )

    internal class DeprecationSpec(
        val isEnabled: Boolean,
        val removedIn: ReplacedDeprecation.RemovedIn?,
        val withUpgradeGuideVersion: Int,
        val withUpgradeGuideSection: String?,
        val isWithDslReference: Boolean
    )

    companion object {
        private val DEFAULT_TYPE: TypeName = ClassName.get(ReplacesEagerProperty.DefaultValue::class.java)
        private const val TO_BE_REPLACED_SETTERS_KEY_PREFIX = "@ToBeReplacedByLazyPropertySetters_"
        private const val TO_BE_REPLACED_SETTERS_VISITED_KEY_PREFIX = "@ToBeReplacedByLazyPropertySettersVisited_"

        private fun getProjectName(processingEnv: ProcessingEnvironment): String? {
            val projectName = processingEnv.getOptions().get(AbstractInstrumentationProcessor.PROJECT_NAME_OPTIONS)
            if (projectName == null || projectName.isEmpty()) {
                return null
            }
            return Stream.of<String>(*projectName.split("-".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
                .map<String?> { s: String? -> s!!.substring(0, 1).uppercase() + s.substring(1) }
                .collect(Collectors.joining())
        }

        private fun isGroovyPropertyGetter(accessor: AccessorSpec, accessors: MutableList<AccessorSpec>): Boolean {
            if (accessor.accessorType != ReplacedAccessor.AccessorType.GETTER) {
                return false
            }
            if (accessor.returnType == TypeName.BOOLEAN || accessor.returnType == TypeName.BOOLEAN.box()) {
                // For boolean properties we have two getters: isFoo() and getFoo(),
                // if isFoo() exists then isFoo() is property getter, else we can use getFoo()
                return isIsGetterMethodName(accessor.methodName) || (isGetGetterMethodName(accessor.methodName) && accessors.stream()
                    .noneMatch { a: AccessorSpec? -> isIsGetterMethodName(a!!.methodName) })
            }
            return isGetGetterMethodName(accessor.methodName)
        }

        private fun isGroovyPropertySetter(accessorSpec: AccessorSpec, groovyPropertyGetter: AccessorSpec): Boolean {
            return accessorSpec.accessorType == ReplacedAccessor.AccessorType.SETTER && isSetterMethodName(accessorSpec.methodName)
                    && accessorSpec.parameters.size == 1 && accessorSpec.parameters.get(0).parameterType == TypeUtils.extractRawType(groovyPropertyGetter.returnType)
        }

        private fun prependReceiverParameter(parameters: MutableList<ParameterInfo>, receiverType: Type?): MutableList<ParameterInfo> {
            val result: MutableList<ParameterInfo> = ArrayList<ParameterInfo>()
            result.add(ParameterInfoImpl("receiver", receiverType, ParameterKindInfo.RECEIVER))
            result.addAll(parameters)
            return result
        }

        private fun getAllSetters(element: Element?): Multimap<String, ExecutableElement?> {
            val setters = ArrayListMultimap.create<String, ExecutableElement?>()
            TypeUtils.getExecutableElementsFromElements(Stream.of<Element?>(element)).stream()
                .filter { method: ExecutableElement? -> method != null && isSetterMethodName(method.getSimpleName().toString()) && method.getParameters().size == 1 }
                .forEach { method: ExecutableElement? ->
                    method ?: return@forEach
                    setters.put(Companion.getPropertyName(method), method)
                }
            return setters
        }

        private fun bridgedMethodToAccessorSpec(
            method: ExecutableElement,
            generatedClassName: String,
            bridgeType: BridgeType,
            deprecationSpec: DeprecationSpec?,
            binaryCompatibility: ReplacesEagerProperty.BinaryCompatibility?,
            bytecodeInterceptorType: BytecodeInterceptorType
        ): AccessorSpec {
            val methodName = method.getSimpleName().toString()
            val propertyName: String = getPropertyName(methodName)
            val returnType = TypeName.get(method.getReturnType())

            // First parameters of adapter is always a type we upgrade, so we skip it for parameters of an accessor
            val skipParameters = if (bridgeType == BridgeType.ADAPTER_METHOD_BRIDGE) 1 else 0
            val parameters: MutableList<ParameterInfo> = method.getParameters().stream().skip(skipParameters.toLong())
                .map<ParameterInfoImpl?> { parameter: VariableElement? ->
                    ParameterInfoImpl(
                        parameter!!.getSimpleName().toString(),
                        TypeUtils.extractType(parameter.asType()),
                        ParameterKindInfo.METHOD_PARAMETER
                    )
                }
                .collect(Collectors.toList())

            val accessorType = if (parameters.isEmpty()) ReplacedAccessor.AccessorType.GETTER else ReplacedAccessor.AccessorType.SETTER
            val bridgedMethodInfo = BridgedMethodInfo(method, bridgeType)
            return AccessorSpec(
                generatedClassName,
                accessorType,
                propertyName,
                methodName,
                returnType,
                parameters,
                deprecationSpec,
                binaryCompatibility,
                bytecodeInterceptorType,
                bridgedMethodInfo
            )
        }

        private fun validateBridgedMethods(adapter: Element, upgradedElement: Element, methods: MutableList<ExecutableElement>) {
            val errors: MutableList<String?> = ArrayList<String?>()
            if (!isPackagePrivate(adapter)) {
                errors.add(String.format("Adapter class '%s' should be package private, but it's not.", adapter))
            }

            val upgradedType = TypeUtils.extractType(upgradedElement.asType())
            for (method in methods) {
                if (method.getParameters().isEmpty()) {
                    errors.add(String.format("Adapter method '%s.%s' has no parameters, but it should have at least one of type '%s'.", adapter, method, upgradedElement))
                } else if (TypeUtils.extractType(method.getParameters().get(0).asType()) != upgradedType) {
                    errors.add(
                        String.format(
                            "Adapter method '%s.%s' should have first parameter of type '%s', but first parameter is of type '%s'.",
                            adapter,
                            method,
                            upgradedElement,
                            method.getParameters().get(0).asType()
                        )
                    )
                }
                if (!method.getModifiers().contains(Modifier.STATIC)) {
                    errors.add(String.format("Adapter method '%s.%s' should be static but it's not.", adapter, method))
                }
                if (!isPackagePrivate(method)) {
                    errors.add(String.format("Adapter method '%s.%s' should be package-private but it's not.", adapter, method))
                }
            }

            if (!errors.isEmpty()) {
                throw AnnotationReadFailure(errors.joinToString("\n"))
            }
        }

        private fun isPackagePrivate(element: Element): Boolean {
            return !element.getModifiers().contains(Modifier.PUBLIC) && !element.getModifiers().contains(Modifier.PROTECTED) && !element.getModifiers().contains(Modifier.PRIVATE)
        }

        private fun extractOriginalType(method: ExecutableElement, annotation: AnnotationMirror): TypeName {
            val annotationValue = AnnotationUtils.findAnnotationValue(annotation, "originalType")
            val typeName = annotationValue.map<TypeName> { v: AnnotationValue? ->
                if (v!!.getValue() is DeclaredType // We use DeclaredType.asElement().asType() so if the original type is a parametrized type,
                // e.g. Iterable<T>, resolved TypeName contains information that it's Iterable<T> and not just Iterable
                )
                    TypeName.get((annotationValue.get().getValue() as DeclaredType).asElement().asType())
                else
                    TypeName.get(annotationValue.get().getValue() as TypeMirror?)
            }.orElse(DEFAULT_TYPE)
            if (typeName != DEFAULT_TYPE) {
                return typeName
            }
            return extractOriginalTypeFromGeneric(method, method.getReturnType())
        }

        private fun extractOriginalTypeFromGeneric(method: ExecutableElement, typeMirror: TypeMirror?): TypeName {
            val typeName = if (method.getReturnType() is DeclaredType)
                (method.getReturnType() as DeclaredType).asElement().toString()
            else
                method.getReturnType().toString()
            val gradleLazyType = GradleLazyType.from(typeName)
        when (gradleLazyType) {
                GradleLazyType.CONFIGURABLE_FILE_COLLECTION -> return GradleLazyType.FILE_COLLECTION.asClassName()!!
                GradleLazyType.DIRECTORY_PROPERTY, GradleLazyType.REGULAR_FILE_PROPERTY -> return ClassName.get(File::class.java)
                GradleLazyType.LIST_PROPERTY -> return ParameterizedTypeName.get(
                    ClassName.get(MutableList::class.java),
                    TypeUtils.getTypeParameterOrThrow(typeMirror, 0)
                )

                GradleLazyType.SET_PROPERTY -> return ParameterizedTypeName.get(
                    ClassName.get(MutableSet::class.java),
                    TypeUtils.getTypeParameterOrThrow(typeMirror, 0)
                )

                GradleLazyType.MAP_PROPERTY -> return ParameterizedTypeName.get(
                    ClassName.get(MutableMap::class.java),
                    TypeUtils.getTypeParameterOrThrow(typeMirror, 0),
                    TypeUtils.getTypeParameterOrThrow(typeMirror, 1)
                )

                GradleLazyType.PROPERTY -> return TypeUtils.getTypeParameterOrThrow(typeMirror, 0)
                GradleLazyType.PROVIDER -> {
                    val extractedType = TypeUtils.getTypeParameterOrThrow(typeMirror, 0)
                    return if (GradleReferencedType.isAssignableToFileSystemLocation(extractedType))
                        ClassName.get(File::class.java)
                    else
                        extractedType
                }

                else -> throw AnnotationReadFailure(
                    String.format(
                        "Cannot extract original type for method '%s.%s: %s'. Use explicit @%s#originalType instead.",
                        method.getEnclosingElement(),
                        method,
                        typeMirror,
                        ReplacesEagerProperty::class.java.getSimpleName()
                    )
                )
            }
        }

        private fun extractCallableInfo(
            kindInfo: CallableKindInfo,
            methodElement: ExecutableElement,
            returnType: Type,
            callableName: String,
            parameters: MutableList<ParameterInfo>
        ): CallableInfo {
            val owner = CallableOwnerInfo(TypeUtils.extractType(methodElement.getEnclosingElement().asType()), true)
            val returnTypeInfo = CallableReturnTypeInfo(returnType)
            return CallableInfoImpl(kindInfo, owner, callableName, returnTypeInfo, parameters)
        }

        private fun extractImplementationInfo(
            accessor: AccessorSpec,
            method: ExecutableElement,
            returnType: Type,
            interceptedMethodName: String,
            methodPrefix: String,
            parameters: MutableList<ParameterInfo>
        ): ImplementationInfoImpl {
            val owner = TypeUtils.extractType(method.getEnclosingElement().asType())
            val implementationOwner = Type.getObjectType(accessor.generatedClassName)
            val implementationName = "access_" + methodPrefix + "_" + interceptedMethodName
            val implementationDescriptor = Type.getMethodDescriptor(returnType, *toArray(owner, parameters))
            return ImplementationInfoImpl(implementationOwner, implementationName, implementationDescriptor)
        }

        private fun toArray(owner: Type?, parameters: MutableList<ParameterInfo>): Array<Type> {
            val array = arrayOfNulls<Type>(1 + parameters.size)
            array[0] = owner ?: throw IllegalArgumentException("Method owner type is missing")
            var i = 1
            for (parameter in parameters) {
                array[i++] = requireNotNull(parameter.parameterType)
            }
            return array as Array<Type>
        }

        private fun getPropertyName(method: ExecutableElement): String {
            return getPropertyName(method.getSimpleName().toString())
        }

        private fun getPropertyName(methodName: String): String {
            if (isIsGetterMethodName(methodName)) {
                // isFoo() -> foo
                return methodName.get(2).lowercaseChar().toString() + methodName.substring(3)
            } else if (isGetGetterMethodName(methodName) || isSetterMethodName(methodName)) {
                // getFoo() -> foo || setFoo() -> foo
                return methodName.get(3).lowercaseChar().toString() + methodName.substring(4)
            } else {
                return methodName
            }
        }

        private fun isIsGetterMethodName(methodName: String): Boolean {
            return methodName.startsWith("is") && methodName.length > 2 && Character.isUpperCase(methodName.get(2))
        }

        private fun isGetGetterMethodName(methodName: String): Boolean {
            return methodName.startsWith("get") && methodName.length > 3 && Character.isUpperCase(methodName.get(3))
        }

        private fun isSetterMethodName(methodName: String): Boolean {
            return methodName.startsWith("set") && methodName.length > 3 && Character.isUpperCase(methodName.get(3))
        }
    }
}
