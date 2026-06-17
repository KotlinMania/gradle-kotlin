/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.tooling.internal.provider.continuous

import org.gradle.api.Action
import org.gradle.api.logging.LogLevel
import org.gradle.deployment.internal.ContinuousExecutionGate
import org.gradle.deployment.internal.DefaultContinuousExecutionGate
import org.gradle.deployment.internal.DeploymentInternal
import org.gradle.deployment.internal.DeploymentRegistryInternal
import org.gradle.deployment.internal.PendingChangesListener
import org.gradle.execution.CancellableOperationManager
import org.gradle.execution.DefaultCancellableOperationManager
import org.gradle.execution.PassThruCancellableOperationManager
import org.gradle.initialization.BuildCancellationToken
import org.gradle.initialization.BuildRequestMetaData
import org.gradle.internal.buildevents.BuildStartedTime
import org.gradle.internal.buildtree.BuildActionRunner
import org.gradle.internal.buildtree.BuildTreeActionExecutor
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.execution.WorkInputListener
import org.gradle.internal.execution.WorkInputListeners
import org.gradle.internal.file.Stat
import org.gradle.internal.invocation.BuildAction
import org.gradle.internal.logging.text.StyledTextOutput
import org.gradle.internal.logging.text.StyledTextOutputFactory
import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.session.BuildSessionActionExecutor
import org.gradle.internal.session.BuildSessionContext
import org.gradle.internal.snapshot.CaseSensitivity
import org.gradle.internal.time.Clock
import org.gradle.internal.watch.vfs.BuildLifecycleAwareVirtualFileSystem
import org.gradle.internal.watch.vfs.FileChangeListeners
import org.gradle.util.internal.DisconnectableInputStream
import java.util.function.Supplier

