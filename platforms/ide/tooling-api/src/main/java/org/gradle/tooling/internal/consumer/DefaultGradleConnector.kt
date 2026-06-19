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
package org.gradle.tooling.internal.consumer

import org.gradle.internal.service.ServiceRegistryBuilder.Companion.builder
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters.Companion.builder
import org.gradle.util.GradleVersion
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit

class DefaultGradleConnector(private val connectionFactory: ConnectionFactory, private val distributionFactory: DistributionFactory) : GradleConnector(), ProjectConnectionCloseListener {
    private var distribution: Distribution? = null

    private val connections: MutableList<DefaultProjectConnection> = ArrayList<DefaultProjectConnection>(4)
    private var stopped = false

    private val connectionParamsBuilder: DefaultConnectionParameters.Builder = DefaultConnectionParameters.Companion.builder()

    override fun connectionClosed(connection: ProjectConnection?) {
        synchronized(connections) {
            connections.remove(connection)
        }
    }

    override fun disconnect() {
        synchronized(connections) {
            stopped = true
            for (connection in connections) {
                connection.disconnect()
            }
            connections.clear()
        }
        ConnectorServices.connectorDisconnected()
    }

    override fun useInstallation(gradleHome: File?): GradleConnector {
        distribution = distributionFactory.getDistribution(gradleHome!!)
        return this
    }

    override fun useGradleVersion(gradleVersion: String?): GradleConnector {
        distribution = distributionFactory.getDistribution(gradleVersion!!)
        return this
    }

    override fun useDistribution(gradleDistribution: URI?): GradleConnector {
        distribution = distributionFactory.getDistribution(gradleDistribution!!)
        return this
    }

    override fun useBuildDistribution(): GradleConnector {
        distribution = null
        return this
    }

    fun useDistributionBaseDir(distributionBaseDir: File?): GradleConnector {
        connectionParamsBuilder.setDistributionBaseDir(distributionBaseDir)
        return this
    }

    override fun forProjectDirectory(projectDir: File?): GradleConnector {
        connectionParamsBuilder.setProjectDir(projectDir)
        return this
    }

    override fun useGradleUserHomeDir(gradleUserHomeDir: File?): GradleConnector {
        connectionParamsBuilder.setGradleUserHomeDir(gradleUserHomeDir)
        return this
    }

    fun searchUpwards(searchUpwards: Boolean): GradleConnector {
        connectionParamsBuilder.setSearchUpwards(searchUpwards)
        return this
    }

    fun embedded(embedded: Boolean): GradleConnector {
        connectionParamsBuilder.setEmbedded(embedded)
        return this
    }

    fun daemonMaxIdleTime(timeoutValue: Int, timeoutUnits: TimeUnit?): GradleConnector {
        connectionParamsBuilder.setDaemonMaxIdleTimeValue(timeoutValue)
        connectionParamsBuilder.setDaemonMaxIdleTimeUnits(timeoutUnits)
        return this
    }

    fun daemonBaseDir(daemonBaseDir: File?): GradleConnector {
        connectionParamsBuilder.setDaemonBaseDir(daemonBaseDir)
        return this
    }

    /**
     * If true then debug log statements will be shown
     */
    fun setVerboseLogging(verboseLogging: Boolean): DefaultGradleConnector {
        connectionParamsBuilder.setVerboseLogging(verboseLogging)
        return this
    }

    @Throws(GradleConnectionException::class)
    override fun connect(): ProjectConnection {
        LOGGER.debug("Connecting from tooling API consumer version {}", GradleVersion.current().getVersion())

        val connectionParameters: ConnectionParameters = connectionParamsBuilder.build()
        checkNotNull(connectionParameters.projectDir) { "A project directory must be specified before creating a connection." }
        if (distribution == null) {
            distribution =
                distributionFactory.getDefaultDistribution(connectionParameters.projectDir!!, connectionParameters.isSearchUpwards ?: true)
        }

        synchronized(connections) {
            check(!stopped) { "Tooling API client has been disconnected. No other connections may be used." }
            val connection = connectionFactory.create(distribution!!, connectionParameters, this)
            connections.add((connection as org.gradle.tooling.internal.consumer.DefaultProjectConnection?)!!)
            return connection
        }
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(GradleConnector::class.java)

        const val MINIMAL_CLIENT_MAJOR_VERSION: Int = 4
        val MINIMUM_SUPPORTED_GRADLE_VERSION: GradleVersion = GradleVersion.version(MINIMAL_CLIENT_MAJOR_VERSION.toString() + ".0")

        /**
         * Closes the tooling API, releasing all resources. Blocks until completed.
         *
         *
         * May attempt to expire some or all daemons started by this tooling API client. The exact behaviour here is implementation-specific and not guaranteed.
         * The expiration is best effort only. This method may return before the daemons have stopped.
         *
         *
         * Note: this is not yet part of the public tooling API yet.
         *
         * TODO - need to model this as a long running operation, and allow stdout, stderr and progress listener to be supplied.
         * TODO - need to define exceptions.
         * TODO - no further operations are allowed after this has been called
         * TODO - cancel current operations or block until complete
         * TODO - introduce a 'tooling API client' interface and move this method there
         */
        fun close() {
            ConnectorServices.close()
        }
    }
}
