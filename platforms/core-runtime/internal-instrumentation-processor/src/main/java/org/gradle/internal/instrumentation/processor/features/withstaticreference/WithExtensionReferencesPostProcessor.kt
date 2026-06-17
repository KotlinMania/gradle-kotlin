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
package org.gradle.internal.instrumentation.processor.features.withstaticreference

import org.gradle.internal.instrumentation.model.CallInterceptionRequest
import org.gradle.internal.instrumentation.model.CallInterceptionRequestImpl
import org.gradle.internal.instrumentation.model.CallableInfo
import org.gradle.internal.instrumentation.model.CallableInfoImpl
import org.gradle.internal.instrumentation.model.CallableKindInfo
import org.gradle.internal.instrumentation.model.CallableOwnerInfo
import org.gradle.internal.instrumentation.model.ParameterInfo
import org.gradle.internal.instrumentation.model.ParameterInfoImpl
import org.gradle.internal.instrumentation.model.ParameterKindInfo
import org.gradle.internal.instrumentation.model.RequestExtra
import org.gradle.internal.instrumentation.model.RequestExtrasContainer
import org.gradle.internal.instrumentation.processor.extensibility.RequestPostProcessorExtension
import org.gradle.internal.instrumentation.processor.features.withstaticreference.WithExtensionReferencesExtra.ProducedSynthetically
import java.util.Arrays
import java.util.Optional
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Collectors
import java.util.stream.Stream
import kotlin.collections.ArrayList
import kotlin.collections.MutableCollection
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf

class WithExtensionReferencesPostProcessor : RequestPostProcessorExtension {
    override fun postProcessRequest(originalRequest: CallInterceptionRequest): MutableCollection<CallInterceptionRequest?>? {
        val extra: Optional<WithExtensionReferencesExtra?> = originalRequest.requestExtras!!.getByType(WithExtensionReferencesExtra::class.java)
        return extra
            .map<MutableList<CallInterceptionRequest?>?>(Function { withExtensionReferencesExtra: WithExtensionReferencesExtra? ->
                Arrays.asList<CallInterceptionRequest?>(
                    originalRequest,
                    Companion.modifiedRequest(originalRequest, withExtensionReferencesExtra!!)
                )
            })
            .orElseGet(Supplier { mutableListOf<CallInterceptionRequest?>(originalRequest) })
    }

    companion object {
        private fun modifiedRequest(originalRequest: CallInterceptionRequest, extra: WithExtensionReferencesExtra): CallInterceptionRequest {
            return CallInterceptionRequestImpl(
                Companion.modifiedCallableInfo(originalRequest.interceptedCallable!!, extra),
                originalRequest.implementationInfo,
                Companion.modifiedExtras(originalRequest.requestExtras!!)
            )
        }

        private fun modifiedCallableInfo(originalInfo: CallableInfo, extra: WithExtensionReferencesExtra): CallableInfo {
            val owner = CallableOwnerInfo(extra.ownerType, false)
            val methodName = extra.methodName
            return CallableInfoImpl(CallableKindInfo.STATIC_METHOD, owner, methodName, originalInfo.returnType, Companion.modifiedParameters(originalInfo.parameters!!))
        }

        private fun modifiedParameters(originalParameters: MutableList<ParameterInfo?>): MutableList<ParameterInfo> {
            val result = ArrayList<ParameterInfo>(originalParameters)
            if (result.size == 0 || result.get(0).kind !== ParameterKindInfo.RECEIVER) {
                throw UnsupportedOperationException("extensions with static references that do not have a receiver parameter are not supported")
            }
            val originalReceiver = result.removeAt(0)
            result.add(0, ParameterInfoImpl("receiverArg", originalReceiver.parameterType, ParameterKindInfo.METHOD_PARAMETER))
            return result
        }

        private fun modifiedExtras(originalExtras: RequestExtrasContainer): MutableList<RequestExtra?> {
            return Stream.of<Stream<RequestExtra?>?>(
                originalExtras.all.stream().filter { it: RequestExtra? -> it !is WithExtensionReferencesExtra },
                Stream.of<RequestExtra?>(ProducedSynthetically())
            ).flatMap<RequestExtra?>(Function.identity<Stream<RequestExtra?>?>()).collect(Collectors.toList())
        }
    }
}
