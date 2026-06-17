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

import org.gradle.internal.instrumentation.model.CallInterceptionRequest
import org.gradle.internal.instrumentation.model.RequestExtra
import org.gradle.internal.instrumentation.processor.codegen.InstrumentationResourceGenerator
import org.gradle.internal.instrumentation.processor.codegen.InstrumentationResourceGenerator.GenerationResult.CanGenerateResource
import java.io.OutputStream

/**
 * Generates META-INF/services resource with all factory classes for generated JvmBytecodeCallInterceptors so we can load them at runtime
 */
class InterceptJvmCallsResourceGenerator : InstrumentationResourceGenerator {
    override fun filterRequestsForResource(interceptionRequests: MutableCollection<CallInterceptionRequest?>?): MutableCollection<CallInterceptionRequest?>? {
        if (interceptionRequests == null) {
            return null
        }

        return interceptionRequests.asSequence()
            .filterNotNull()
            .filter { request ->
                request.requestExtras.getByType(RequestExtra.InterceptJvmCalls::class.java).isPresent
            }
            .toMutableList()
    }

    override fun generateResourceForRequests(filteredRequests: MutableCollection<CallInterceptionRequest?>?): InstrumentationResourceGenerator.GenerationResult {
        if (filteredRequests == null || filteredRequests.isEmpty()) {
            return InstrumentationResourceGenerator.GenerationResult.NoResourceToGenerate()
        }

        return object : CanGenerateResource {
            override val packageName: String?
                get() = ""

            override val name: String?
                get() = "META-INF/services/" + org.gradle.internal.instrumentation.api.jvmbytecode.JvmBytecodeCallInterceptor.Factory::class.java.name

            override fun write(outputStream: OutputStream?) {
                if (outputStream == null) {
                    return
                }

                val types = filteredRequests
                    .asSequence()
                    .filterNotNull()
                    .mapNotNull { request -> request.requestExtras.getByType(RequestExtra.InterceptJvmCalls::class.java).orElse(null) }
                    .map { extra -> extra.implementationClassName + "\$Factory" }
                    .distinct()
                    .sorted()
                    .joinToString("\n")
                try {
                    java.io.OutputStreamWriter(outputStream, java.nio.charset.StandardCharsets.UTF_8).use { writer ->
                        writer.write(types)
                    }
                } catch (e: java.io.IOException) {
                    throw org.gradle.internal.UncheckedException.throwAsUncheckedException(e)
                }
            }
        }
    }
}
