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
import org.gradle.launcher.daemon.diagnostics.DaemonDiagnostics
import org.gradle.launcher.daemon.protocol.Build
import org.gradle.launcher.daemon.protocol.BuildStarted
import org.gradle.launcher.daemon.protocol.DaemonUnavailable
import org.gradle.launcher.daemon.protocol.Failure
import org.gradle.launcher.daemon.server.api.DaemonCommandExecution
import org.gradle.launcher.daemon.server.api.DaemonStoppedException
import org.gradle.launcher.daemon.server.api.DaemonUnavailableException

/**
 * Updates the daemon idle/busy status, sending a DaemonUnavailable result back to the client if the daemon is busy.
 */
class StartBuildOrRespondWithBusy(private val diagnostics: DaemonDiagnostics?) : BuildCommandOnly() {
    override fun doBuild(execution: DaemonCommandExecution, build: Build?) {
        val stateCoordinator = execution.daemonStateControl

        try {
            val command: Runnable = object : Runnable {
                override fun run() {
                    LOGGER.info("Daemon is about to start building {}. Dispatching build started information...", build)
                    execution.connection!!.buildStarted(BuildStarted(diagnostics))
                    execution.proceed()
                }
            }

            stateCoordinator!!.runCommand(command, execution.toString())
        } catch (e: DaemonUnavailableException) {
            LOGGER.info("Daemon will not handle the command {} because is unavailable: {}", build, e.message)
            execution.connection!!.daemonUnavailable(DaemonUnavailable(e.message))
        } catch (e: DaemonStoppedException) {
            execution.connection!!.completed(Failure(e))
        }
    }

    companion object {
        private val LOGGER: Logger = Logging.getLogger(StartBuildOrRespondWithBusy::class.java)
    }
}
