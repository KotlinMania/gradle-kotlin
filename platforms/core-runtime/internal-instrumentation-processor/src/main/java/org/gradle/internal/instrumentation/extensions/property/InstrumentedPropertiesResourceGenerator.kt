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

import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.databind.ObjectMapper
import org.gradle.internal.UncheckedException
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty
import org.gradle.internal.instrumentation.model.CallInterceptionRequest
import org.gradle.internal.instrumentation.model.ParameterInfo
import org.gradle.internal.instrumentation.processor.codegen.InstrumentationResourceGenerator
import org.gradle.internal.instrumentation.processor.codegen.InstrumentationResourceGenerator.GenerationResult.CanGenerateResource
import org.objectweb.asm.Type
import java.io.IOException
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.Map
import java.util.function.Function
import java.util.stream.Collectors


/**
 * Writes all instrumented properties to a resource file
 */
class InstrumentedPropertiesResourceGenerator : InstrumentationResourceGenerator {
    private val mapper = ObjectMapper()

    override fun filterRequestsForResource(interceptionRequests: MutableCollection<CallInterceptionRequest?>): MutableCollection<CallInterceptionRequest?> {
        return interceptionRequests.stream()
            .filter { request: CallInterceptionRequest? -> request!!.requestExtras.getByType<PropertyUpgradeRequestExtra?>(PropertyUpgradeRequestExtra::class.java).isPresent() }
            .collect(Collectors.toList())
    }

    override fun generateResourceForRequests(filteredRequests: MutableCollection<CallInterceptionRequest?>): InstrumentationResourceGenerator.GenerationResult {
        return object : CanGenerateResource {
            override fun getPackageName(): String {
                return ""
            }

            override fun getName(): String {
                return "META-INF/gradle/instrumentation/upgraded-properties.json"
            }

            override fun write(outputStream: OutputStream) {
                val requests: MutableMap<String?, MutableList<CallInterceptionRequest?>?> = filteredRequests.stream()
                    .collect(Collectors.groupingBy(Function { request: CallInterceptionRequest? -> Companion.getFqName(request!!) }))
                val entries: MutableList<UpgradedProperty?> = toPropertyEntries(requests)
                try {
                    OutputStreamWriter(outputStream, StandardCharsets.UTF_8).use { writer ->
                        writer.write(mapper.writeValueAsString(entries))
                    }
                } catch (e: IOException) {
                    throw UncheckedException.throwAsUncheckedException(e)
                }
            }
        }
    }

    @JsonPropertyOrder(alphabetic = true)
    internal class UpgradedProperty(
        val containingType: String?,
        val propertyName: String?,
        val methodName: String?,
        val methodDescriptor: String?,
        val replacedAccessors: MutableList<ReplacedAccessor?>?
    )

    @JsonPropertyOrder(alphabetic = true)
    internal class ReplacedAccessor(val name: String?, val descriptor: String?, val binaryCompatibility: ReplacesEagerProperty.BinaryCompatibility?)
    companion object {
        private fun getFqName(request: CallInterceptionRequest): String {
            val propertyName = request.requestExtras.getByType<PropertyUpgradeRequestExtra?>(PropertyUpgradeRequestExtra::class.java).get().getPropertyName()
            val containingType = request.interceptedCallable.owner.type.getClassName()
            return containingType + "#" + propertyName
        }

        private fun toPropertyEntries(requests: MutableMap<String?, MutableList<CallInterceptionRequest?>?>): MutableList<UpgradedProperty?> {
            return requests.entries.stream()
                .sorted(Map.Entry.comparingByKey<String?, MutableList<CallInterceptionRequest?>?>())
                .map<UpgradedProperty?> { e: MutableMap.MutableEntry<String?, MutableList<CallInterceptionRequest?>?>? -> Companion.toPropertyEntry(e!!.value) }
                .collect(Collectors.toList())
        }

        private fun toPropertyEntry(requests: MutableList<CallInterceptionRequest>): UpgradedProperty {
            val firstRequest = requests.get(0)
            val upgradeExtra = firstRequest.requestExtras.getByType<PropertyUpgradeRequestExtra>(PropertyUpgradeRequestExtra::class.java).get()
            val propertyName = upgradeExtra.getPropertyName()
            val methodName = upgradeExtra.getMethodName()
            val methodDescriptor = upgradeExtra.getMethodDescriptor()
            val containingType = firstRequest.interceptedCallable.owner.type.getClassName()
            val upgradedAccessors = requests.stream()
                .map<ReplacedAccessor?> { request: CallInterceptionRequest? ->
                    val requestExtra = request!!.requestExtras.getByType<PropertyUpgradeRequestExtra>(PropertyUpgradeRequestExtra::class.java).get()
                    val intercepted = request.interceptedCallable
                    val returnType = intercepted.returnType.type
                    val parameterTypes = intercepted.parameters.stream()
                        .map<Type?> { obj: ParameterInfo? -> obj!!.parameterType }
                        .toArray<Type?> { _Dummy_.__Array__() }
                    ReplacedAccessor(intercepted.callableName, Type.getMethodDescriptor(returnType, *parameterTypes), requestExtra.getBinaryCompatibility())
                }
                .sorted(Comparator.comparing<ReplacedAccessor?, String?>(Function { o: ReplacedAccessor? -> o!!.name }).thenComparing<String?>(Function { o: ReplacedAccessor? -> o!!.descriptor }))
                .collect(Collectors.toList())
            return UpgradedProperty(containingType, propertyName, methodName, methodDescriptor, upgradedAccessors)
        }
    }
}
