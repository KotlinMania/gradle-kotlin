/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.StartParameter
import org.gradle.api.internal.StartParameterInternal
import org.gradle.api.internal.changedetection.state.FileHasherStatistics
import org.gradle.api.internal.tasks.userinput.BuildScanUserInputHandler
import org.gradle.api.internal.tasks.userinput.DefaultBuildScanUserInputHandler
import org.gradle.api.internal.tasks.userinput.DefaultUserInputHandler
import org.gradle.api.internal.tasks.userinput.NonInteractiveUserInputHandler
import org.gradle.api.internal.tasks.userinput.UserInputHandler
import org.gradle.api.internal.tasks.userinput.UserInputReader
import org.gradle.api.problems.internal.ExceptionProblemRegistry
import org.gradle.api.problems.internal.ProblemsInternal
import org.gradle.deployment.internal.DeploymentRegistryInternal
import org.gradle.execution.WorkValidationWarningReporter
import org.gradle.initialization.BuildCancellationToken
import org.gradle.initialization.BuildEventConsumer
import org.gradle.initialization.BuildRequestMetaData
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.build.event.BuildEventListenerFactory
import org.gradle.internal.buildevents.BuildLoggerFactory
import org.gradle.internal.buildevents.BuildStartedTime
import org.gradle.internal.buildoption.InternalOptions
import org.gradle.internal.buildtree.BuildActionRunner
import org.gradle.internal.buildtree.BuildModelParameters
import org.gradle.internal.buildtree.BuildTreeActionExecutor
import org.gradle.internal.buildtree.BuildTreeLifecycleListener
import org.gradle.internal.buildtree.ProblemReportingBuildActionRunner
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.enterprise.core.GradleEnterprisePluginManager
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.exception.ExceptionAnalyser
import org.gradle.internal.execution.WorkInputListeners
import org.gradle.internal.file.StatStatistics
import org.gradle.internal.initialization.layout.BuildTreeLocations
import org.gradle.internal.logging.sink.OutputEventListenerManager
import org.gradle.internal.logging.text.StyledTextOutputFactory
import org.gradle.internal.nativeintegration.filesystem.FileSystem
import org.gradle.internal.operations.BuildOperationListenerManager
import org.gradle.internal.operations.BuildOperationProgressEventEmitter
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.internal.problems.failure.FailureFactory
import org.gradle.internal.service.Provides
import org.gradle.internal.service.ServiceRegistration
import org.gradle.internal.service.ServiceRegistrationProvider
import org.gradle.internal.service.scopes.AbstractGradleModuleServices
import org.gradle.internal.session.BuildSessionActionExecutor
import org.gradle.internal.snapshot.CaseSensitivity
import org.gradle.internal.snapshot.impl.DirectorySnapshotterStatistics
import org.gradle.internal.time.Clock
import org.gradle.internal.time.Time
import org.gradle.internal.watch.vfs.BuildLifecycleAwareVirtualFileSystem
import org.gradle.internal.watch.vfs.FileChangeListeners
import org.gradle.internal.work.ProjectParallelExecutionController
import org.gradle.launcher.exec.BuildCompletionNotifyingBuildActionRunner
import org.gradle.launcher.exec.BuildOutcomeReportingBuildActionRunner
import org.gradle.launcher.exec.ChainingBuildActionRunner
import org.gradle.launcher.exec.DefaultBuildTreeActionExecutor
import org.gradle.launcher.exec.RootBuildLifecycleBuildActionExecutor
import org.gradle.problems.buildtree.ProblemDiagnosticsFactory
import org.gradle.problems.buildtree.ProblemReporter
import org.gradle.problems.buildtree.ProblemStream
import org.gradle.tooling.internal.provider.continuous.ContinuousBuildActionExecutor

class LauncherServices : AbstractGradleModuleServices() {
    override fun registerGlobalServices(registration: ServiceRegistration) {
        registration.add<ExecuteBuildActionRunner>(BuildActionRunner::class.java, ExecuteBuildActionRunner::class.java)
        registration.addProvider(ToolingGlobalScopeServices())
    }

    override fun registerBuildSessionServices(registration: ServiceRegistration) {
        registration.addProvider(ToolingBuildSessionScopeServices())
        registration.add<DefaultBuildTreeActionExecutor>(BuildTreeActionExecutor::class.java, DefaultBuildTreeActionExecutor::class.java)
    }

    override fun registerBuildTreeServices(registration: ServiceRegistration) {
        registration.addProvider(ToolingBuildTreeScopeServices())
    }

    internal class ToolingGlobalScopeServices : ServiceRegistrationProvider {
        @Provides
        fun createBuildLoggerFactory(
            styledTextOutputFactory: StyledTextOutputFactory,
            workValidationWarningReporter: WorkValidationWarningReporter,
            failureFactory: FailureFactory
        ): BuildLoggerFactory {
            return BuildLoggerFactory(
                styledTextOutputFactory,
                workValidationWarningReporter,
                Time.clock(),
                null,
                failureFactory
            )
        }
    }

