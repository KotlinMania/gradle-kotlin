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

import com.google.common.annotations.VisibleForTesting
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketException
import java.net.UnknownHostException

/**
 * Provides information on how two processes on this machine can communicate via IP addresses
 */
@ServiceScope(Scope.Global::class)
open class InetAddressFactory {
    private val logger: Logger = LoggerFactory.getLogger(javaClass)
    private val lock = Any()
    private var localBindingAddress: InetAddress? = null
    private var wildcardBindingAddress: InetAddress? = null
    private var inetAddresses: InetAddresses? = null
    private var initialized = false

    constructor()

    @VisibleForTesting
    constructor(inetAddresses: InetAddresses) {
        this.inetAddresses = inetAddresses
    }

    /**
     * Determines if the IP address can be used for communication with this machine
     */
    fun isCommunicationAddress(address: InetAddress): Boolean {
        return getLocalBindingAddress() == address
    }

    /**
     * Local communication address for this machine
     */
    fun getLocalBindingAddress(): InetAddress {
        try {
            synchronized(lock) {
                init()
                return localBindingAddress!!
            }
        } catch (e: Exception) {
            throw RuntimeException("Could not determine a usable local IP for this machine.", e)
        }
    }

    /**
     * Wildcard address for this machine
     */
    fun getWildcardBindingAddress(): InetAddress {
        try {
            synchronized(lock) {
                init()
                return wildcardBindingAddress!!
            }
        } catch (e: Exception) {
            throw RuntimeException("Could not determine a usable wildcard IP for this machine.", e)
        }
    }

    @Throws(Exception::class)
    private fun init() {
        if (initialized) {
            return
        }

        initialized = true
        wildcardBindingAddress = InetSocketAddress(0).getAddress()

        if (!findGradleDaemonBindAddress() && !findOpenshiftAddress()) {
            findLocalBindingAddress()
        }
    }

    /**
     * Prefer first loopback address if available, otherwise use the wildcard address.
     */
    @Throws(SocketException::class)
    private fun findLocalBindingAddress() {
        if (inetAddresses == null) { // For testing
            inetAddresses = InetAddresses()
        }
        if (inetAddresses!!.getLoopback().isEmpty()) {
            logger.debug("No loopback address for local binding, using fallback {}", wildcardBindingAddress)
            localBindingAddress = wildcardBindingAddress
        } else {
            localBindingAddress = InetAddress.getLoopbackAddress()
        }
    }

    private fun findGradleDaemonBindAddress(): Boolean {
        val address = resolveEnvBindAddress("GRADLE_DAEMON_BIND_ADDRESS")
        if (address != null) {
            localBindingAddress = address
            return true
        }
        return false
    }

    private fun findOpenshiftAddress(): Boolean {
        for (key in this.envKeys) {
            if (key.startsWith("OPENSHIFT_") && key.endsWith("_IP")) {
                val address = resolveEnvBindAddress(key)
                if (address != null) {
                    localBindingAddress = address
                    return true
                }
            }
        }
        return false
    }

    private fun resolveEnvBindAddress(envVarName: String): InetAddress? {
        val address = getEnv(envVarName)
        if (address == null) {
            return null
        }
        try {
            logger.debug("Environment variable {} detected. Using bind address {}.", envVarName, address)
            return InetAddress.getByName(address)
        } catch (e: UnknownHostException) {
            throw RuntimeException(String.format("Invalid bind address '%s' specified in environment variable '%s'.", address, envVarName), e)
        }
    }

    @VisibleForTesting
    open fun getEnv(name: String): String? {
        return System.getenv(name)
    }

    @get:VisibleForTesting
    open val envKeys: MutableSet<String>
        get() = System.getenv().keys
}
