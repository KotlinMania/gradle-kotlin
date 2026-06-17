/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.tooling.internal.provider

import org.gradle.initialization.BuildEventConsumer
import org.gradle.tooling.internal.protocol.PhasedActionResult
import org.gradle.tooling.internal.protocol.PhasedActionResultListener
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer

/**
 * Consumer of events from phased actions. This consumer deserializes the results and forward them to the correct listener.
 */
class PhasedActionEventConsumer internal constructor(
    private val phasedActionResultListener: PhasedActionResultListener,
    private val payloadSerializer: PayloadSerializer,
    private val delegate: BuildEventConsumer
) : BuildEventConsumer {
    override fun dispatch(event: Any) {
        if (event is PhasedBuildActionResult) {
            val resultEvent = event
            val deserializedResult = payloadSerializer.deserialize(resultEvent.result)

            phasedActionResultListener.onResult(object : PhasedActionResult<Any> {
                override fun getResult(): Any {
                    return deserializedResult!!
                }

                override fun getPhase(): PhasedActionResult.Phase {
                    return resultEvent.phase
                }
            })
        } else {
            delegate.dispatch(event)
        }
    }
}
