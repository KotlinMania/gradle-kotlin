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
import java.util.HashMap

/**
 * Writes all instrumented properties to a resource file
 */
class InstrumentedPropertiesResourceGenerator : InstrumentationResourceGenerator {
    private val mapper = ObjectMapper()

    override fun filterRequestsForResource(interceptionRequests: MutableCollection<CallInterceptionRequest?>?): MutableCollection<CallInterceptionRequest?>? {
        if (interceptionRequests == null) {
            return null
        }
        return interceptionRequests.asSequence()
            .filterNotNull()
            .filter { request ->
                request.requestExtras.getByType(PropertyUpgradeRequestExtra::class.java).isPresent
            }
            .toMutableList()
    }

    override fun generateResourceForRequests(filteredRequests: MutableCollection<CallInterceptionRequest?>?): InstrumentationResourceGenerator.GenerationResult? {
        if (filteredRequests == null || filteredRequests.isEmpty()) {
            return InstrumentationResourceGenerator.GenerationResult.NoResourceToGenerate()
        }

        val requests = HashMap<String, MutableList<CallInterceptionRequest>>()
        for (request in filteredRequests.asSequence().filterNotNull()) {
            val requestExtras = request.requestExtras
            val requestExtra = requestExtras
                .getByType(PropertyUpgradeRequestExtra::class.java)
                .orElse(null)
                ?: continue
            val fqName = getFqName(request, requestExtra)
            requests.computeIfAbsent(fqName) { ArrayList() }.add(request)
        }

        if (requests.isEmpty()) {
            return InstrumentationResourceGenerator.GenerationResult.NoResourceToGenerate()
        }

        val entries: MutableList<UpgradedProperty> = toPropertyEntries(requests)

        return object : CanGenerateResource {
            override val packageName: String?
                get() = ""

            override val name: String?
                get() = "META-INF/gradle/instrumentation/upgraded-properties.json"

            override fun write(outputStream: OutputStream?) {
                if (outputStream == null) {
                    return
                }
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
        val replacedAccessors: MutableList<ReplacedAccessor>?
    )

    @JsonPropertyOrder(alphabetic = true)
    internal class ReplacedAccessor(
        val name: String?,
        val descriptor: String?,
        val binaryCompatibility: ReplacesEagerProperty.BinaryCompatibility?
    )

    companion object {
        private fun getFqName(request: CallInterceptionRequest, requestExtra: PropertyUpgradeRequestExtra): String {
            val containingType = request.interceptedCallable?.owner?.type?.className ?: ""
            return "$containingType#${requestExtra.propertyName}"
        }

        private fun toPropertyEntries(requests: MutableMap<String, MutableList<CallInterceptionRequest>>): MutableList<UpgradedProperty> {
            return requests.entries
                .asSequence()
                .sortedBy { it.key }
                .map { entry -> toPropertyEntry(entry.value) }
                .toMutableList()
        }

        private fun toPropertyEntry(requests: MutableList<CallInterceptionRequest>): UpgradedProperty {
            val firstRequest = requireNotNull(requests.firstOrNull())
            val firstRequestExtras = firstRequest.requestExtras
            val upgradeExtra = firstRequestExtras
                .getByType(PropertyUpgradeRequestExtra::class.java)
                .orElseThrow { IllegalArgumentException("Missing PropertyUpgradeRequestExtra in request for upgraded property entry") }!!

            val propertyName = upgradeExtra.propertyName
            val methodName = upgradeExtra.methodName
            val methodDescriptor = upgradeExtra.methodDescriptor
            val containingType = firstRequest.interceptedCallable?.owner?.type?.className

            val upgradedAccessors = requests
                .asSequence()
                .mapNotNull { request ->
                    val requestExtras = request.requestExtras
                    val requestExtra = requestExtras
                        .getByType(PropertyUpgradeRequestExtra::class.java)
                        .orElse(null)
                        ?: return@mapNotNull null
                    val intercepted = request.interceptedCallable ?: return@mapNotNull null
                    val returnType = intercepted.returnType?.type ?: return@mapNotNull null
                    val parameterTypes: Array<Type> = intercepted.parameters.orEmpty()
                        .asSequence()
                        .filterNotNull()
                        .map { parameter -> parameter.parameterType }
                        .filterNotNull()
                        .toList()
                        .toTypedArray()

                    ReplacedAccessor(
                        intercepted.callableName,
                        Type.getMethodDescriptor(returnType, *parameterTypes),
                        requestExtra.binaryCompatibility
                    )
                }
                .sortedWith(compareBy({ it.name.orEmpty() }, { it.descriptor.orEmpty() }))
                .toMutableList()

            return UpgradedProperty(containingType, propertyName, methodName, methodDescriptor, upgradedAccessors)
        }
    }
}
