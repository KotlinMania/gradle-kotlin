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
import java.util.function.Supplier
import java.util.stream.Collectors

class CompositeInstrumentationCodeGenerator(private val generators: MutableCollection<InstrumentationCodeGenerator?>) : InstrumentationCodeGenerator {
    override fun generateCodeForRequestedInterceptors(interceptionRequests: MutableCollection<CallInterceptionRequest?>?): InstrumentationCodeGenerator.GenerationResult {
        val results = generators.stream()
            .map<InstrumentationCodeGenerator.GenerationResult?> { generators: InstrumentationCodeGenerator? -> generators!!.generateCodeForRequestedInterceptors(interceptionRequests) }.collect(
                Collectors.toList()
            )

        val failures = results.stream().filter { it: InstrumentationCodeGenerator.GenerationResult? -> it is HasFailures }
            .map<HasFailures?> { it: InstrumentationCodeGenerator.GenerationResult? -> it as HasFailures }.collect(
                Collectors.toList()
            )
        if (!failures.isEmpty()) {
            return CodeFailures(failures.stream().flatMap<FailureInfo?> { it: HasFailures? -> requireNotNull(it!!.failureDetails).stream() }.collect(Collectors.toList()))
        }

        val generatingResults = results.stream().map<CanGenerateClasses?> { it: InstrumentationCodeGenerator.GenerationResult? -> it as CanGenerateClasses? }.collect(Collectors.toList())
        val generatorByClassName: MutableMap<String?, CanGenerateClasses?> = LinkedHashMap<String?, CanGenerateClasses?>()
        generatingResults.forEach(Consumer { result: CanGenerateClasses? ->
            requireNotNull(result!!.classNames).forEach(Consumer { className: String? ->
                check(generatorByClassName.put(className, result) == null) { "multiple code generators for class name " + className }
            })
        })

        return object : CanGenerateClasses {
            override val classNames: MutableCollection<String?>
                get() = generatingResults.stream().flatMap<String?> { it: CanGenerateClasses? -> requireNotNull(it!!.classNames).stream() }.collect(Collectors.toCollection(Supplier { LinkedHashSet() }))

            override fun buildType(className: String?, builder: TypeSpec.Builder?) {
                generatorByClassName.get(className)!!.buildType(className, builder)
            }

            override val coveredRequests: MutableList<CallInterceptionRequest?>
                get() = generatingResults.stream().flatMap<CallInterceptionRequest?> { it: CanGenerateClasses? -> requireNotNull(it!!.coveredRequests).stream() }.distinct().collect(Collectors.toList())
        }
    }
}
