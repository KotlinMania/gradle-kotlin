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

import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.internal.consumer.async.AsyncConsumerActionExecutor
import org.gradle.tooling.internal.consumer.async.DefaultAsyncConsumerActionExecutor
import org.gradle.tooling.internal.consumer.connection.CancellableConsumerActionExecutor
import org.gradle.tooling.internal.consumer.connection.ConsumerActionExecutor
import org.gradle.tooling.internal.consumer.connection.LazyConsumerActionExecutor
import org.gradle.tooling.internal.consumer.connection.ProgressLoggingConsumerActionExecutor
import org.gradle.tooling.internal.consumer.connection.RethrowingErrorsConsumerActionExecutor
import org.gradle.tooling.internal.consumer.loader.ToolingImplementationLoader

class ConnectionFactory(private val toolingImplementationLoader: ToolingImplementationLoader, private val executorFactory: ExecutorFactory, private val loggingProvider: LoggingProvider) {
    fun create(distribution: Distribution, parameters: ConnectionParameters, listener: ProjectConnectionCloseListener?): ProjectConnection {
        val lazyConnection: ConsumerActionExecutor = LazyConsumerActionExecutor(distribution, toolingImplementationLoader, loggingProvider, parameters)
        val cancellableConnection: ConsumerActionExecutor = CancellableConsumerActionExecutor(lazyConnection)
        val progressLoggingConnection: ConsumerActionExecutor = ProgressLoggingConsumerActionExecutor(cancellableConnection, loggingProvider)
        val rethrowingErrorsConnection: ConsumerActionExecutor = RethrowingErrorsConsumerActionExecutor(progressLoggingConnection)
        val asyncConnection: AsyncConsumerActionExecutor = DefaultAsyncConsumerActionExecutor(rethrowingErrorsConnection, executorFactory)
        return DefaultProjectConnection(asyncConnection, parameters, listener)
    }
}