    internal class ToolingBuildSessionScopeServices : ServiceRegistrationProvider {
        @Provides
        fun createActionExecutor(
            listenerFactory: BuildEventListenerFactory,
            executorFactory: ExecutorFactory,
            listenerManager: ListenerManager,
            buildOperationListenerManager: BuildOperationListenerManager,
            workListeners: WorkInputListeners,
            fileChangeListeners: FileChangeListeners,
            styledTextOutputFactory: StyledTextOutputFactory,
            requestMetaData: BuildRequestMetaData,
            cancellationToken: BuildCancellationToken,
            deploymentRegistry: DeploymentRegistryInternal,
            eventConsumer: BuildEventConsumer,
            buildStartedTime: BuildStartedTime,
            clock: Clock,
            fileSystem: FileSystem,
            virtualFileSystem: BuildLifecycleAwareVirtualFileSystem,
            buildTreeActionExecutor: BuildTreeActionExecutor
        ): BuildSessionActionExecutor {
            val caseSensitivity = if (fileSystem.isCaseSensitive) CaseSensitivity.CASE_SENSITIVE else CaseSensitivity.CASE_INSENSITIVE
            return SubscribableBuildActionExecutor(
                listenerManager,
                buildOperationListenerManager,
                listenerFactory, eventConsumer,
                ContinuousBuildActionExecutor(
                    workListeners,
                    fileChangeListeners,
                    styledTextOutputFactory,
                    executorFactory,
                    requestMetaData,
                    cancellationToken,
                    deploymentRegistry,
                    listenerManager,
                    buildStartedTime,
                    clock,
                    fileSystem,
                    caseSensitivity,
                    virtualFileSystem,
                    buildTreeActionExecutor
                )
            )
        }

        @Provides
        fun createUserInputHandler(
            startParameter: StartParameterInternal,
            requestMetaData: BuildRequestMetaData,
            outputEventListenerManager: OutputEventListenerManager,
            clock: Clock,
            inputReader: UserInputReader
        ): UserInputHandler {
            if (startParameter.isNonInteractive() || !requestMetaData.isInteractiveConsole()) {
                return NonInteractiveUserInputHandler()
            }

            return DefaultUserInputHandler(outputEventListenerManager.broadcaster, clock, inputReader)
        }

        @Provides
        fun createBuildScanUserInputHandler(userInputHandler: UserInputHandler): BuildScanUserInputHandler {
            return DefaultBuildScanUserInputHandler(userInputHandler)
        }
    }

    internal class ToolingBuildTreeScopeServices : ServiceRegistrationProvider {
        @Provides
        fun createProblemStream(parameter: StartParameter, diagnosticsFactory: ProblemDiagnosticsFactory): ProblemStream {
            return if (parameter.getWarningMode().shouldDisplayMessages()) diagnosticsFactory.newUnlimitedStream() else diagnosticsFactory.newStream()
        }

        @Provides
        fun createActionExecutor(
            buildModelParameters: BuildModelParameters,
            projectParallelExecutionController: ProjectParallelExecutionController,
            buildActionRunners: MutableList<BuildActionRunner>,
            styledTextOutputFactory: StyledTextOutputFactory,
            buildStateRegistry: BuildStateRegistry,
            eventEmitter: BuildOperationProgressEventEmitter,
            listenerManager: ListenerManager,
            buildStartedTime: BuildStartedTime,
            buildRequestMetaData: BuildRequestMetaData,
            gradleEnterprisePluginManager: GradleEnterprisePluginManager,
            virtualFileSystem: BuildLifecycleAwareVirtualFileSystem,
            deploymentRegistry: DeploymentRegistryInternal,
            statStatisticsCollector: StatStatistics.Collector,
            fileHasherStatisticsCollector: FileHasherStatistics.Collector,
            directorySnapshotterStatisticsCollector: DirectorySnapshotterStatistics.Collector,
            buildOperationRunner: BuildOperationRunner,
            buildTreeLocations: BuildTreeLocations,
            exceptionAnalyser: ExceptionAnalyser,
            problemReporters: MutableList<ProblemReporter>,
            buildLoggerFactory: BuildLoggerFactory,
            options: InternalOptions,
            startParameter: StartParameter,
            failureFactory: FailureFactory,
            problemsService: ProblemsInternal,
            problemStream: ProblemStream,
            registry: ExceptionProblemRegistry
        ): RootBuildLifecycleBuildActionExecutor {
            return RootBuildLifecycleBuildActionExecutor(
                buildModelParameters,
                projectParallelExecutionController,
                listenerManager.getBroadcaster<BuildTreeLifecycleListener>(BuildTreeLifecycleListener::class.java),
                problemsService,
                eventEmitter,
                startParameter,
                problemStream,
                buildStateRegistry,
                BuildCompletionNotifyingBuildActionRunner(
                    gradleEnterprisePluginManager,
                    failureFactory,
                    buildOperationRunner,
                    FileSystemWatchingBuildActionRunner(
                        eventEmitter,
                        virtualFileSystem,
                        deploymentRegistry,
                        statStatisticsCollector,
                        fileHasherStatisticsCollector,
                        directorySnapshotterStatisticsCollector,
                        buildOperationRunner,
                        options,
                        BuildOutcomeReportingBuildActionRunner(
                            styledTextOutputFactory,
                            listenerManager,
                            buildStartedTime,
                            buildRequestMetaData,
                            buildLoggerFactory,
                            failureFactory,
                            registry,
                            ProblemReportingBuildActionRunner(
                                exceptionAnalyser,
                                buildTreeLocations,
                                problemReporters,
                                ChainingBuildActionRunner(buildActionRunners)
                            )
                        )
                    )
                )
            )
        }

        @Provides
        fun createBuildLoggerFactory(
            styledTextOutputFactory: StyledTextOutputFactory,
            workValidationWarningReporter: WorkValidationWarningReporter,
            clock: Clock,
            gradleEnterprisePluginManager: GradleEnterprisePluginManager,
            failureFactory: FailureFactory
        ): BuildLoggerFactory {
            return BuildLoggerFactory(
                styledTextOutputFactory,
                workValidationWarningReporter,
                clock,
                gradleEnterprisePluginManager,
                failureFactory
            )
        }
    }
}
