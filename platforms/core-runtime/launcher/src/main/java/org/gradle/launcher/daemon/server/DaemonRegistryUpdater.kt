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
package org.gradle.launcher.daemon.server

import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.internal.concurrent.Stoppable
import org.gradle.internal.remote.Address
import org.gradle.launcher.daemon.context.DaemonContext
import org.gradle.launcher.daemon.logging.DaemonMessages
import org.gradle.launcher.daemon.registry.DaemonInfo
import org.gradle.launcher.daemon.registry.DaemonRegistry
import org.gradle.launcher.daemon.registry.DaemonStopEvent
import org.gradle.launcher.daemon.server.api.DaemonState
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationStatus
import java.util.Date

internal class DaemonRegistryUpdater(private val daemonRegistry: DaemonRegistry, private val daemonContext: DaemonContext, private val token: ByteArray) : Stoppable {
    private var connectorAddress: Address? = null

    fun onStartActivity() {
        LOGGER.info("Marking the daemon as busy, address: {}", connectorAddress)
        try {
            daemonRegistry.markState(connectorAddress, DaemonState.Busy)
        } catch (e: DaemonRegistry.EmptyRegistryException) {
            LOGGER.warn("Cannot mark daemon as busy because the registry is empty.")
        }
    }

    fun onCompleteActivity() {
        LOGGER.info("Marking the daemon as idle, address: {}", connectorAddress)
        try {
            daemonRegistry.markState(connectorAddress, DaemonState.Idle)
        } catch (e: DaemonRegistry.EmptyRegistryException) {
            LOGGER.warn("Cannot mark daemon as idle because the registry is empty.")
        }
    }

    fun onCancel() {
        LOGGER.info("Marking the daemon as canceled, address: {}", connectorAddress)
        try {
            daemonRegistry.markState(connectorAddress, DaemonState.Canceled)
        } catch (e: DaemonRegistry.EmptyRegistryException) {
            LOGGER.warn("Cannot mark daemon as canceled because the registry is empty.")
        }
    }

    fun onStart(connectorAddress: Address) {
        LOGGER.info("{}{}", DaemonMessages.ADVERTISING_DAEMON, connectorAddress)
        LOGGER.debug("Advertised daemon context: {}", daemonContext)
        this.connectorAddress = connectorAddress
        daemonRegistry.store(DaemonInfo(connectorAddress, daemonContext, token, DaemonState.Busy))
    }

    fun onExpire(reason: String, status: DaemonExpirationStatus) {
        LOGGER.debug("Storing daemon stop event: {}", reason)
        val timestamp = Date(System.currentTimeMillis())
        daemonRegistry.storeStopEvent(DaemonStopEvent(timestamp, daemonContext.getPid(), status, reason))
    }

    override fun stop() {
        LOGGER.debug("Removing our presence to clients, eg. removing this address from the registry: {}", connectorAddress)
        try {
            daemonRegistry.remove(connectorAddress)
        } catch (e: DaemonRegistry.EmptyRegistryException) {
            LOGGER.warn("Cannot remove daemon from the registry because the registry is empty.")
        }
        LOGGER.debug("Address removed from registry.")
    }

    companion object {
        private val LOGGER: Logger = Logging.getLogger(DaemonRegistryUpdater::class.java)
    }
}
