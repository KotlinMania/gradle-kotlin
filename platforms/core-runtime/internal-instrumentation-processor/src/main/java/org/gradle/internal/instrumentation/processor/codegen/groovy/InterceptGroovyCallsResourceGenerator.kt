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

import org.gradle.internal.UncheckedException
import org.gradle.internal.instrumentation.model.CallInterceptionRequest
import org.gradle.internal.instrumentation.model.RequestExtra
import org.gradle.internal.instrumentation.processor.codegen.InstrumentationResourceGenerator
import org.gradle.internal.instrumentation.processor.codegen.InstrumentationResourceGenerator.GenerationResult.CanGenerateResource
import org.gradle.internal.instrumentation.processor.codegen.groovy.CallInterceptorSpecs.CallInterceptorSpec.ConstructorInterceptorSpec
import org.gradle.internal.instrumentation.processor.codegen.groovy.CallInterceptorSpecs.CallInterceptorSpec.NamedCallableInterceptorSpec
import java.io.IOException
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.function.Consumer
import java.util.stream.Collectors

/**
 * Generates META-INF/services resource with all generated CallInterceptors so we can load them at runtime
 */
class InterceptGroovyCallsResourceGenerator : InstrumentationResourceGenerator {
    override fun filterRequestsForResource(interceptionRequests: MutableCollection<CallInterceptionRequest?>): MutableCollection<CallInterceptionRequest?> {
        return interceptionRequests.stream()
            .filter { request: CallInterceptionRequest? -> request!!.requestExtras!!.getByType(RequestExtra.InterceptGroovyCalls::class.java).isPresent() }
            .collect(Collectors.toList())
    }

    override fun generateResourceForRequests(filteredRequests: MutableCollection<CallInterceptionRequest?>): InstrumentationResourceGenerator.GenerationResult {
        val callInterceptorTypes: MutableList<String?> = ArrayList<String?>()
        val specs = GroovyClassGeneratorUtils.groupRequests(filteredRequests)
        specs.getNamedRequests().forEach(Consumer { spec: NamedCallableInterceptorSpec? -> callInterceptorTypes.add(spec!!.getFullClassName()) })
        specs.getConstructorRequests().forEach(Consumer { spec: ConstructorInterceptorSpec? -> callInterceptorTypes.add(spec!!.getFullClassName()) })

        return object : CanGenerateResource {
            override fun getPackageName(): String {
                return ""
            }

            override fun getName(): String {
                return "META-INF/services/" + InterceptGroovyCallsGenerator.Companion.FILTERABLE_CALL_INTERCEPTOR.reflectionName()
            }

            override fun write(outputStream: OutputStream) {
                val types = callInterceptorTypes.stream()
                    .distinct()
                    .sorted()
                    .collect(Collectors.joining("\n"))
                try {
                    OutputStreamWriter(outputStream, StandardCharsets.UTF_8).use { writer ->
                        writer.write(types)
                    }
                } catch (e: IOException) {
                    throw UncheckedException.throwAsUncheckedException(e)
                }
            }
        }
    }
}
