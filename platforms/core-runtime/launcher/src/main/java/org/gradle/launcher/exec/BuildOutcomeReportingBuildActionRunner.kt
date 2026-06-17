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
package org.gradle.launcher.exec

import org.gradle.StartParameter
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.tasks.execution.statistics.TaskExecutionStatisticsEventAdapter
import org.gradle.api.logging.Logging
import org.gradle.api.problems.internal.ExceptionProblemRegistry
import org.gradle.initialization.BuildRequestMetaData
import org.gradle.internal.buildevents.BuildLogger
import org.gradle.internal.buildevents.BuildLoggerFactory
import org.gradle.internal.buildevents.BuildStartedTime
import org.gradle.internal.buildevents.TaskExecutionStatisticsReporter
import org.gradle.internal.buildtree.BuildActionRunner
import org.gradle.internal.buildtree.BuildTreeLifecycleController
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.invocation.BuildAction
import org.gradle.internal.logging.text.StyledTextOutputFactory
import org.gradle.internal.problems.failure.FailureFactory
import java.util.function.Consumer

class BuildOutcomeReportingBuildActionRunner(
    private val styledTextOutputFactory: StyledTextOutputFactory,
    private val listenerManager: ListenerManager,
    private val buildStartedTime: BuildStartedTime,
    private val buildRequestMetaData: BuildRequestMetaData,
    private val buildLoggerFactory: BuildLoggerFactory,
    private val failureFactory: FailureFactory,
    private val registry: ExceptionProblemRegistry,
    private val delegate: BuildActionRunner
) : BuildActionRunner {
    override fun run(action: BuildAction, buildController: BuildTreeLifecycleController): BuildActionRunner.Result {
        val startParameter: StartParameter = action.getStartParameter()
        val taskStatisticsCollector = TaskExecutionStatisticsEventAdapter()
        listenerManager.addListener(taskStatisticsCollector)

        val buildLogger = buildLoggerFactory.create(
            Logging.getLogger(BuildLogger::class.java),
            startParameter,
            buildStartedTime,
            buildRequestMetaData
        )
        // Register as a 'logger' to support this being replaced by build logic.
        buildController.beforeBuild(Consumer { gradle: GradleInternal? -> Companion.callUseLogger(gradle!!, buildLogger) })

        val result = delegate.run(action, buildController)

        val problemLocator = registry.getProblemLocator()
        val buildFailure = result.getBuildFailure()
        val richBuildFailure = if (buildFailure == null) null else failureFactory.create(buildFailure, problemLocator)
        buildLogger.logResult(richBuildFailure)
        TaskExecutionStatisticsReporter(styledTextOutputFactory).buildFinished(taskStatisticsCollector.getStatistics())
        if (buildFailure != null) {
            return result.withFailure(richBuildFailure!!)
        } else {
            return result
        }
    }

    companion object {
        @Suppress("deprecation")
        private fun callUseLogger(gradle: GradleInternal, logger: BuildLogger) {
            gradle.useLogger(logger)
        }
    }
}
