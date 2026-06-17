/*
 * Copyright 2012 the original author or authors.
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
import org.gradle.internal.UncheckedException
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.concurrent.ManagedExecutor
import org.gradle.internal.concurrent.Stoppable
import org.gradle.launcher.daemon.context.DaemonContext
import org.gradle.launcher.daemon.logging.DaemonMessages
import org.gradle.launcher.daemon.protocol.Command
import org.gradle.launcher.daemon.protocol.Failure
import org.gradle.launcher.daemon.protocol.Message
import org.gradle.launcher.daemon.server.api.DaemonConnection
import org.gradle.launcher.daemon.server.api.DaemonStateControl
import org.gradle.launcher.daemon.server.exec.DaemonCommandExecuter
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

class DefaultIncomingConnectionHandler(
    private val commandExecuter: DaemonCommandExecuter,
    private val daemonContext: DaemonContext,
    private val daemonStateControl: DaemonStateControl,
    private val executorFactory: ExecutorFactory,
    private val token: ByteArray
) : IncomingConnectionHandler, Stoppable {
    private val workers: ManagedExecutor
    private val lock: Lock = ReentrantLock()
    private val condition: Condition = lock.newCondition()
    private val inProgress: MutableSet<SynchronizedDispatchConnection<Message>> = HashSet<SynchronizedDispatchConnection<Message>>()

    init {
        this.workers = executorFactory.create("Daemon")
    }

    override fun handle(connection: SynchronizedDispatchConnection<Message>) {
        // Mark the connection has being handled
        onStartHandling(connection)

        //we're spinning a thread to do work to avoid blocking the connection
        //This means that the Daemon potentially can do multiple things but we only allows a single build at a time
        workers.execute(DefaultIncomingConnectionHandler.ConnectionWorker(connection))
    }

    private fun onStartHandling(connection: SynchronizedDispatchConnection<Message>) {
        lock.lock()
        try {
            inProgress.add(connection)
        } finally {
            lock.unlock()
        }
    }

    private fun onFinishHandling(connection: SynchronizedDispatchConnection<Message>) {
        lock.lock()
        try {
            inProgress.remove(connection)
            condition.signalAll()
        } finally {
            lock.unlock()
        }
    }

    /**
     * Blocks until all connections have been handled or abandoned.
     */
    override fun stop() {
        lock.lock()
        try {
            while (!inProgress.isEmpty()) {
                try {
                    condition.await()
                } catch (e: InterruptedException) {
                    throw UncheckedException.throwAsUncheckedException(e)
                }
            }
        } finally {
            lock.unlock()
        }
    }

    private inner class ConnectionWorker(private val connection: SynchronizedDispatchConnection<Message>) : Runnable {
        override fun run() {
            try {
                receiveAndHandleCommand()
            } finally {
                onFinishHandling(connection)
            }
        }

        fun receiveAndHandleCommand() {
            try {
                val daemonConnection = DefaultDaemonConnection(connection, executorFactory)
                try {
                    val command = receiveCommand(daemonConnection)
                    if (command != null) {
                        handleCommand(command, daemonConnection)
                    }
                } finally {
                    daemonConnection.stop()
                }
            } finally {
                connection.stop()
            }
        }

        fun receiveCommand(daemonConnection: DaemonConnection): Command {
            try {
                val command = daemonConnection.receive(120, TimeUnit.SECONDS) as Command
                LOGGER.info("Received command: {}.", command)
                return command
            } catch (e: Throwable) {
                LOGGER.warn(String.format("Unable to receive command from client %s. Discarding connection.", connection), e)
                return null
            }
        }

        fun handleCommand(command: Command, daemonConnection: DaemonConnection) {
            LOGGER.debug("{}{} with connection: {}.", DaemonMessages.STARTED_EXECUTING_COMMAND, command, connection)
            try {
                if (!command.getToken().contentEquals(token)) {
                    throw BadlyFormedRequestException(String.format("Unexpected authentication token in command %s received from %s", command, connection))
                }
                commandExecuter.executeCommand(daemonConnection, command, daemonContext, daemonStateControl)
            } catch (e: Throwable) {
                LOGGER.warn(String.format("Unable to execute command %s from %s. Dispatching the failure to the daemon client", command, connection), e)
                daemonConnection.completed(Failure(e))
            } finally {
                LOGGER.debug("{}{}", DaemonMessages.FINISHED_EXECUTING_COMMAND, command)
            }

            val finished = daemonConnection.receive(60, TimeUnit.SECONDS)
            if (finished != null) {
                LOGGER.debug("Received finished message: {}", finished)
            } else {
                LOGGER.warn(String.format("Timed out waiting for finished message from client %s. Discarding connection.", connection))
            }
        }
    }

    companion object {
        private val LOGGER: Logger = Logging.getLogger(DefaultIncomingConnectionHandler::class.java)
    }
}
