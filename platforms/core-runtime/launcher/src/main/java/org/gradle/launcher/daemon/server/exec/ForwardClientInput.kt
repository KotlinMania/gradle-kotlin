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

import org.gradle.api.internal.tasks.userinput.UserInputReader
import org.gradle.internal.daemon.clientinput.ClientInputForwarder
import org.gradle.internal.daemon.clientinput.StdinHandler
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.launcher.daemon.server.api.DaemonCommandAction
import org.gradle.launcher.daemon.server.api.DaemonCommandExecution
import java.util.function.Function

/**
 * Listens for [org.gradle.launcher.daemon.protocol.InputMessage] commands during execution and forwards them to this process' System.in and services such
 * as [UserInputReader].
 */
class ForwardClientInput(inputReader: UserInputReader, eventDispatch: OutputEventListener) : DaemonCommandAction {
    private val forwarder: ClientInputForwarder

    init {
        this.forwarder = ClientInputForwarder(inputReader, eventDispatch)
    }

    override fun execute(execution: DaemonCommandExecution) {
        forwarder.forwardInput<Any?>(Function { stdinHandler: StdinHandler? ->
            execution.connection!!.onStdin(stdinHandler)
            try {
                execution.proceed()
            } finally {
                execution.connection.onStdin(null)
            }
            null
        })
    }
}
