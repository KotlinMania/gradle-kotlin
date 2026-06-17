/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.launcher.cli

import org.gradle.api.internal.StartParameterInternal
import org.gradle.configuration.GradleLauncherMetaData
import org.gradle.initialization.DefaultBuildCancellationToken
import org.gradle.initialization.NoOpBuildEventConsumer
import org.gradle.initialization.ReportedException
import org.gradle.internal.concurrent.Stoppable
import org.gradle.internal.daemon.client.execution.ClientBuildRequestContext
import org.gradle.internal.nativeintegration.console.ConsoleDetector
import org.gradle.internal.service.ServiceRegistry
import org.gradle.launcher.exec.BuildActionExecutor
import org.gradle.launcher.exec.BuildActionParameters
import org.gradle.tooling.internal.provider.action.ExecuteBuildAction

class RunBuildAction(
    private val executor: BuildActionExecutor<BuildActionParameters?, ClientBuildRequestContext?>,
    private val startParameter: StartParameterInternal?,
    private val clientMetaData: GradleLauncherMetaData,
    private val startTime: Long,
    private val buildActionParameters: BuildActionParameters?,
    private val sharedServices: ServiceRegistry,
    private val stoppable: Stoppable
) : Runnable {
    override fun run() {
        try {
            val result = executor.execute(
                ExecuteBuildAction(startParameter),
                buildActionParameters,
                ClientBuildRequestContext(
                    clientMetaData,
                    startTime,
                    sharedServices.get<ConsoleDetector?>(ConsoleDetector::class.java)!!.isInteractiveConsole,
                    DefaultBuildCancellationToken(),
                    NoOpBuildEventConsumer()
                )
            )
            if (result.hasFailure()) {
                // Don't need to unpack the serialized failure. It will already have been reported and is not used by anything downstream of this action.
                throw ReportedException()
            }
        } finally {
            stoppable.stop()
        }
    }
}
