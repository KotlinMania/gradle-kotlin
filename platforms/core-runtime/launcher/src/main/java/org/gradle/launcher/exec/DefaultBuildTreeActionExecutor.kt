/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.launcher.exec

import org.gradle.internal.Factory
import org.gradle.internal.UncheckedException
import org.gradle.internal.build.BuildLayoutValidator
import org.gradle.internal.buildoption.InternalOptions
import org.gradle.internal.buildtree.BuildActionModelRequirements
import org.gradle.internal.buildtree.BuildActionRunner
import org.gradle.internal.buildtree.BuildModelParametersFactory
import org.gradle.internal.buildtree.BuildTreeActionExecutor
import org.gradle.internal.buildtree.BuildTreeState
import org.gradle.internal.buildtree.RunTasksRequirements
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.Hashing
import org.gradle.internal.id.UniqueId
import org.gradle.internal.invocation.BuildAction
import org.gradle.internal.operations.BuildOperationContext
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.internal.operations.CallableBuildOperation
import org.gradle.internal.operations.logging.LoggingBuildOperationProgressBroadcaster
import org.gradle.internal.operations.notify.BuildOperationNotificationValve
import org.gradle.internal.problems.failure.Failure
import org.gradle.internal.scopeids.id.BuildInvocationScopeId
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.snapshot.ValueSnapshotter
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.tooling.internal.provider.action.BuildModelAction
import org.gradle.tooling.internal.provider.action.ClientProvidedBuildAction
import org.gradle.tooling.internal.provider.action.ClientProvidedPhasedAction
import java.util.function.Supplier

class DefaultBuildTreeActionExecutor(
    private val buildModelParametersFactory: BuildModelParametersFactory,
    private val buildLayoutValidator: BuildLayoutValidator,
    private val valueSnapshotter: ValueSnapshotter,
    private val options: InternalOptions,
    private val workerLeaseService: WorkerLeaseService,
    private val buildOperationRunner: BuildOperationRunner,
    private val loggingBuildOperationProgressBroadcaster: LoggingBuildOperationProgressBroadcaster,
    private val buildOperationNotificationValve: BuildOperationNotificationValve
) : BuildTreeActionExecutor {
    override fun runBuildTreeAction(action: BuildAction, buildSessionServices: ServiceRegistry): BuildActionRunner.Result {
        return workerLeaseService.runAsWorkerThread<BuildActionRunner.Result>(Factory { runAsBuildOperation(action, buildSessionServices) })
    }

    private fun runAsBuildOperation(action: BuildAction, buildSessionServices: ServiceRegistry): BuildActionRunner.Result {
        buildOperationNotificationValve.start()
        try {
            return buildOperationRunner.call<BuildActionRunner.Result>(object : CallableBuildOperation<BuildActionRunner.Result> {
                override fun call(buildOperationContext: BuildOperationContext): BuildActionRunner.Result {
                    loggingBuildOperationProgressBroadcaster.rootBuildOperationStarted()
                    val result = runBuildTreeLifecycle(action, buildSessionServices)
                    buildOperationContext.setResult(DefaultRunBuildResult(result))
                    if (result.getBuildFailure() != null) {
                        buildOperationContext.failed(result.getBuildFailure())
                    }
                    return result
                }

                override fun description(): BuildOperationDescriptor.Builder {
                    return BuildOperationDescriptor.displayName("Run build").details(DETAILS)
                }
            })
        } finally {
            buildOperationNotificationValve.stop()
        }
    }

    private fun runBuildTreeLifecycle(action: BuildAction, buildSessionServices: ServiceRegistry): BuildActionRunner.Result {
        var result: BuildActionRunner.Result? = null
        try {
            buildLayoutValidator.validate(action.getStartParameter())

            val actionRequirements = buildActionModelRequirementsFor(action)
            val buildModelParameters = buildModelParametersFactory.parametersForRootBuildTree(actionRequirements, options)
            val buildInvocationScopeId = BuildInvocationScopeId(UniqueId.generate())
            BuildTreeState(buildSessionServices, actionRequirements, buildModelParameters, buildInvocationScopeId).use { buildTree ->
                // assign instead of return to allow combining build failures with cleanup failures below
                result = buildTree.getServices().get<RootBuildLifecycleBuildActionExecutor>(RootBuildLifecycleBuildActionExecutor::class.java).execute(action)
            }
        } catch (t: Throwable) {
            // If cleanup has failed, combine the cleanup failure with other failures that may be packed in the result
            val failure = if (result == null) t else result.addFailure(t).getBuildFailure()
            // Note: throw the failure rather than returning a result object containing the failure, as console failure logging based on the _result_ happens down in the root build scope
            // whereas console failure logging based on the _thrown exception_ happens up outside session scope. It would be better to refactor so that a result can be returned from here
            throw UncheckedException.throwAsUncheckedException(failure)
        }
        return result!!
    }

    private fun buildActionModelRequirementsFor(action: BuildAction): BuildActionModelRequirements {
        if (action is BuildModelAction && action.isCreateModel()) {
            val buildModelAction = action
            val payload: Any = buildModelAction.getModelName()
            return QueryModelRequirements(action.getStartParameter(), action.isRunTasks(), payloadHashProvider(payload))
        } else if (action is ClientProvidedBuildAction) {
            val payload = action.getAction()
            return RunActionRequirements(action.getStartParameter(), action.isRunTasks(), payloadHashProvider(payload))
        } else if (action is ClientProvidedPhasedAction) {
            val payload = action.getPhasedAction()
            return RunPhasedActionRequirements(action.getStartParameter(), action.isRunTasks(), payloadHashProvider(payload))
        } else {
            return RunTasksRequirements(action.getStartParameter())
        }
    }

    private fun payloadHashProvider(payload: Any): Supplier<HashCode> {
        val valueSnapshotter = this.valueSnapshotter
        return Supplier { Hashing.hashHashable(valueSnapshotter.snapshot(payload)) }
    }

    private class DefaultRunBuildResult(private val result: BuildActionRunner.Result) : RunBuildBuildOperationType.Result {
        override fun getFailure(): Failure? {
            return result.getRichBuildFailure()
        }
    }

    companion object {
        private val DETAILS: RunBuildBuildOperationType.Details = object : RunBuildBuildOperationType.Details {
        }
    }
}
