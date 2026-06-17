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
package org.gradle.tooling.internal.consumer.connection

import org.gradle.internal.concurrent.Stoppable
import org.gradle.tooling.BuildAction
import org.gradle.tooling.internal.consumer.PhasedBuildAction
import org.gradle.tooling.internal.consumer.TestExecutionRequest
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters

/**
 * Implementations must be thread-safe.
 */
interface ConsumerConnection : Stoppable {
    /**
     * Cleans up resources used by this connection. Blocks until complete.
     */
    override fun stop()

    val displayName: String?

    @Throws(UnsupportedOperationException::class, IllegalStateException::class)
    fun <T> run(type: Class<T?>, operationParameters: ConsumerOperationParameters): T?

    @Throws(UnsupportedOperationException::class, IllegalStateException::class)
    fun <T> run(action: BuildAction<T?>, operationParameters: ConsumerOperationParameters): T?

    @Throws(UnsupportedOperationException::class, IllegalStateException::class)
    fun run(phasedBuildAction: PhasedBuildAction, operationParameters: ConsumerOperationParameters)

    fun runTests(testExecutionRequest: TestExecutionRequest, operationParameters: ConsumerOperationParameters)

    fun notifyDaemonsAboutChangedPaths(changedPaths: MutableList<String>, operationParameters: ConsumerOperationParameters)

    fun stopWhenIdle(operationParameters: ConsumerOperationParameters)
}
