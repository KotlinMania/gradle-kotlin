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

import org.gradle.internal.instrumentation.model.CallableInfo.hasKotlinDefaultMaskParam
import org.gradle.internal.instrumentation.model.CallableInfo.hasCallerClassNameParam
import org.gradle.internal.instrumentation.model.CallableInfo.hasInjectVisitorContextParam
import org.gradle.internal.instrumentation.model.CallableOwnerInfo.isInterceptSubtypes
import org.gradle.internal.instrumentation.processor.codegen.RequestGroupingInstrumentationClassSourceGenerator
import org.gradle.internal.instrumentation.model.CallInterceptionRequest
import org.gradle.internal.instrumentation.model.CallableInfo
import org.gradle.internal.instrumentation.model.RequestExtra
import org.gradle.internal.instrumentation.processor.codegen.HasFailures.FailureInfo
import org.gradle.internal.instrumentation.processor.codegen.jvmbytecode.InterceptJvmCallsGenerator
import org.gradle.internal.instrumentation.model.CallableOwnerInfo
import org.gradle.internal.instrumentation.processor.codegen.GradleReferencedType
import org.gradle.internal.instrumentation.processor.codegen.jvmbytecode.InterceptJvmCallsGenerator.InvocationGenerator
import org.gradle.internal.instrumentation.processor.codegen.JavadocUtils
import org.gradle.internal.instrumentation.model.CallableKindInfo
import org.gradle.internal.instrumentation.model.ParameterKindInfo
import org.gradle.internal.instrumentation.processor.codegen.InstrumentationResourceGenerator
import org.gradle.internal.instrumentation.processor.codegen.InstrumentationResourceGenerator.GenerationResult.CanGenerateResource

/**
 * Generates META-INF/services resource with all factory classes for generated JvmBytecodeCallInterceptors so we can load them at runtime
 */
class InterceptJvmCallsResourceGenerator : InstrumentationResourceGenerator {
    override fun filterRequestsForResource(interceptionRequests: kotlin.collections.MutableCollection<CallInterceptionRequest?>): kotlin.collections.MutableCollection<CallInterceptionRequest?> {
        return interceptionRequests.stream()
            .filter { request: CallInterceptionRequest? -> request.requestExtras.getByType(org.gradle.internal.instrumentation.model.RequestExtra.InterceptJvmCalls::class.java).isPresent() }
            .collect(java.util.stream.Collectors.toList())
    }

    override fun generateResourceForRequests(filteredRequests: kotlin.collections.MutableCollection<CallInterceptionRequest?>): InstrumentationResourceGenerator.GenerationResult {
        return object : CanGenerateResource {
            override fun getPackageName(): kotlin.String {
                return ""
            }

            override fun getName(): kotlin.String {
                return "META-INF/services/" + org.gradle.internal.instrumentation.api.jvmbytecode.JvmBytecodeCallInterceptor.Factory::class.java.getName()
            }

            override fun write(outputStream: java.io.OutputStream) {
                val types: kotlin.String = filteredRequests.stream()
                    .map<kotlin.Any?> { request: CallInterceptionRequest? -> request.requestExtras.getByType(org.gradle.internal.instrumentation.model.RequestExtra.InterceptJvmCalls::class.java) }
                    .map<kotlin.String?> { extra: kotlin.Any? -> extra.get().implementationClassName + "\$Factory" }
                    .distinct()
                    .sorted()
                    .collect(java.util.stream.Collectors.joining("\n"))
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
