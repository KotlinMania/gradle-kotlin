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
package org.gradle.tooling.internal.provider

import org.gradle.api.logging.configuration.ConsoleOutput
import org.gradle.internal.concurrent.Stoppable
import org.gradle.internal.daemon.client.execution.ClientBuildRequestContext
import org.gradle.internal.invocation.BuildAction
import org.gradle.internal.io.NullOutputStream
import org.gradle.internal.logging.LoggingManagerInternal
import org.gradle.internal.logging.events.OutputEvent
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.logging.events.ProgressCompleteEvent
import org.gradle.internal.logging.events.ProgressStartEvent
import org.gradle.launcher.exec.BuildActionExecutor
import org.gradle.launcher.exec.BuildActionResult
import org.gradle.tooling.internal.protocol.ProgressListenerVersion1
import org.gradle.tooling.internal.provider.connection.ProviderOperationParameters
import java.io.OutputStream
import java.lang.Boolean

/**
 * A [BuildActionExecutor] which routes Gradle logging to those listeners specified in the [ProviderOperationParameters] provided with a tooling api build request.
 */
class LoggingBridgingBuildActionExecuter(
    private val delegate: BuildActionExecutor<ConnectionOperationParameters, ClientBuildRequestContext>,
    private val loggingManager: LoggingManagerInternal,
    private val stoppable: Stoppable
) : BuildActionExecutor<ConnectionOperationParameters, ClientBuildRequestContext> {
    override fun execute(action: BuildAction, parameters: ConnectionOperationParameters, buildRequestContext: ClientBuildRequestContext): BuildActionResult {
        val actionParameters = parameters.getOperationParameters()
        attachConsole(actionParameters)
        val progressListener: ProgressListenerVersion1 = actionParameters.progressListener
        val listener = OutputEventListenerAdapter(progressListener)
        loggingManager.addOutputEventListener(listener)
        loggingManager.setLevelInternal(actionParameters.buildLogLevel)
        loggingManager.start()
        try {
            return delegate.execute(action, parameters, buildRequestContext)
        } finally {
            loggingManager.stop()
            stoppable.stop()
        }
    }

    private fun attachConsole(actionParameters: ProviderOperationParameters) {
        val stdOut: OutputStream? = actionParameters.standardOutput
        val stdErr: OutputStream? = actionParameters.standardError
        if (Boolean.TRUE == actionParameters.isColorOutput && stdOut != null) {
            loggingManager.attachConsole(stdOut, notNull(stdErr!!), ConsoleOutput.Rich)
        } else if (stdOut != null || stdErr != null) {
            loggingManager.attachConsole(notNull(stdOut!!), notNull(stdErr!!), ConsoleOutput.Plain)
        }
    }

    private fun notNull(outputStream: OutputStream): OutputStream {
        if (outputStream == null) {
            return NullOutputStream.INSTANCE
        }
        return outputStream
    }

    private class OutputEventListenerAdapter(private val progressListener: ProgressListenerVersion1) : OutputEventListener {
        override fun onOutput(event: OutputEvent) {
            if (event is ProgressStartEvent) {
                val startEvent = event
                progressListener.onOperationStart(startEvent.getDescription())
            } else if (event is ProgressCompleteEvent) {
                progressListener.onOperationEnd()
            }
        }
    }
}
