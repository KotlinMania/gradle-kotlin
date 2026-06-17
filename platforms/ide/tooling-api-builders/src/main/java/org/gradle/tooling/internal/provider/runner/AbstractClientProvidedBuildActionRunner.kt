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
import org.gradle.internal.buildtree.BuildTreeModelAction
import org.gradle.internal.buildtree.BuildTreeModelController
import org.gradle.tooling.internal.protocol.InternalBuildAction
import org.gradle.tooling.internal.protocol.InternalBuildActionFailureException
import org.gradle.tooling.internal.protocol.InternalBuildActionVersion2
import org.gradle.tooling.internal.protocol.PhasedActionResult
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer
import org.gradle.tooling.internal.provider.serialization.SerializedPayload

abstract class AbstractClientProvidedBuildActionRunner(private val buildControllerFactory: BuildControllerFactory, private val payloadSerializer: PayloadSerializer) : BuildActionRunner {
    protected fun runClientAction(action: ClientAction, buildController: BuildTreeLifecycleController): BuildActionRunner.Result {
        val adapter: ActionAdapter = AbstractClientProvidedBuildActionRunner.ActionAdapter(action, payloadSerializer)
        try {
            val result = buildController.fromBuildModel<SerializedPayload>(
                action.isRunTasks, adapter
            )
            return BuildActionRunner.Result.of(result)
        } catch (e: RuntimeException) {
            var clientFailure = e
            if (adapter.actionFailure != null) {
                clientFailure = InternalBuildActionFailureException(adapter.actionFailure)
            }
            return BuildActionRunner.Result.failed(e, clientFailure)
        }
    }

    protected interface ClientAction {
        val projectsEvaluatedAction: Any?

        val buildFinishedAction: Any?

        fun collectActionResult(serializedResult: SerializedPayload?, phase: PhasedActionResult.Phase?)

        val result: SerializedPayload?

        val isRunTasks: Boolean
    }

    private inner class ActionAdapter(private val clientAction: ClientAction, private val payloadSerializer: PayloadSerializer) : BuildTreeModelAction<SerializedPayload?> {
        var actionFailure: RuntimeException? = null

        override fun beforeTasks(controller: BuildTreeModelController) {
            runAction(controller, clientAction.projectsEvaluatedAction, PhasedActionResult.Phase.PROJECTS_LOADED)
        }

        override fun fromBuildModel(controller: BuildTreeModelController): SerializedPayload? {
            runAction(controller, clientAction.buildFinishedAction, PhasedActionResult.Phase.BUILD_FINISHED)
            return clientAction.result
        }

        fun runAction(controller: BuildTreeModelController?, action: Any?, phase: PhasedActionResult.Phase?) {
            if (action == null || actionFailure != null) {
                return
            }

            val internalBuildController = buildControllerFactory.controllerFor(controller)
            try {
                val result = executeAction(action, internalBuildController)
                val serializedResult = payloadSerializer.serialize(result)
                clientAction.collectActionResult(serializedResult, phase)
            } catch (e: RuntimeException) {
                actionFailure = e
                throw e
            }
        }

        @Suppress("deprecation")
        fun executeAction(action: Any, internalBuildController: DefaultBuildController?): Any? {
            if (action is InternalBuildActionVersion2<*>) {
                return action.execute(internalBuildController)
            } else {
                return (action as InternalBuildAction<*>).execute(internalBuildController)
            }
        }
    }
}
