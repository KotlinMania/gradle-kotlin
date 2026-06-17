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

import com.squareup.javapoet.TypeSpec
import org.gradle.internal.instrumentation.model.CallInterceptionRequest
import org.gradle.internal.instrumentation.processor.codegen.HasFailures.FailureInfo
import org.gradle.internal.instrumentation.processor.codegen.InstrumentationCodeGenerator.GenerationResult.CanGenerateClasses
import org.gradle.internal.instrumentation.processor.codegen.InstrumentationCodeGenerator.GenerationResult.CodeFailures
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Collectors

abstract class RequestGroupingInstrumentationClassSourceGenerator : InstrumentationCodeGenerator {
    protected abstract fun classNameForRequest(request: CallInterceptionRequest?): String?

    protected abstract fun classContentForClass(
        className: String?,
        requestsClassGroup: MutableList<CallInterceptionRequest?>?,
        onProcessedRequest: Consumer<in CallInterceptionRequest?>?,
        onFailure: Consumer<in FailureInfo?>?
    ): Consumer<TypeSpec.Builder?>?

    override fun generateCodeForRequestedInterceptors(interceptionRequests: MutableCollection<CallInterceptionRequest?>): InstrumentationCodeGenerator.GenerationResult {
        val requestsByImplClass = interceptionRequests.stream()
            .filter { it: CallInterceptionRequest? -> classNameForRequest(it) != null }
            .collect(Collectors.groupingBy(Function { request: CallInterceptionRequest? -> this.classNameForRequest(request) }, Supplier { LinkedHashMap() }, Collectors.toList()))

        val failuresInfo: MutableList<FailureInfo?> = ArrayList<FailureInfo?>()
        val processedRequests: MutableSet<CallInterceptionRequest?> = LinkedHashSet<CallInterceptionRequest?>(interceptionRequests.size)
        val classContentByName: MutableMap<String?, Consumer<TypeSpec.Builder?>?> = java.util.LinkedHashMap<String?, Consumer<TypeSpec.Builder?>?>()

        requestsByImplClass.forEach { (className: String?, requests: MutableList<CallInterceptionRequest?>?) ->
            classContentByName.put(
                className,
                classContentForClass(className, requests, Consumer { e: CallInterceptionRequest? -> processedRequests.add(e) }, Consumer { e: FailureInfo? -> failuresInfo.add(e) })
            )
        }

        if (failuresInfo.isEmpty()) {
            return successResult(processedRequests, classContentByName)
        } else {
            return CodeFailures(failuresInfo)
        }
    }

    companion object {
        private fun successResult(processedRequests: MutableSet<CallInterceptionRequest?>, classContentByName: MutableMap<String?, Consumer<TypeSpec.Builder?>?>): CanGenerateClasses {
            return object : CanGenerateClasses {
                override fun getClassNames(): MutableCollection<String?> {
                    return classContentByName.keys
                }

                override fun buildType(className: String?, builder: TypeSpec.Builder?) {
                    classContentByName.get(className)!!.accept(builder)
                }

                override fun getCoveredRequests(): MutableList<CallInterceptionRequest?> {
                    return ArrayList<CallInterceptionRequest?>(processedRequests)
                }
            }
        }
    }
}
