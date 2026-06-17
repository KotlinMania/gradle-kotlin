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

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException

/**
 * Provides some information about the network addresses of the local machine.
 */
internal class InetAddresses {
    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    val loopback: MutableList<InetAddress> = ArrayList<InetAddress>()
    val remote: MutableList<InetAddress> = ArrayList<InetAddress>()

    init {
        analyzeNetworkInterfaces()
    }

    @Throws(SocketException::class)
    private fun analyzeNetworkInterfaces() {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        if (interfaces != null) {
            while (interfaces.hasMoreElements()) {
                analyzeNetworkInterface(interfaces.nextElement())
            }
        }
    }

    private fun analyzeNetworkInterface(networkInterface: NetworkInterface) {
        logger.debug("Adding IP addresses for network interface {}", networkInterface.getDisplayName())
        try {
            val isLoopbackInterface = networkInterface.isLoopback()
            logger.debug("Is this a loopback interface? {}", isLoopbackInterface)

            val candidates = networkInterface.getInetAddresses()
            while (candidates.hasMoreElements()) {
                val candidate = candidates.nextElement()
                if (isLoopbackInterface) {
                    if (candidate.isLoopbackAddress()) {
                        logger.debug("Adding loopback address {}", candidate)
                        loopback.add(candidate)
                    } else {
                        logger.debug("Ignoring remote address on loopback interface {}", candidate)
                    }
                } else {
                    if (candidate.isLoopbackAddress()) {
                        logger.debug("Ignoring loopback address on remote interface {}", candidate)
                    } else {
                        logger.debug("Adding remote address {}", candidate)
                        remote.add(candidate)
                    }
                }
            }
        } catch (e: SocketException) {
            // Log the error but analyze the remaining interfaces. We could for example run into https://bugs.openjdk.java.net/browse/JDK-7032558
            logger.debug("Error while querying interface {} for IP addresses", networkInterface, e)
        } catch (e: Throwable) {
            throw RuntimeException(String.format("Could not determine the IP addresses for network interface %s", networkInterface.getName()), e)
        }
    }
}
