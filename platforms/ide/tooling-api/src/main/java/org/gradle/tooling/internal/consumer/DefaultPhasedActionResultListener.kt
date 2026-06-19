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
package org.gradle.tooling.internal.consumer

import org.gradle.tooling.IntermediateResultHandler
import org.gradle.tooling.internal.protocol.PhasedActionResult
import org.gradle.tooling.internal.protocol.PhasedActionResultListener

/**
 * Adapts individual result handlers of actions in a [PhasedBuildAction] to a unified listener to be provided to the connection.
 */
class DefaultPhasedActionResultListener(
    private val projectsLoadedHandler: IntermediateResultHandler<*>?,
    private val buildFinishedHandler: IntermediateResultHandler<*>?
) : PhasedActionResultListener {
    override fun onResult(result: PhasedActionResult<*>?) {
        val model: Any? = result!!.result
        val type = result.phase
        if (type == PhasedActionResult.Phase.PROJECTS_LOADED) {
            onComplete(model, projectsLoadedHandler)
        } else if (type == PhasedActionResult.Phase.BUILD_FINISHED) {
            onComplete(model, buildFinishedHandler)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun onComplete(result: Any?, handler: IntermediateResultHandler<*>?) {
        if (handler != null) {
            (handler as IntermediateResultHandler<Any?>).onComplete(result)
        }
    }
}
