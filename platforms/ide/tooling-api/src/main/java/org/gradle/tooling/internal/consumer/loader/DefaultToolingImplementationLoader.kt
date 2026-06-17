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
package org.gradle.tooling.internal.consumer.loader

import org.gradle.api.JavaVersion
import org.gradle.initialization.BuildCancellationToken
import org.gradle.internal.Factory
import org.gradle.internal.classloader.FilteringClassLoader
import org.gradle.internal.classloader.VisitableURLClassLoader
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.internal.service.DefaultServiceLocator
import org.gradle.internal.service.ServiceLocator
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.UnsupportedVersionException
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter
import org.gradle.tooling.internal.consumer.ConnectionParameters
import org.gradle.tooling.internal.consumer.Distribution
import org.gradle.tooling.internal.consumer.connection.AbstractConsumerConnection
import org.gradle.tooling.internal.consumer.connection.ConsumerConnection
import org.gradle.tooling.internal.consumer.connection.NoToolingApiConnection
import org.gradle.tooling.internal.consumer.connection.NotifyDaemonsAboutChangedPathsConsumerConnection
import org.gradle.tooling.internal.consumer.connection.ParameterAcceptingConsumerConnection
import org.gradle.tooling.internal.consumer.connection.ParameterValidatingConsumerConnection
import org.gradle.tooling.internal.consumer.connection.PhasedActionAwareConsumerConnection
import org.gradle.tooling.internal.consumer.connection.StopWhenIdleConsumerConnection
import org.gradle.tooling.internal.consumer.connection.TestExecutionConsumerConnection
import org.gradle.tooling.internal.consumer.connection.UnsupportedOlderVersionConnection
import org.gradle.tooling.internal.consumer.converters.ConsumerTargetTypeProvider
import org.gradle.tooling.internal.consumer.versioning.ModelMapping
import org.gradle.tooling.internal.protocol.ConnectionVersion4
import org.gradle.tooling.internal.protocol.InternalBuildProgressListener
import org.gradle.tooling.internal.protocol.InternalInvalidatableVirtualFileSystemConnection
import org.gradle.tooling.internal.protocol.InternalParameterAcceptingConnection
import org.gradle.tooling.internal.protocol.InternalPhasedActionConnection
import org.gradle.tooling.internal.protocol.InternalStopWhenIdleConnection
import org.gradle.tooling.internal.protocol.test.InternalTestExecutionConnection
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Loads the tooling API implementation of the Gradle version that will run the build (the "provider").
 * Adapts the rather clunky cross-version interface to the more readable interface of the TAPI client.
 */
class DefaultToolingImplementationLoader internal constructor(private val classLoader: ClassLoader) : ToolingImplementationLoader {
    constructor() : this(DefaultToolingImplementationLoader::class.java.getClassLoader())

    override fun create(
        distribution: Distribution,
        progressLoggerFactory: ProgressLoggerFactory?,
        progressListener: InternalBuildProgressListener?,
        connectionParameters: ConnectionParameters,
        cancellationToken: BuildCancellationToken?
    ): ConsumerConnection {
        LOGGER.debug("Using tooling provider from {}", distribution.displayName)
        val serviceClassLoader = createImplementationClassLoader(distribution, progressLoggerFactory, progressListener, connectionParameters, cancellationToken)
        val serviceLocator: ServiceLocator = DefaultServiceLocator(serviceClassLoader)
        try {
            val factory: Factory<ConnectionVersion4?>? = serviceLocator.findFactory<ConnectionVersion4?>(ConnectionVersion4::class.java)
            if (factory == null) {
                return NoToolingApiConnection(distribution)
            }
            val connection: ConnectionVersion4 = factory.create()!!

            val adapter = ProtocolToModelAdapter(ConsumerTargetTypeProvider())
            val modelMapping = ModelMapping()
            if (connection is InternalStopWhenIdleConnection) {
                return createConnection(StopWhenIdleConsumerConnection(connection, modelMapping, adapter), connectionParameters)
            } else if (connection is InternalInvalidatableVirtualFileSystemConnection) {
                return createConnection(NotifyDaemonsAboutChangedPathsConsumerConnection(connection, modelMapping, adapter), connectionParameters)
            } else if (connection is InternalPhasedActionConnection) {
                return createConnection(PhasedActionAwareConsumerConnection(connection, modelMapping, adapter), connectionParameters)
            } else if (connection is InternalParameterAcceptingConnection) {
                return createConnection(ParameterAcceptingConsumerConnection(connection, modelMapping, adapter), connectionParameters)
            } else if (connection is InternalTestExecutionConnection) {
                return createConnection(TestExecutionConsumerConnection(connection, modelMapping, adapter), connectionParameters)
            } else {
                return UnsupportedOlderVersionConnection(connection, adapter)
            }
        } catch (e: UnsupportedVersionException) {
            throw e
        } catch (t: Throwable) {
            throw GradleConnectionException(String.format("Could not create an instance of Tooling API implementation using the specified %s.", distribution.displayName), t)
        }
    }

    private fun createConnection(adaptedConnection: AbstractConsumerConnection, connectionParameters: ConnectionParameters): ConsumerConnection {
        adaptedConnection.configure(connectionParameters)
        val versionDetails = adaptedConnection.versionDetails
        return ParameterValidatingConsumerConnection(versionDetails, adaptedConnection)
    }

    private fun createImplementationClassLoader(
        distribution: Distribution,
        progressLoggerFactory: ProgressLoggerFactory?,
        progressListener: InternalBuildProgressListener?,
        connectionParameters: ConnectionParameters?,
        cancellationToken: BuildCancellationToken?
    ): ClassLoader {
        val implementationClasspath = distribution.getToolingImplementationClasspath(progressLoggerFactory, progressListener, connectionParameters, cancellationToken)
        LOGGER.debug("Using tooling provider classpath: {}", implementationClasspath)
        val filterSpec = FilteringClassLoader.Spec()
        filterSpec.allowPackage("org.gradle.tooling.internal.protocol")
        filterSpec.allowClass(JavaVersion::class.java)
        val filteringClassLoader = FilteringClassLoader(classLoader, filterSpec)
        return VisitableURLClassLoader.fromClassPath("tooling-implementation-loader", filteringClassLoader, implementationClasspath)
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(DefaultToolingImplementationLoader::class.java)
    }
}
