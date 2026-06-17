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
package org.gradle.internal.instrumentation.extensions.types

import org.gradle.internal.UncheckedException
import org.gradle.internal.instrumentation.model.CallInterceptionRequest
import org.gradle.internal.instrumentation.processor.codegen.InstrumentationResourceGenerator
import org.gradle.internal.instrumentation.processor.codegen.InstrumentationResourceGenerator.GenerationResult.CanGenerateResource
import java.io.IOException
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.stream.Collectors


/**
 * Writes all instrumented types with inherited method interception to a resources
 */
class InstrumentedTypesResourceGenerator : InstrumentationResourceGenerator {
    override fun filterRequestsForResource(interceptionRequests: MutableCollection<CallInterceptionRequest?>?): MutableCollection<CallInterceptionRequest?>? {
        if (interceptionRequests == null) {
            return null
        }
        return interceptionRequests.stream()
            .filter { request: CallInterceptionRequest? ->
                val owner = request?.interceptedCallable?.owner
                owner != null && owner.type != null && owner.isInterceptSubtypes
            }
                .collect(Collectors.toList())
    }

    override fun generateResourceForRequests(filteredRequests: MutableCollection<CallInterceptionRequest?>?): InstrumentationResourceGenerator.GenerationResult? {
        if (filteredRequests == null || filteredRequests.isEmpty()) {
            return null
        }
        return object : CanGenerateResource {
            override val packageName: String?
                get() = ""

            override val name: String?
                get() = "META-INF/gradle/instrumentation/instrumented-classes.txt"

            override fun write(outputStream: OutputStream?) {
                if (outputStream == null) {
                    return
                }
                val types = filteredRequests.stream()
                    .map<String?> { request: CallInterceptionRequest? ->
                        requireNotNull(requireNotNull(request!!.interceptedCallable).owner).type!!.getClassName().replace(".", "/")
                    }
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
