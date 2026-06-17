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

import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.TypeSpec
import org.gradle.internal.instrumentation.model.CallInterceptionRequest
import org.gradle.internal.instrumentation.model.RequestExtra.OriginatingElement
import org.gradle.internal.instrumentation.processor.codegen.HasFailures.FailureInfo
import org.gradle.internal.instrumentation.processor.codegen.InstrumentationCodeGenerator.GenerationResult.CanGenerateClasses
import org.gradle.internal.instrumentation.processor.codegen.InstrumentationCodeGenerator.GenerationResult.CodeFailures
import org.gradle.internal.instrumentation.processor.codegen.InstrumentationResourceGenerator.GenerationResult.CanGenerateResource
import org.gradle.internal.instrumentation.processor.codegen.InstrumentationResourceGenerator.GenerationResult.ResourceFailures
import org.gradle.internal.instrumentation.util.NameUtil
import java.io.IOException
import java.util.Objects
import java.util.Optional
import java.util.function.Consumer
import java.util.function.Function
import java.util.stream.Collectors
import javax.annotation.processing.Filer
import javax.annotation.processing.Messager
import javax.lang.model.element.Element
import javax.lang.model.element.ExecutableElement
import javax.tools.Diagnostic
import javax.tools.StandardLocation

class InstrumentationCodeGeneratorHost(
    private val filer: Filer,
    private val messager: Messager,
    private val codeGenerator: InstrumentationCodeGenerator,
    private val resourceGenerators: MutableList<InstrumentationResourceGenerator>
) {
    fun generateCodeForRequestedInterceptors(
        interceptionRequests: MutableCollection<CallInterceptionRequest?>?
    ) {
        val result = codeGenerator.generateCodeForRequestedInterceptors(interceptionRequests)
        if (result is CanGenerateClasses) {
            for (canonicalClassName in result.getClassNames()) {
                val className = NameUtil.getClassName(canonicalClassName)
                val builder = TypeSpec.classBuilder(className)
                val generateType = result
                getOriginatingElements(generateType.getCoveredRequests()).forEach(Consumer { originatingElement: ExecutableElement? -> builder.addOriginatingElement(originatingElement) })
                generateType.buildType(canonicalClassName, builder)
                val generatedType = builder.build()
                val javaFile = JavaFile.builder(className.packageName(), generatedType).indent("    ").build()
                try {
                    javaFile.writeTo(filer)
                } catch (e: IOException) {
                    messager.printMessage(Diagnostic.Kind.ERROR, "Failed to write generated source file in package " + className.packageName() + ", named " + generatedType.name)
                }
            }
        } else if (result is CodeFailures) {
            printFailures(result)
        }

        // Right now every InstrumentationResourceGenerator have to generate it's own resource, but if needed,
        // we can extend this and we can support write to composite resources in a similar way as we do for classes
        for (resourceGenerator in resourceGenerators) {
            generateResource(resourceGenerator, interceptionRequests)
        }
    }

    private fun generateResource(resourceGenerator: InstrumentationResourceGenerator, interceptionRequests: MutableCollection<CallInterceptionRequest?>?) {
        val filteredRequests = resourceGenerator.filterRequestsForResource(interceptionRequests)
        if (filteredRequests.isEmpty()) {
            return
        }

        val result = resourceGenerator.generateResourceForRequests(filteredRequests)
        if (result is CanGenerateResource) {
            val resourceResult = result
            try {
                val originatingElements = getOriginatingElements(filteredRequests).toTypedArray<Element?>()
                val resource = filer.createResource(StandardLocation.CLASS_OUTPUT, resourceResult.getPackageName(), resourceResult.getName(), *originatingElements)
                resource.openOutputStream().use { outputStream ->
                    result.write(outputStream)
                }
            } catch (e: IOException) {
                messager.printMessage(
                    Diagnostic.Kind.ERROR,
                    "Failed to write generated resource file in package " + resourceResult.getPackageName() + ", named " + resourceResult.getName() + ": " + e.message
                )
            }
        } else if (result is ResourceFailures) {
            printFailures(result)
        }
    }

    private fun printFailures(failure: HasFailures) {
        failure.getFailureDetails().forEach(Consumer { details: FailureInfo? ->
            val maybeOriginatingElement =
                Optional.ofNullable<CallInterceptionRequest?>(details!!.request)
                    .flatMap<ExecutableElement?>(Function { presentRequest: CallInterceptionRequest? ->
                        presentRequest!!.requestExtras!!.getByType(OriginatingElement::class.java).map(OriginatingElement::getElement)
                    })
            if (maybeOriginatingElement.isPresent()) {
                messager.printMessage(Diagnostic.Kind.ERROR, details.reason, maybeOriginatingElement.get())
            } else {
                messager.printMessage(Diagnostic.Kind.ERROR, details.reason)
            }
        })
    }

    companion object {
        private fun getOriginatingElements(coveredRequests: MutableCollection<CallInterceptionRequest?>): MutableSet<ExecutableElement?> {
            return coveredRequests.stream()
                .map<kotlin.Any?> { requests: CallInterceptionRequest? -> requests!!.requestExtras!!.getByType(OriginatingElement::class.java).map(OriginatingElement::getElement).orElse(null) }
                .filter { obj: Any? -> Objects.nonNull(obj) }.collect(
                    Collectors.toSet()
                )
        }
    }
}
