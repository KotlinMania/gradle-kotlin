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

import org.gradle.internal.UncheckedException
import org.gradle.internal.remote.Address
import org.gradle.internal.remote.internal.ConnectCompletion
import org.gradle.internal.remote.internal.ConnectException
import org.gradle.internal.remote.internal.OutgoingConnector
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketException
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets

class TcpOutgoingConnector : OutgoingConnector {
    @Throws(ConnectException::class)
    override fun connect(destinationAddress: Address): ConnectCompletion {
        require(destinationAddress is InetEndpoint) { String.format("Cannot create a connection to address of unknown type: %s.", destinationAddress) }
        val address = destinationAddress
        LOGGER.debug("Attempting to connect to {}.", address)

        // Try each address in turn. Not all of them are necessarily reachable (eg when socket option IPV6_V6ONLY
        // is on - the default for debian and others), so we will try each of them until we can connect
        val candidateAddresses = address.getCandidates()

        // Now try each address
        try {
            var lastFailure: Exception? = null
            for (candidate in candidateAddresses) {
                LOGGER.debug("Trying to connect to address {}.", candidate)
                val socketChannel: SocketChannel?
                try {
                    socketChannel = tryConnect(address, candidate)
                } catch (e: SocketException) {
                    LOGGER.debug("Cannot connect to address {}, skipping.", candidate)
                    lastFailure = e
                    continue
                } catch (e: SocketTimeoutException) {
                    LOGGER.debug("Timeout connecting to address {}, skipping.", candidate)
                    lastFailure = e
                    continue
                }
                LOGGER.debug("Connected to address {}.", socketChannel.socket().getRemoteSocketAddress())
                return SocketConnectCompletion(socketChannel)
            }
            throw ConnectException(
                String.format(
                    "Could not connect to server %s. Tried addresses: %s.",
                    destinationAddress, candidateAddresses
                ), lastFailure
            )
        } catch (e: ConnectException) {
            throw e
        } catch (e: Exception) {
            throw RuntimeException(
                String.format(
                    "Could not connect to server %s. Tried addresses: %s.",
                    destinationAddress, candidateAddresses
                ), e
            )
        }
    }

    @Throws(IOException::class)
    private fun tryConnect(address: InetEndpoint, candidate: InetAddress): SocketChannel {
        val socketChannel = SocketChannel.open()
        try {
            socketChannel.socket().connect(InetSocketAddress(candidate, address.getPort()), CONNECT_TIMEOUT)
            if (!detectSelfConnect(socketChannel)) {
                SocketBlockingUtil.configureNonblocking(socketChannel)
                socketChannel.write(ByteBuffer.wrap(CONNECTION_PREAMBLE))
                return socketChannel
            }
            socketChannel.close()
        } catch (e: IOException) {
            socketChannel.close()
            throw e
        } catch (e: Throwable) {
            socketChannel.close()
            throw UncheckedException.throwAsUncheckedException(e)
        }

        throw java.net.ConnectException(String.format("Socket connected to itself on %s port %s.", candidate, address.getPort()))
    }

    fun detectSelfConnect(socketChannel: SocketChannel): Boolean {
        val socket = socketChannel.socket()
        val localAddress = socket.getLocalSocketAddress()
        val remoteAddress = socket.getRemoteSocketAddress()
        if (localAddress == remoteAddress) {
            LOGGER.debug(
                "Detected that socket was bound to {} while connecting to {}. This looks like the socket connected to itself.",
                localAddress, remoteAddress
            )
            return true
        }
        return false
    }

    companion object {
        val CONNECTION_PREAMBLE: ByteArray = "Gradle Magic".toByteArray(StandardCharsets.UTF_8)
        private val LOGGER: Logger = LoggerFactory.getLogger(TcpOutgoingConnector::class.java)
        private const val CONNECT_TIMEOUT = 10000
    }
}
