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
import org.gradle.internal.buildtree.BuildTreeModelTarget
import org.gradle.internal.buildtree.ToolingModelRequestContext
import org.gradle.internal.invocation.BuildAction
import org.gradle.tooling.internal.protocol.InternalUnsupportedModelException
import org.gradle.tooling.internal.provider.action.BuildModelAction
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer
import org.gradle.tooling.provider.model.UnknownModelException
import org.gradle.tooling.provider.model.internal.ToolingModelBuilderResultInternal

class BuildModelActionRunner(private val payloadSerializer: PayloadSerializer) : BuildActionRunner {
    override fun run(action: BuildAction, buildController: BuildTreeLifecycleController): BuildActionRunner.Result {
        if (action !is BuildModelAction) {
            return BuildActionRunner.Result.nothing()
        }

        val buildModelAction = action

        val createAction = ModelCreateAction(buildModelAction)
        try {
            if (buildModelAction.isCreateModel()) {
                val result = buildController.fromBuildModel<ToolingModelBuilderResultInternal>(buildModelAction.isRunTasks(), createAction)
                val serializedResult = payloadSerializer.serialize(result.getModel())
                return BuildActionRunner.Result.of(serializedResult)
            } else {
                buildController.scheduleAndRunTasks()
                return BuildActionRunner.Result.of(null)
            }
        } catch (e: RuntimeException) {
            var clientFailure = e
            if (createAction.modelLookupFailure != null) {
                clientFailure = InternalUnsupportedModelException().initCause(createAction.modelLookupFailure) as RuntimeException
            }
            return BuildActionRunner.Result.failed(e, clientFailure)
        }
    }

    private class ModelCreateAction(private val buildModelAction: BuildModelAction) : BuildTreeModelAction<ToolingModelBuilderResultInternal?> {
        private var modelLookupFailure: UnknownModelException? = null

        override fun beforeTasks(controller: BuildTreeModelController) {
            // Ignore
        }

        override fun fromBuildModel(controller: BuildTreeModelController): ToolingModelBuilderResultInternal? {
            val modelName = buildModelAction.getModelName()
            try {
                val modelRequestContext = ToolingModelRequestContext(modelName, null, false)
                return controller.getModel(BuildTreeModelTarget.ofDefault(), modelRequestContext)
            } catch (e: UnknownModelException) {
                modelLookupFailure = e
                throw e
            }
        }
    }
}
