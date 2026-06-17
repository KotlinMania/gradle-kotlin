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

import com.google.common.annotations.VisibleForTesting
import org.gradle.internal.concurrent.DefaultExecutorFactory
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.operations.BuildOperationIdFactory
import org.gradle.internal.operations.DefaultBuildOperationIdFactory
import org.gradle.internal.service.CloseableServiceRegistry
import org.gradle.internal.service.Provides
import org.gradle.internal.service.ServiceRegistrationProvider
import org.gradle.internal.service.ServiceRegistryBuilder
import org.gradle.internal.time.Clock
import org.gradle.internal.time.Time.clock
import org.gradle.tooling.CancellationTokenSource
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.internal.consumer.loader.CachingToolingImplementationLoader
import org.gradle.tooling.internal.consumer.loader.DefaultToolingImplementationLoader
import org.gradle.tooling.internal.consumer.loader.SynchronizedToolingImplementationLoader
import org.gradle.tooling.internal.consumer.loader.ToolingImplementationLoader
import org.jspecify.annotations.NullMarked

/**
 * Internal API that is used for cross-version TAPI client testing.
 */
@NullMarked
object ConnectorServices {
    private var sharedConnectorFactory: GradleConnectorFactory? = null
    private var activeConnectors = 0

    @JvmStatic
    fun createCancellationTokenSource(): CancellationTokenSource {
        return DefaultCancellationTokenSource()
    }

    @JvmStatic
    @Synchronized
    fun createConnector(): GradleConnector {
        if (sharedConnectorFactory == null) {
            sharedConnectorFactory = createConnectorFactory()
        }
        activeConnectors++
        return sharedConnectorFactory!!.createConnector()
    }

    @Synchronized
    fun close() {
        if (sharedConnectorFactory != null) {
            sharedConnectorFactory!!.close()
            sharedConnectorFactory = null
        }
    }

    /**
     * Called when a connector is disconnected. When the last active connector is gone,
     * closes the shared services so non-daemon resources (e.g. the file lock request
     * listener thread) don't keep the JVM alive.
     */
    @Synchronized
    fun connectorDisconnected() {
        if (activeConnectors > 0) {
            activeConnectors--
        }
        if (activeConnectors == 0) {
            close()
        }
    }

    /**
     * Resets the state of connector services.
     *
     *
     * Used for cross-version testing of the lifecycle of the connector services.
     */
    @JvmStatic
    @VisibleForTesting
    @Synchronized
    fun reset() {
        close()
        activeConnectors = 0
    }

    /**
     * Used for cross-version testing of the lifecycle of the connector services.
     */
    @VisibleForTesting
    fun createConnectorFactory(): GradleConnectorFactory {
        return DefaultGradleConnectorFactory()
    }

    private class DefaultGradleConnectorFactory : GradleConnectorFactory {
        private val ownerRegistry: CloseableServiceRegistry = ConnectorServiceRegistry.Companion.create()

        override fun createConnector(): GradleConnector {
            return ownerRegistry.get<GradleConnectorFactory?>(GradleConnectorFactory::class.java)!!.createConnector()
        }

        override fun close() {
            ownerRegistry.close()
        }
    }

    /**
     * Exists for the purpose of creating [GradleConnectorFactory].
     *
     *
     * The service registry is used to simplify setting up and tearing down the dependencies.
     */
    private class ConnectorServiceRegistry : ServiceRegistrationProvider {
        @Provides
        protected fun createConnectorFactory(connectionFactory: ConnectionFactory, distributionFactory: DistributionFactory): GradleConnectorFactory {
            return object : GradleConnectorFactory {
                override fun createConnector(): GradleConnector {
                    return DefaultGradleConnector(connectionFactory, distributionFactory)
                }

                override fun close() {}
            }
        }

        @Provides
        protected fun createExecutorFactory(): ExecutorFactory {
            return DefaultExecutorFactory()
        }

        @Provides
        protected fun createExecutorServiceFactory(): ExecutorServiceFactory {
            return DefaultExecutorServiceFactory()
        }

        @Provides
        protected fun createTimeProvider(): Clock {
            return clock()
        }

        @Provides
        protected fun createDistributionFactory(clock: Clock): DistributionFactory {
            return DistributionFactory(clock)
        }

        @Provides
        protected fun createToolingImplementationLoader(): ToolingImplementationLoader {
            return SynchronizedToolingImplementationLoader(CachingToolingImplementationLoader(DefaultToolingImplementationLoader()))
        }

        @Provides
        protected fun createBuildOperationIdFactory(): BuildOperationIdFactory {
            return DefaultBuildOperationIdFactory()
        }

        @Provides
        protected fun createLoggingProvider(clock: Clock, buildOperationIdFactory: BuildOperationIdFactory): LoggingProvider {
            return SynchronizedLogging(clock, buildOperationIdFactory)
        }

        @Provides
        protected fun createConnectionFactory(toolingImplementationLoader: ToolingImplementationLoader, executorFactory: ExecutorFactory, loggingProvider: LoggingProvider): ConnectionFactory {
            return ConnectionFactory(toolingImplementationLoader, executorFactory, loggingProvider)
        }

        companion object {
            private fun create(): CloseableServiceRegistry {
                return ServiceRegistryBuilder.builder()
                    .displayName("connector services")
                    .provider(ConnectorServiceRegistry())
                    .build()
            }
        }
    }
}
