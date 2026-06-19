/*
 * Copyright 2017 the original author or authors.
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
import org.gradle.tooling.internal.consumer.PhasedBuildAction
import org.gradle.tooling.internal.consumer.TestExecutionRequest
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters
import org.gradle.tooling.internal.consumer.versioning.VersionDetails
import org.gradle.tooling.model.internal.Exceptions

class ParameterValidatingConsumerConnection(private val targetVersionDetails: VersionDetails, private val delegate: ConsumerConnection) : ConsumerConnection {
    override fun stop() {
        delegate.stop()
    }

    override val displayName: String?
        get() = delegate.displayName

    @Throws(UnsupportedOperationException::class, IllegalStateException::class)
    override fun <T> run(type: Class<T?>, operationParameters: ConsumerOperationParameters): T? {
        validateParameters(operationParameters)
        return delegate.run<T?>(type, operationParameters)
    }

    @Throws(UnsupportedOperationException::class, IllegalStateException::class)
    override fun <T> run(action: BuildAction<T?>, operationParameters: ConsumerOperationParameters): T? {
        validateParameters(operationParameters)
        validateBuildActionParameters(operationParameters)
        return delegate.run<T?>(action, operationParameters)
    }

    override fun run(phasedBuildAction: PhasedBuildAction, operationParameters: ConsumerOperationParameters) {
        validateParameters(operationParameters)
        delegate.run(phasedBuildAction, operationParameters)
    }

    override fun runTests(testExecutionRequest: TestExecutionRequest, operationParameters: ConsumerOperationParameters) {
        validateParameters(operationParameters)
        delegate.runTests(testExecutionRequest, operationParameters)
    }

    override fun notifyDaemonsAboutChangedPaths(changedPaths: MutableList<String>, operationParameters: ConsumerOperationParameters) {
        delegate.notifyDaemonsAboutChangedPaths(changedPaths, operationParameters)
    }

    override fun stopWhenIdle(operationParameters: ConsumerOperationParameters) {
        delegate.stopWhenIdle(operationParameters)
    }

    private fun validateParameters(operationParameters: ConsumerOperationParameters) {
        if (!targetVersionDetails.supportsEnvironmentVariablesCustomization()) {
            if (operationParameters.environmentVariables != null) {
                throw Exceptions.unsupportedFeature("environment variables customization feature", targetVersionDetails.version!!, "3.5")
            }
        }
    }

    private fun validateBuildActionParameters(operationParameters: ConsumerOperationParameters) {
        if (!targetVersionDetails.supportsRunTasksBeforeExecutingAction()) {
            if (operationParameters.tasks != null) {
                throw Exceptions.unsupportedFeature("forTasks() method on BuildActionExecuter", targetVersionDetails.version!!, "3.5")
            }
        }
    }
}
