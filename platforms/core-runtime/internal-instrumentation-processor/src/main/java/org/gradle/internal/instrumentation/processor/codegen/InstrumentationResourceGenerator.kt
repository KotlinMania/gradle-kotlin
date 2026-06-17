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
package org.gradle.internal.instrumentation.processor.codegen

import org.gradle.internal.instrumentation.model.CallInterceptionRequest
import org.gradle.internal.instrumentation.processor.codegen.HasFailures.FailureInfo
import java.io.OutputStream

interface InstrumentationResourceGenerator {
    /**
     * Filter the requests to only those that are relevant to the resource being generated.
     */
    fun filterRequestsForResource(
        interceptionRequests: MutableCollection<CallInterceptionRequest?>?
    ): MutableCollection<CallInterceptionRequest?>?

    /**
     * Actually generate the resource for filtered requests. A collection of filtered requests passed as a parameter will always have at least one element.
     */
    fun generateResourceForRequests(
        filteredRequests: MutableCollection<CallInterceptionRequest?>?
    ): GenerationResult?

    interface GenerationResult {
        interface CanGenerateResource : GenerationResult {
            val packageName: String?
            val name: String?
            fun write(outputStream: OutputStream?)
        }

        class NoResourceToGenerate : GenerationResult

        class ResourceFailures(private val failures: MutableList<FailureInfo?>?) : GenerationResult, HasFailures {
            override val failureDetails: MutableList<FailureInfo?>? = failures
        }
    }
}
