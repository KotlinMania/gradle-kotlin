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
import java.util.Optional
import kotlin.collections.ArrayList
import kotlin.collections.MutableCollection
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf

class WithExtensionReferencesPostProcessor : RequestPostProcessorExtension {
    override fun postProcessRequest(originalRequest: CallInterceptionRequest?): MutableCollection<CallInterceptionRequest?>? {
        val request = checkNotNull(originalRequest)
        val extra: Optional<WithExtensionReferencesExtra> = checkNotNull(request.requestExtras).getByType(WithExtensionReferencesExtra::class.java)
        return extra
            .map { withExtensionReferencesExtra: WithExtensionReferencesExtra ->
                mutableListOf<CallInterceptionRequest?>(request, Companion.modifiedRequest(request, withExtensionReferencesExtra))
            }
            .orElse(mutableListOf(request))
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
            return CallableInfoImpl(
                CallableKindInfo.STATIC_METHOD,
                owner,
                methodName,
                originalInfo.returnType,
                Companion.modifiedParameters(checkNotNull(originalInfo.parameters))
            )
        }

        private fun modifiedParameters(originalParameters: MutableList<ParameterInfo>): MutableList<ParameterInfo> {
            val result = ArrayList<ParameterInfo>(originalParameters)
            if (result.size == 0) {
                throw UnsupportedOperationException("extensions with static references that do not have a receiver parameter are not supported")
            }
            val originalReceiver = result[0]
            if (originalReceiver.kind !== ParameterKindInfo.RECEIVER) {
                throw UnsupportedOperationException("extensions with static references that do not have a receiver parameter are not supported")
            }
            result.removeAt(0)
            result.add(0, ParameterInfoImpl("receiverArg", originalReceiver.parameterType, ParameterKindInfo.METHOD_PARAMETER))
            return result
        }

        private fun modifiedExtras(originalExtras: RequestExtrasContainer): MutableList<RequestExtra?> {
            val result: MutableList<RequestExtra?> = originalExtras.all.filter { it !is WithExtensionReferencesExtra }.toMutableList()
            result.add(ProducedSynthetically())
            return result
        }
    }
}
