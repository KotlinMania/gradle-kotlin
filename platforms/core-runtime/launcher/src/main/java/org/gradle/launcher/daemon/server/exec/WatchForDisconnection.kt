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
import org.gradle.launcher.daemon.server.api.DaemonCommandAction
import org.gradle.launcher.daemon.server.api.DaemonCommandExecution

class WatchForDisconnection : DaemonCommandAction {
    override fun execute(execution: DaemonCommandExecution) {
        // Watch for the client disconnecting before we call stop()
        execution.connection!!.onDisconnect(object : Runnable {
            override fun run() {
                LOGGER.warn("thread {}: client disconnection detected, canceling the build", Thread.currentThread().getId())
                execution.daemonStateControl!!.requestCancel()
            }
        })

        try {
            execution.proceed()
        } finally {
            // Remove the handler
            execution.connection.onDisconnect(null)
        }
    }

    companion object {
        private val LOGGER: Logger = Logging.getLogger(WatchForDisconnection::class.java)
        const val EXPIRATION_REASON: String = "client disconnected"
    }
}
