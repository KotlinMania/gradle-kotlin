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
package org.gradle.launcher.daemon.server

import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.internal.concurrent.CompositeStoppable
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.concurrent.Stoppable
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.remote.Address
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.launcher.daemon.context.DaemonContext
import org.gradle.launcher.daemon.logging.DaemonMessages
import org.gradle.launcher.daemon.registry.DaemonRegistry
import org.gradle.launcher.daemon.server.api.DaemonStateControl
import org.gradle.launcher.daemon.server.exec.DaemonCommandExecuter
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationListener
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationResult
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationStatus
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationStrategy
import org.gradle.process.internal.shutdown.ShutdownHooks
import java.security.SecureRandom
import java.util.Date
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

/**
 * A long-lived build server that accepts commands via a communication channel.
 *
 *
 * Daemon instances are single use and have a start/stop debug. They are also threadsafe.
 *
 *
 * See [org.gradle.launcher.daemon.client.DaemonClient] for a description of the daemon communication protocol.
 */
@ServiceScope(Scope.Global::class)
class Daemon(
    private val connector: DaemonServerConnector,
    val daemonRegistry: DaemonRegistry,
    val daemonContext: DaemonContext,
    private val commandExecuter: DaemonCommandExecuter,
    private val executorFactory: ExecutorFactory,
    private val listenerManager: ListenerManager
) : Stoppable {
    private val scheduledExecutorService: ScheduledExecutorService

    private var stateCoordinator: DaemonStateCoordinator? = null

    private val lifecycleLock: Lock = ReentrantLock()

    private var connectorAddress: Address? = null
    private var registryUpdater: DaemonRegistryUpdater? = null
    private var connectionHandler: DefaultIncomingConnectionHandler? = null

    /**
     * Creates a new daemon instance.
     *
     * @param connector The provider of server connections for this daemon
     * @param daemonRegistry The registry that this daemon should advertise itself in
     */
    init {
        this.scheduledExecutorService = executorFactory.createScheduled("Daemon periodic checks", 1)
    }

    val uid: String
        get() = daemonContext.getUid()

    val address: Address
        get() = connectorAddress

    /**
     * Starts the daemon, receiving connections asynchronously (i.e. returns immediately).
     *
     * @throws IllegalStateException if this daemon is already running, or has already been stopped.
     */
    fun start() {
        LOGGER.info("start() called on daemon - {}", daemonContext)
        lifecycleLock.lock()
        try {
            check(stateCoordinator == null) { "cannot start daemon as it is already running" }

            // Generate an authentication token, which must be provided by the client in any requests it makes
            val secureRandom = SecureRandom()
            val token = ByteArray(16)
            secureRandom.nextBytes(token)

            registryUpdater = DaemonRegistryUpdater(daemonRegistry, daemonContext, token)

            ShutdownHooks.addShutdownHook(object : Runnable {
                override fun run() {
                    try {
                        daemonRegistry.remove(connectorAddress)
                    } catch (e: Exception) {
                        LOGGER.debug("VM shutdown hook was unable to remove the daemon address from the registry. It will be cleaned up later.", e)
                    }
                }
            })

            val onStartCommand: Runnable = object : Runnable {
                override fun run() {
                    registryUpdater!!.onStartActivity()
                }
            }

            val onFinishCommand: Runnable = object : Runnable {
                override fun run() {
                    registryUpdater!!.onCompleteActivity()
                }
            }

            val onCancelCommand: Runnable = object : Runnable {
                override fun run() {
                    registryUpdater!!.onCancel()
                }
            }

            // Start the pipeline in reverse order:
            // 1. mark daemon as running
            // 2. start handling incoming commands
            // 3. start accepting incoming connections
            // 4. advertise presence in registry
            stateCoordinator = DaemonStateCoordinator(executorFactory, onStartCommand, onFinishCommand, onCancelCommand)
            connectionHandler = DefaultIncomingConnectionHandler(commandExecuter, daemonContext, stateCoordinator!!, executorFactory, token)
            val connectionErrorHandler: Runnable = object : Runnable {
                override fun run() {
                    stateCoordinator!!.stop()
                }
            }
            connectorAddress = connector.start(connectionHandler!!, connectionErrorHandler)
            LOGGER.debug("Daemon starting at: {}, with address: {}", Date(), connectorAddress)
            registryUpdater!!.onStart(connectorAddress!!)
        } finally {
            lifecycleLock.unlock()
        }

        LOGGER.lifecycle(DaemonMessages.PROCESS_STARTED)
    }

    /**
     * Stops the daemon, blocking until any current requests/connections have been satisfied.
     *
     *
     * This is the semantically the same as sending the daemon the Stop command.
     *
     *
     * This method does not quite conform to the semantics of the Stoppable contract in that it will NOT
     * wait for any executing builds to stop before returning. This is by design as we currently have no way of
     * gracefully stopping a build process and blocking until it's done would not allow us to tear down the jvm
     * like we need to. This may change in the future if we create a way to interrupt a build.
     *
     *
     * What will happen though is that the daemon will immediately disconnect from any clients and remove itself
     * from the registry.
     */
    override fun stop() {
        LOGGER.debug("stop() called on daemon")
        lifecycleLock.lock()
        try {
            checkNotNull(stateCoordinator) { "cannot stop daemon as it has not been started." }

            LOGGER.info(DaemonMessages.REMOVING_PRESENCE_DUE_TO_STOP)

            // Stop periodic checks
            scheduledExecutorService.shutdown()

            // Stop the pipeline:
            // 1. mark daemon as stopped, so that any incoming requests will be rejected with 'daemon unavailable'
            // 2. remove presence from registry
            // 3. stop accepting new connections
            // 4. wait for commands in progress to finish (except for abandoned long running commands, like running a build)
            CompositeStoppable.stoppable(stateCoordinator!!, registryUpdater!!, connector, connectionHandler!!).stop()
        } finally {
            lifecycleLock.unlock()
        }
    }

    fun stopOnExpiration(expirationStrategy: DaemonExpirationStrategy, checkIntervalMills: Int): DaemonStopState {
        LOGGER.debug("stopOnExpiration() called on daemon")
        scheduleExpirationChecks(expirationStrategy, checkIntervalMills)
        return awaitExpiration()
    }

    private fun scheduleExpirationChecks(expirationStrategy: DaemonExpirationStrategy, checkIntervalMills: Int) {
        val periodicCheck = DaemonExpirationPeriodicCheck(expirationStrategy, listenerManager)
        listenerManager.addListener(Daemon.DefaultDaemonExpirationListener(stateCoordinator!!, registryUpdater!!))
        val ignored = scheduledExecutorService.scheduleAtFixedRate(periodicCheck, checkIntervalMills.toLong(), checkIntervalMills.toLong(), TimeUnit.MILLISECONDS)
    }

    /**
     * Tell DaemonStateCoordinator to block until it's state is Stopped.
     */
    private fun awaitExpiration(): DaemonStopState {
        LOGGER.debug("awaitExpiration() called on daemon")

        var stateCoordinator: DaemonStateCoordinator?
        lifecycleLock.lock()
        try {
            checkNotNull(this.stateCoordinator) { "cannot await stop on daemon as it has not been started." }
            stateCoordinator = this.stateCoordinator
        } finally {
            lifecycleLock.unlock()
        }

        return stateCoordinator!!.awaitStop()
    }

    fun getStateCoordinator(): DaemonStateCoordinator {
        return stateCoordinator!!
    }

    private class DaemonExpirationPeriodicCheck(private val expirationStrategy: DaemonExpirationStrategy, listenerManager: ListenerManager) : Runnable {
        private val listenerBroadcast: DaemonExpirationListener

        init {
            this.listenerBroadcast = listenerManager.getBroadcaster<DaemonExpirationListener>(DaemonExpirationListener::class.java)
        }

        override fun run() {
            try {
                LOGGER.debug("Starting periodic daemon health check.")
                val result = expirationStrategy.checkExpiration()
                if (result!!.status != DaemonExpirationStatus.DO_NOT_EXPIRE) {
                    listenerBroadcast.onExpirationEvent(result)
                }
                LOGGER.debug("Finished periodic daemon health check.")
            } catch (t: Throwable) {
                // this class is used as task in a scheduled executor service, so it must not throw any throwable,
                // otherwise the further invocations of this task get automatically and silently cancelled
                LOGGER.error("Problem in daemon expiration check", t)
            }
        }
    }

    private class DefaultDaemonExpirationListener(private val stateControl: DaemonStateControl, private val registryUpdater: DaemonRegistryUpdater) : DaemonExpirationListener {
        override fun onExpirationEvent(result: DaemonExpirationResult) {
            val expirationCheck = result.status

            if (expirationCheck != DaemonExpirationStatus.DO_NOT_EXPIRE) {
                if (expirationCheck != DaemonExpirationStatus.QUIET_EXPIRE) {
                    registryUpdater.onExpire(result.reason!!, expirationCheck!!)
                }

                if (expirationCheck == DaemonExpirationStatus.IMMEDIATE_EXPIRE) {
                    stateControl.requestForcefulStop(result.reason)
                } else {
                    stateControl.requestStop(result.reason)
                }
            }
        }
    }

    companion object {
        private val LOGGER: Logger = Logging.getLogger(Daemon::class.java)
    }
}