class ContinuousBuildActionExecutor(
    private val inputsListeners: WorkInputListeners,
    private val fileChangeListeners: FileChangeListeners,
    styledTextOutputFactory: StyledTextOutputFactory,
    private val executorFactory: ExecutorFactory,
    private val requestMetaData: BuildRequestMetaData,
    private val cancellationToken: BuildCancellationToken,
    private val deploymentRegistry: DeploymentRegistryInternal,
    private val listenerManager: ListenerManager,
    private val buildStartedTime: BuildStartedTime,
    private val clock: Clock,
    private val stat: Stat,
    private val caseSensitivity: CaseSensitivity,
    private val virtualFileSystem: BuildLifecycleAwareVirtualFileSystem,
    private val delegate: BuildTreeActionExecutor
) : BuildSessionActionExecutor {
    private val operatingSystem: OperatingSystem
    private val logger: StyledTextOutput

    init {
        this.operatingSystem = OperatingSystem.current()
        this.logger = styledTextOutputFactory.create(ContinuousBuildActionExecutor::class.java, LogLevel.QUIET)
    }

    override fun execute(action: BuildAction, buildSession: BuildSessionContext): BuildActionRunner.Result {
        if (action.getStartParameter().isContinuous()) {
            val alwaysOpenExecutionGate = DefaultContinuousExecutionGate()
            val cancellableOperationManager = createCancellableOperationManager(requestMetaData, cancellationToken)
            return executeMultipleBuilds(action, requestMetaData, buildSession, cancellationToken, cancellableOperationManager, alwaysOpenExecutionGate)
        } else {
            try {
                return delegate.runBuildTreeAction(action, buildSession.getServices())
            } finally {
                val cancellableOperationManager = createCancellableOperationManager(requestMetaData, cancellationToken)
                waitForDeployments(action, requestMetaData, buildSession, cancellationToken, cancellableOperationManager)
            }
        }
    }

    private fun createCancellableOperationManager(requestContext: BuildRequestMetaData, cancellationToken: BuildCancellationToken): CancellableOperationManager {
        val cancellableOperationManager: CancellableOperationManager
        if (requestContext.isInteractiveConsole()) {
            if (System.`in` !is DisconnectableInputStream) {
                System.setIn(DisconnectableInputStream(System.`in`))
            }
            val inputStream = System.`in` as DisconnectableInputStream
            cancellableOperationManager = DefaultCancellableOperationManager(executorFactory.create("Cancel signal monitor"), inputStream, cancellationToken)
        } else {
            cancellableOperationManager = PassThruCancellableOperationManager(cancellationToken)
        }
        return cancellableOperationManager
    }

    private fun waitForDeployments(
        action: BuildAction,
        requestContext: BuildRequestMetaData,
        buildSession: BuildSessionContext,
        cancellationToken: BuildCancellationToken,
        cancellableOperationManager: CancellableOperationManager
    ) {
        if (!deploymentRegistry.getRunningDeployments().isEmpty()) {
            // Deployments are considered outOfDate until initial execution with file watching
            for (deployment in deploymentRegistry.getRunningDeployments()) {
                (deployment as DeploymentInternal).outOfDate()
            }
            logger.println().println("Reloadable deployment detected. Entering continuous build.")
            resetBuildStartedTime()
            val deploymentRequestExecutionGate = deploymentRegistry.getExecutionGate()
            executeMultipleBuilds(action, requestContext, buildSession, cancellationToken, cancellableOperationManager, deploymentRequestExecutionGate)
        }
        cancellableOperationManager.closeInput()
    }

    private fun executeMultipleBuilds(
        action: BuildAction,
        requestContext: BuildRequestMetaData,
        buildSession: BuildSessionContext,
        cancellationToken: BuildCancellationToken,
        cancellableOperationManager: CancellableOperationManager,
        continuousExecutionGate: ContinuousExecutionGate
    ): BuildActionRunner.Result {
        var lastResult: BuildActionRunner.Result
        val pendingChangesListener = listenerManager.getBroadcaster<PendingChangesListener>(PendingChangesListener::class.java)
        while (true) {
            val buildInputs = BuildInputHierarchy(caseSensitivity, stat)
            val continuousBuildTriggerHandler = ContinuousBuildTriggerHandler(
                cancellationToken,
                continuousExecutionGate,
                action.getStartParameter().continuousBuildQuietPeriod
            )
            val singleFirePendingChangesListener = SingleFirePendingChangesListener(pendingChangesListener)
            val fileEventCollector = FileEventCollector(buildInputs, Runnable {
                continuousBuildTriggerHandler.notifyFileChangeArrived()
                singleFirePendingChangesListener.onPendingChanges()
            })
            try {
                fileChangeListeners.addListener(fileEventCollector)
                lastResult = executeBuildAndAccumulateInputs(action, AccumulateBuildInputsListener(buildInputs), buildSession)

                // Let the VFS clean itself up after the build
                virtualFileSystem.afterBuildFinished()

                if (buildInputs.isEmpty()) {
                    logger.println().withStyle(StyledTextOutput.Style.Failure).println("Exiting continuous build as Gradle did not detect any file system inputs.")
                    return lastResult
                } else if (!continuousBuildTriggerHandler.hasBeenTriggered() && !virtualFileSystem.isWatchingAnyLocations()) {
                    logger.println().withStyle(StyledTextOutput.Style.Failure).println("Exiting continuous build as Gradle does not watch any file system locations.")
                    return lastResult
                } else {
                    cancellableOperationManager.monitorInput(Action { operationToken: BuildCancellationToken? ->
                        continuousBuildTriggerHandler.wait(
                            Runnable { logger.println().println("Waiting for changes to input files..." + determineExitHint(requestContext)) }
                        )
                        if (!operationToken!!.isCancellationRequested()) {
                            fileEventCollector.reportChanges(logger)
                        }
                    })
                }
            } finally {
                fileChangeListeners.removeListener(fileEventCollector)
            }

            if (cancellationToken.isCancellationRequested()) {
                break
            } else {
                logger.println("Change detected, executing build...").println()
                resetBuildStartedTime()
            }
        }

        logger.println("Build cancelled.")
        return lastResult
    }

    private fun resetBuildStartedTime() {
        buildStartedTime.reset(clock.currentTime)
    }

    private fun determineExitHint(requestContext: BuildRequestMetaData): String {
        if (requestContext.isInteractiveConsole()) {
            if (operatingSystem.isWindows()) {
                return " (ctrl-d then enter to exit)"
            } else {
                return " (ctrl-d to exit)"
            }
        } else {
            return ""
        }
    }

    private fun executeBuildAndAccumulateInputs(
        action: BuildAction,
        inputListener: WorkInputListener,
        buildSession: BuildSessionContext
    ): BuildActionRunner.Result {
        return withInputListener<BuildActionRunner.Result>(
            inputListener,
            java.util.function.Supplier { delegate.runBuildTreeAction(action, buildSession.getServices()) }
        )!!
    }

    private fun <T> withInputListener(listener: WorkInputListener, supplier: Supplier<T?>): T? {
        try {
            inputsListeners.addListener(listener)
            return supplier.get()
        } finally {
            inputsListeners.removeListener(listener)
        }
    }
}
