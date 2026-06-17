/*
 * Copyright 2015 the original author or authors.
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

import org.gradle.api.tasks.testing.TestExecutionException
import org.gradle.internal.buildtree.BuildActionRunner
import org.gradle.internal.buildtree.BuildTreeLifecycleController
import org.gradle.internal.invocation.BuildAction
import org.gradle.internal.operations.BuildOperationAncestryTracker
import org.gradle.internal.operations.BuildOperationListenerManager
import org.gradle.tooling.internal.protocol.test.InternalTestExecutionException
import org.gradle.tooling.internal.provider.action.TestExecutionRequestAction

class TestExecutionRequestActionRunner(
    private val ancestryTracker: BuildOperationAncestryTracker?,
    private val buildOperationListenerManager: BuildOperationListenerManager
) : BuildActionRunner {
    override fun run(action: BuildAction, buildController: BuildTreeLifecycleController): BuildActionRunner.Result {
        if (action !is TestExecutionRequestAction) {
            return BuildActionRunner.Result.nothing()
        }

        try {
            val testExecutionRequestAction = action
            val testExecutionResultEvaluator = TestExecutionResultEvaluator(ancestryTracker, testExecutionRequestAction)
            buildOperationListenerManager.addListener(testExecutionResultEvaluator)
            try {
                doRun(testExecutionRequestAction, buildController)
            } finally {
                buildOperationListenerManager.removeListener(testExecutionResultEvaluator)
            }
            testExecutionResultEvaluator.evaluate()
        } catch (e: RuntimeException) {
            val throwable = findRootCause(e)
            if (throwable is TestExecutionException) {
                return BuildActionRunner.Result.failed(e, InternalTestExecutionException("Error while running test(s)", throwable))
            } else {
                return BuildActionRunner.Result.failed(e)
            }
        }

        return BuildActionRunner.Result.of(null)
    }

    private fun doRun(action: TestExecutionRequestAction, buildController: BuildTreeLifecycleController) {
        buildController.scheduleAndRunTasks(TestExecutionBuildConfigurationAction(action))
    }

    private fun findRootCause(tex: Exception): Throwable {
        var t: Throwable = tex
        while (t.cause != null) {
            t = t.cause!!
        }
        return t
    }
}
