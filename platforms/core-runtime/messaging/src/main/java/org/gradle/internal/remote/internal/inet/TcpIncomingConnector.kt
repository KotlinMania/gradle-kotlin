/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.internal.remote.internal.inet

import org.gradle.api.Action
import org.gradle.internal.UncheckedException
import org.gradle.internal.concurrent.CompositeStoppable
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.id.IdGenerator
import org.gradle.internal.remote.Address
import org.gradle.internal.remote.ConnectionAcceptor
import org.gradle.internal.remote.internal.ConnectCompletion
import org.gradle.internal.remote.internal.IncomingConnector
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.util.UUID
import java.util.concurrent.TimeUnit

class TcpIncomingConnector(
    private val executorFactory: ExecutorFactory,
    private val addressFactory: InetAddressFactory,
    private val idGenerator: IdGenerator<UUID>,
    private val acceptTimeoutSeconds: Int
) : IncomingConnector {
    override fun accept(action: Action<ConnectCompletion>, allowRemote: Boolean): ConnectionAcceptor {
        val serverSocket: ServerSocketChannel
        val localPort: Int
        try {
            serverSocket = ServerSocketChannel.open()
            serverSocket.socket().bind(InetSocketAddress(addressFactory.getLocalBindingAddress(), 0))
            localPort = serverSocket.socket().getLocalPort()
        } catch (e: Exception) {
            throw UncheckedException.throwAsUncheckedException(e)
        }

        val id = idGenerator.generateId()
        val addresses = mutableListOf<InetAddress>(addressFactory.getLocalBindingAddress())
        val address: Address = MultiChoiceAddress(id, localPort, addresses)
        LOGGER.debug("Listening on {}.", address)

        val executor = executorFactory.create("Incoming " + (if (allowRemote) "remote" else "local") + " TCP Connector on port " + localPort)
        executor.execute(TcpIncomingConnector.Receiver(serverSocket, action, allowRemote))

        return object : ConnectionAcceptor {
            override fun getAddress(): Address {
                return address
            }

            override fun requestStop() {
                CompositeStoppable.stoppable(serverSocket).stop()
            }

            override fun stop() {
                requestStop()
                executor.stop()
            }
        }
    }

    private inner class Receiver(private val serverSocket: ServerSocketChannel, private val action: Action<ConnectCompletion>, private val allowRemote: Boolean) : Runnable {
        override fun run() {
            try {
                try {
                    while (true) {
                        val socket = serverSocket.accept()
                        val remoteSocketAddress = socket.socket().getRemoteSocketAddress() as InetSocketAddress
                        val remoteInetAddress = remoteSocketAddress.getAddress()
                        if (!allowRemote && !addressFactory.isCommunicationAddress(remoteInetAddress)) {
                            LOGGER.error("Cannot accept connection from remote address {}.", remoteSocketAddress)
                            socket.close()
                            continue
                        }
                        try {
                            SocketBlockingUtil.configureNonblocking(socket)
                            waitForConnectionPreamble(socket)
                        } catch (e: IOException) {
                            LOGGER.error("Failed connection handshake with {}.", remoteSocketAddress, e)
                            socket.close()
                            continue
                        }

                        LOGGER.debug("Accepted connection from {} to {}.", socket.socket().getRemoteSocketAddress(), socket.socket().getLocalSocketAddress())
                        try {
                            action.execute(SocketConnectCompletion(socket))
                        } catch (t: Throwable) {
                            socket.close()
                            throw t
                        }
                    }
                } catch (e: ClosedChannelException) {
                    // Ignore
                } catch (e: Throwable) {
                    LOGGER.error("Could not accept remote connection.", e)
                }
            } finally {
                CompositeStoppable.stoppable(serverSocket).stop()
            }
        }

        @Throws(IOException::class, InterruptedException::class)
        fun waitForConnectionPreamble(socket: SocketChannel) {
            val buffer = ByteBuffer.allocate(TcpOutgoingConnector.Companion.CONNECTION_PREAMBLE.size)
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(acceptTimeoutSeconds.toLong())
            while (buffer.hasRemaining() && System.nanoTime() < deadline) {
                val read = socket.read(buffer)
                if (read == -1) {
                    break
                }
                if (read == 0) {
                    Thread.sleep(1)
                }
            }
            if (!buffer.array().contentEquals(TcpOutgoingConnector.Companion.CONNECTION_PREAMBLE)) {
                throw IOException("Did not receive connection preamble within " + acceptTimeoutSeconds + "s")
            }
        }
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(TcpIncomingConnector::class.java)
    }
}
