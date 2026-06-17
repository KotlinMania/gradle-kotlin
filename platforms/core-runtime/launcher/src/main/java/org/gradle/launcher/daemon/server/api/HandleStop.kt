/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.launcher.daemon.server.api

import org.gradle.internal.event.ListenerManager
import org.gradle.launcher.daemon.protocol.Stop
import org.gradle.launcher.daemon.protocol.StopWhenIdle
import org.gradle.launcher.daemon.protocol.Success
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationListener
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationResult
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationStatus

class HandleStop(listenerManager: ListenerManager) : DaemonCommandAction {
    private val listenerBroadcast: DaemonExpirationListener

    init {
        this.listenerBroadcast = listenerManager.getBroadcaster<DaemonExpirationListener>(DaemonExpirationListener::class.java)
    }

    override fun execute(execution: DaemonCommandExecution) {
        if (execution.getCommand() is Stop) {
            listenerBroadcast.onExpirationEvent(DaemonExpirationResult(DaemonExpirationStatus.IMMEDIATE_EXPIRE, EXPIRATION_REASON))
            execution.getConnection().completed(Success(null))
        } else if (execution.getCommand() is StopWhenIdle) {
            hangShutdownForTesting()
            listenerBroadcast.onExpirationEvent(DaemonExpirationResult(DaemonExpirationStatus.GRACEFUL_EXPIRE, EXPIRATION_REASON))
            execution.getConnection().completed(Success(null))
        } else {
            execution.proceed()
        }
    }

    private fun hangShutdownForTesting() {
        // For testing purposes, if this system property is set, the daemon will wait the specified
        // number of milliseconds before continuing to shutdown. This simulates builds for daemons
        // that are very slow to respond.
        val hang = Integer.getInteger("org.gradle.internal.testing.daemon.hang", 0)
        if (hang > 0) {
            try {
                Thread.sleep(hang.toLong())
            } catch (e: InterruptedException) {
                // ignore
            }
        }
    }

    companion object {
        const val EXPIRATION_REASON: String = "stop command received"
    }
}
