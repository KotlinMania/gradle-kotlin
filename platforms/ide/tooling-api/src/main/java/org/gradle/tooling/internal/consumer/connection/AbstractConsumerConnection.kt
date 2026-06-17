/*
 * Copyright 2012 the original author or authors.
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
import org.gradle.tooling.internal.consumer.ConnectionParameters
import org.gradle.tooling.internal.consumer.PhasedBuildAction
import org.gradle.tooling.internal.consumer.TestExecutionRequest
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters
import org.gradle.tooling.internal.consumer.versioning.VersionDetails
import org.gradle.tooling.internal.protocol.ConnectionVersion4
import org.gradle.tooling.model.internal.Exceptions

abstract class AbstractConsumerConnection(val delegate: ConnectionVersion4, val versionDetails: VersionDetails) : HasCompatibilityMapping(
    versionDetails
), ConsumerConnection {
    override fun stop() {
    }

    override fun getDisplayName(): String {
        return delegate.metaData.displayName
    }

    abstract fun configure(connectionParameters: ConnectionParameters)

    protected abstract val modelProducer: ModelProducer?

    protected abstract val actionRunner: ActionRunner?

    override fun <T> run(type: Class<T?>, operationParameters: ConsumerOperationParameters): T? {
        return this.modelProducer!!.produceModel<T?>(type, operationParameters)
    }

    override fun <T> run(action: BuildAction<T?>, operationParameters: ConsumerOperationParameters): T? {
        return this.actionRunner!!.run<T?>(action, operationParameters)
    }

    override fun run(phasedBuildAction: PhasedBuildAction, operationParameters: ConsumerOperationParameters) {
        throw Exceptions.unsupportedFeature(operationParameters.getEntryPointName(), this.versionDetails.getVersion(), "4.8")
    }

    override fun runTests(testExecutionRequest: TestExecutionRequest, operationParameters: ConsumerOperationParameters) {
        throw Exceptions.unsupportedFeature(operationParameters.getEntryPointName(), this.versionDetails.getVersion(), "2.6")
    }

    override fun notifyDaemonsAboutChangedPaths(changedPaths: MutableList<String>, operationParameters: ConsumerOperationParameters) {
        // Default is no-op
    }

    override fun stopWhenIdle(operationParameters: ConsumerOperationParameters) {
        // Default is no-op
    }
}
