/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.api.internal.tasks.userinput.UserInputReader
import org.gradle.initialization.BuildRequestContext
import org.gradle.internal.daemon.client.clientinput.DaemonClientInputForwarder
import org.gradle.internal.daemon.clientinput.ClientInputForwarder
import org.gradle.internal.daemon.clientinput.StdinHandler
import org.gradle.internal.dispatch.Dispatch
import org.gradle.internal.invocation.BuildAction
import org.gradle.internal.logging.console.GlobalUserInputReceiver
import org.gradle.internal.logging.events.OutputEvent
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.logging.events.ReadStdInEvent
import org.gradle.launcher.daemon.protocol.CloseInput
import org.gradle.launcher.daemon.protocol.ForwardInput
import org.gradle.launcher.daemon.protocol.InputMessage
import org.gradle.launcher.daemon.protocol.UserResponse
import org.gradle.launcher.exec.BuildActionExecutor
import org.gradle.launcher.exec.BuildActionParameters
import org.gradle.launcher.exec.BuildActionResult
import java.io.InputStream
import java.util.function.Function

/**
 * Used in tooling API embedded mode to forward client provided user input to this process's System.in and other relevant services.
 * Reuses the services used by the daemon client and daemon server to forward user input.
 */
class ForwardStdInToThisProcess(
    private val userInputReceiver: GlobalUserInputReceiver,
    private val userInputReader: UserInputReader,
    private val finalStandardInput: InputStream,
    private val delegate: BuildActionExecutor<BuildActionParameters, BuildRequestContext>
) : BuildActionExecutor<BuildActionParameters, BuildRequestContext> {
    override fun execute(action: BuildAction, actionParameters: BuildActionParameters, buildRequestContext: BuildRequestContext): BuildActionResult {
        val forwarder = ClientInputForwarder(userInputReader, OutputEventListener { event: OutputEvent? ->
            if (event is ReadStdInEvent) {
                userInputReceiver.readAndForwardStdin(event)
            } else {
                throw IllegalArgumentException()
            }
        })
        return forwarder.forwardInput<BuildActionResult>(Function { stdinHandler: StdinHandler? ->
            val inputForwarder = DaemonClientInputForwarder(finalStandardInput, Dispatch { message: InputMessage? ->
                if (message is UserResponse) {
                    stdinHandler!!.onUserResponse(message)
                } else if (message is ForwardInput) {
                    stdinHandler!!.onInput(message)
                } else if (message is CloseInput) {
                    stdinHandler!!.onEndOfInput()
                } else {
                    throw IllegalArgumentException()
                }
            }, userInputReceiver)
            try {
                return@forwardInput delegate.execute(action, actionParameters, buildRequestContext)
            } finally {
                inputForwarder.stop()
                stdinHandler!!.onEndOfInput()
            }
        })
    }
}
