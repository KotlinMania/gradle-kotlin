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

import org.gradle.api.Action
import org.gradle.internal.concurrent.CompositeStoppable
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.id.UUIDGenerator
import org.gradle.internal.remote.Address
import org.gradle.internal.remote.ConnectionAcceptor
import org.gradle.internal.remote.internal.ConnectCompletion
import org.gradle.internal.remote.internal.IncomingConnector
import org.gradle.internal.remote.internal.RemoteConnection
import org.gradle.internal.remote.internal.inet.InetAddressFactory
import org.gradle.internal.remote.internal.inet.TcpIncomingConnector
import org.gradle.internal.serialize.Serializer
import org.gradle.internal.serialize.Serializers
import org.gradle.launcher.daemon.protocol.Message
import java.io.UncheckedIOException
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

/**
 * Opens a TCP connection for clients to connect to communicate with a daemon.
 */
class DaemonTcpServerConnector(executorFactory: ExecutorFactory, inetAddressFactory: InetAddressFactory, private val serializer: Serializer<Message>) : DaemonServerConnector {
    private val incomingConnector: IncomingConnector

    private var started = false
    private var stopped = false
    private val lifecycleLock: Lock = ReentrantLock()
    private var acceptor: ConnectionAcceptor? = null

    init {
        this.incomingConnector = TcpIncomingConnector(
            executorFactory,
            inetAddressFactory,
            UUIDGenerator(),
            10
        )
    }

    override fun start(handler: IncomingConnectionHandler, connectionErrorHandler: Runnable): Address {
        lifecycleLock.lock()
        try {
            check(!stopped) { "server connector cannot be started as it is either stopping or has been stopped" }
            check(!started) { "server connector cannot be started as it has already been started" }

            // Hold the lock until we actually start accepting connections for the case when stop is called from another
            // thread while we are in the middle here.
            val connectEvent: Action<ConnectCompletion> = object : Action<ConnectCompletion> {
                override fun execute(completion: ConnectCompletion) {
                    val remoteConnection: RemoteConnection<Message>
                    try {
                        remoteConnection = completion.create<Message>(Serializers.stateful<Message>(serializer))
                    } catch (e: UncheckedIOException) {
                        connectionErrorHandler.run()
                        throw e
                    }
                    handler.handle(SynchronizedDispatchConnection<Message>(remoteConnection))
                }
            }

            acceptor = incomingConnector.accept(connectEvent, false)
            started = true
            return acceptor!!.address
        } finally {
            lifecycleLock.unlock()
        }
    }

    override fun stop() {
        lifecycleLock.lock()
        try {
            stopped = true
        } finally {
            lifecycleLock.unlock()
        }

        CompositeStoppable.stoppable(acceptor!!, incomingConnector).stop()
    }
}
