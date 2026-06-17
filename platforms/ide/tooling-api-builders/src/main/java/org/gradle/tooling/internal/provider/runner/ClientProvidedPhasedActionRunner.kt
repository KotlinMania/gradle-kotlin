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
package org.gradle.tooling.internal.provider.runner

import org.gradle.initialization.BuildEventConsumer
import org.gradle.internal.buildtree.BuildActionRunner
import org.gradle.internal.buildtree.BuildTreeLifecycleController
import org.gradle.internal.buildtree.BuildTreeModelSideEffect
import org.gradle.internal.buildtree.BuildTreeModelSideEffectExecutor
import org.gradle.internal.invocation.BuildAction
import org.gradle.tooling.internal.protocol.InternalPhasedAction
import org.gradle.tooling.internal.protocol.PhasedActionResult
import org.gradle.tooling.internal.provider.PhasedBuildActionResult
import org.gradle.tooling.internal.provider.action.ClientProvidedPhasedAction
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer
import org.gradle.tooling.internal.provider.serialization.SerializedPayload

class ClientProvidedPhasedActionRunner(
    buildControllerFactory: BuildControllerFactory?,
    private val payloadSerializer: PayloadSerializer,
    private val buildEventConsumer: BuildEventConsumer,
    private val sideEffectExecutor: BuildTreeModelSideEffectExecutor
) : AbstractClientProvidedBuildActionRunner(buildControllerFactory, payloadSerializer), BuildActionRunner {
    override fun run(action: BuildAction, buildController: BuildTreeLifecycleController): BuildActionRunner.Result {
        if (action !is ClientProvidedPhasedAction) {
            return BuildActionRunner.Result.nothing()
        }

        val clientProvidedPhasedAction = action
        val phasedAction = payloadSerializer.deserialize(clientProvidedPhasedAction.getPhasedAction()) as InternalPhasedAction?

        return runClientAction(ClientProvidedPhasedActionRunner.ClientActionImpl(phasedAction, action), buildController)
    }

    private inner class ClientActionImpl(private val phasedAction: InternalPhasedAction, private val action: BuildAction) : ClientAction {
        override fun getProjectsEvaluatedAction(): Any {
            return phasedAction.projectsLoadedAction!!
        }

        override fun getBuildFinishedAction(): Any {
            return phasedAction.buildFinishedAction!!
        }

        override fun collectActionResult(serializedResult: SerializedPayload?, phase: PhasedActionResult.Phase?) {
            val phaseResult = PhasedBuildActionResult(serializedResult, phase)
            val buildEventConsumer = this@ClientProvidedPhasedActionRunner.buildEventConsumer
            sideEffectExecutor.runIsolatableSideEffect(BuildTreeModelSideEffect { buildEventConsumer.dispatch(phaseResult) })
        }

        override fun getResult(): SerializedPayload? {
            return null
        }

        override fun isRunTasks(): Boolean {
            return action.isRunTasks()
        }
    }
}
