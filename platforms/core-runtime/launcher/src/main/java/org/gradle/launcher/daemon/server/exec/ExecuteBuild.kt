/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.launcher.daemon.server.exec

import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.configuration.DefaultBuildClientMetaData
import org.gradle.initialization.BuildCancellationToken
import org.gradle.initialization.BuildRequestContext
import org.gradle.initialization.BuildRequestMetaData
import org.gradle.initialization.DefaultBuildRequestContext
import org.gradle.initialization.DefaultBuildRequestMetaData
import org.gradle.launcher.daemon.logging.DaemonMessages
import org.gradle.launcher.daemon.protocol.Build
import org.gradle.launcher.daemon.server.api.DaemonCommandExecution
import org.gradle.launcher.daemon.server.stats.DaemonRunningStats
import org.gradle.launcher.exec.BuildActionExecutor
import org.gradle.launcher.exec.BuildActionParameters

/**
 * Actually executes the build.
 *
 * Typically the last action in the pipeline.
 */
class ExecuteBuild(private val actionExecuter: BuildActionExecutor<BuildActionParameters?, BuildRequestContext?>, private val runningStats: DaemonRunningStats) : BuildCommandOnly() {
    override fun doBuild(execution: DaemonCommandExecution, build: Build) {
        LOGGER.debug(DaemonMessages.STARTED_BUILD)
        LOGGER.debug("Executing build with daemon context: {}", execution.daemonContext)
        runningStats.buildStarted()
        val buildEventConsumer = DaemonConnectionBackedEventConsumer(execution)
        try {
            val cancellationToken: BuildCancellationToken = execution.daemonStateControl!!.cancellationToken
            val clientMetaData = DefaultBuildClientMetaData(build.getBuildClientMetaData())
            val buildRequestMetaData: BuildRequestMetaData = DefaultBuildRequestMetaData(clientMetaData, build.getStartTime(), build.isInteractiveConsole())
            val buildRequestContext: BuildRequestContext = DefaultBuildRequestContext(buildRequestMetaData, cancellationToken, buildEventConsumer)
            if (!build.getAction().getStartParameter().isContinuous()) {
                buildRequestContext.getCancellationToken().addCallback(object : Runnable {
                    override fun run() {
                        LOGGER.info(DaemonMessages.CANCELED_BUILD)
                    }
                })
            }
            val result = actionExecuter.execute(build.getAction(), build.getParameters(), buildRequestContext)
            execution.result = result
        } finally {
            buildEventConsumer.waitForFinish()
            runningStats.buildFinished()
            LOGGER.debug(DaemonMessages.FINISHED_BUILD)
        }

        execution.proceed() // ExecuteBuild should be the last action, but in case we want to decorate the result in the future
    }

    companion object {
        private val LOGGER: Logger = Logging.getLogger(ExecuteBuild::class.java)
    }
}
