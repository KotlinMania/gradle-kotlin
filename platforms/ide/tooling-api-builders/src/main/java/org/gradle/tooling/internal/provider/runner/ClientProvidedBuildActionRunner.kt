/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.internal.buildtree.BuildActionRunner
import org.gradle.internal.buildtree.BuildTreeLifecycleController
import org.gradle.internal.invocation.BuildAction
import org.gradle.tooling.internal.protocol.PhasedActionResult
import org.gradle.tooling.internal.provider.action.ClientProvidedBuildAction
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer
import org.gradle.tooling.internal.provider.serialization.SerializedPayload

class ClientProvidedBuildActionRunner(
    buildControllerFactory: BuildControllerFactory?,
    private val payloadSerializer: PayloadSerializer
) : AbstractClientProvidedBuildActionRunner(
    buildControllerFactory,
    payloadSerializer
), BuildActionRunner {
    override fun run(action: BuildAction, buildController: BuildTreeLifecycleController): BuildActionRunner.Result {
        if (action !is ClientProvidedBuildAction) {
            return BuildActionRunner.Result.nothing()
        }

        val clientProvidedBuildAction = action

        val clientAction = payloadSerializer.deserialize(clientProvidedBuildAction.getAction())

        return runClientAction(ClientActionImpl(clientAction, action), buildController)
    }

    private class ClientActionImpl(private val clientAction: Any?, private val action: BuildAction) : ClientAction {
        private var result: SerializedPayload? = null

        override fun getProjectsEvaluatedAction(): Any? {
            return null
        }

        override fun getBuildFinishedAction(): Any? {
            return clientAction
        }

        override fun collectActionResult(serializedResult: SerializedPayload?, phase: PhasedActionResult.Phase?) {
            this.result = serializedResult
        }

        override fun getResult(): SerializedPayload? {
            return result
        }

        override fun isRunTasks(): Boolean {
            return action.isRunTasks()
        }
    }
}
