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

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import org.gradle.initialization.BuildCancellationToken
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.concurrent.CompositeStoppable
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.internal.consumer.ConnectionParameters
import org.gradle.tooling.internal.consumer.Distribution
import org.gradle.tooling.internal.consumer.connection.ConsumerConnection
import org.gradle.tooling.internal.protocol.InternalBuildProgressListener
import org.jspecify.annotations.NullMarked
import java.io.Closeable
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException

class CachingToolingImplementationLoader(private val loader: ToolingImplementationLoader) : ToolingImplementationLoader, Closeable {
    private val connections: Cache<ClassPath, ConsumerConnection> = CacheBuilder.newBuilder()
        .maximumSize(15)
        .build<ClassPath, ConsumerConnection>()

    override fun create(
        distribution: Distribution?,
        progressLoggerFactory: ProgressLoggerFactory?,
        progressListener: InternalBuildProgressListener?,
        connectionParameters: ConnectionParameters?,
        cancellationToken: BuildCancellationToken?
    ): ConsumerConnection? {
        val classpath = distribution!!.getToolingImplementationClasspath(progressLoggerFactory, progressListener, connectionParameters, cancellationToken)!!

        try {
            return connections.get(
                classpath,
                ConsumerConnectionCreator(distribution, progressLoggerFactory!!, progressListener!!, connectionParameters!!, cancellationToken!!)
            )
        } catch (e: ExecutionException) {
            throw GradleConnectionException(String.format("Could not create an instance of Tooling API implementation using the specified %s.", distribution.displayName), e)
        }
    }

    override fun close() {
        try {
            CompositeStoppable.stoppable(connections.asMap().values).stop()
        } finally {
            connections.invalidateAll()
        }
    }

    @NullMarked
    private inner class ConsumerConnectionCreator(
        private val distribution: Distribution,
        private val progressLoggerFactory: ProgressLoggerFactory,
        private val progressListener: InternalBuildProgressListener,
        private val connectionParameters: ConnectionParameters,
        private val cancellationToken: BuildCancellationToken
    ) : Callable<ConsumerConnection> {
        @Throws(Exception::class)
        override fun call(): ConsumerConnection {
            return loader.create(distribution, progressLoggerFactory, progressListener, connectionParameters, cancellationToken)!!
        }
    }
}
