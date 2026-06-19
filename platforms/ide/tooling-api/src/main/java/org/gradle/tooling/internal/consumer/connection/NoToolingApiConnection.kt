/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.tooling.BuildAction
import org.gradle.tooling.internal.consumer.Distribution
import org.gradle.tooling.internal.consumer.PhasedBuildAction
import org.gradle.tooling.internal.consumer.TestExecutionRequest
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters
import org.gradle.tooling.model.internal.Exceptions

/**
 * A `ConsumerConnection` implementation for a Gradle version that does not support the tooling API.
 *
 *
 * Used for versions &lt; 1.0-milestone-3.
 */
class NoToolingApiConnection(private val distribution: Distribution) : ConsumerConnection {
    override fun stop() {
    }

    override val displayName: String?
        get() = distribution.displayName

    @Throws(UnsupportedOperationException::class, IllegalStateException::class)
    override fun <T> run(type: Class<T?>, operationParameters: ConsumerOperationParameters): T? {
        throw Exceptions.unsupportedFeature(operationParameters.entryPointName, distribution, "1.2")
    }

    @Throws(UnsupportedOperationException::class, IllegalStateException::class)
    override fun <T> run(action: BuildAction<T?>, operationParameters: ConsumerOperationParameters): T? {
        throw Exceptions.unsupportedFeature(operationParameters.entryPointName, distribution, "1.8")
    }

    override fun run(phasedBuildAction: PhasedBuildAction, operationParameters: ConsumerOperationParameters) {
        throw Exceptions.unsupportedFeature(operationParameters.entryPointName, distribution, "4.8")
    }

    override fun runTests(testExecutionRequest: TestExecutionRequest, operationParameters: ConsumerOperationParameters) {
        throw Exceptions.unsupportedFeature(operationParameters.entryPointName, distribution, "2.6")
    }

    override fun notifyDaemonsAboutChangedPaths(changedPaths: MutableList<String>, operationParameters: ConsumerOperationParameters) {
        throw Exceptions.unsupportedFeature(operationParameters.entryPointName, distribution, "6.1")
    }

    override fun stopWhenIdle(operationParameters: ConsumerOperationParameters) {
        throw Exceptions.unsupportedFeature(operationParameters.entryPointName, distribution, "6.5")
    }
}
