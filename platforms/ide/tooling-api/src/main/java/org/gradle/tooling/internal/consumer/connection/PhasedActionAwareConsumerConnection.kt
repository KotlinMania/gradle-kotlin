/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.tooling.BuildActionFailureException
import org.gradle.tooling.IntermediateResultHandler
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter
import org.gradle.tooling.internal.consumer.DefaultPhasedActionResultListener
import org.gradle.tooling.internal.consumer.PhasedBuildAction
import org.gradle.tooling.internal.consumer.parameters.BuildCancellationTokenAdapter
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters
import org.gradle.tooling.internal.consumer.versioning.ModelMapping
import org.gradle.tooling.internal.consumer.versioning.VersionDetails
import org.gradle.tooling.internal.protocol.ConnectionVersion4
import org.gradle.tooling.internal.protocol.InternalBuildActionFailureException
import org.gradle.tooling.internal.protocol.InternalBuildActionVersion2
import org.gradle.tooling.internal.protocol.InternalPhasedAction
import org.gradle.tooling.internal.protocol.InternalPhasedActionConnection
import org.gradle.tooling.internal.protocol.PhasedActionResultListener
import java.io.File

/**
 * An adapter for [InternalPhasedActionConnection].
 *
 *
 * Used for providers &gt;= 4.8.
 */
open class PhasedActionAwareConsumerConnection(delegate: ConnectionVersion4, modelMapping: ModelMapping, adapter: ProtocolToModelAdapter) :
    ParameterAcceptingConsumerConnection(delegate, modelMapping, adapter) {
    override fun doRun(phasedBuildAction: PhasedBuildAction, operationParameters: ConsumerOperationParameters) {
        val connection = getDelegate() as InternalPhasedActionConnection
        val listener: PhasedActionResultListener = DefaultPhasedActionResultListener(
            Companion.getHandler(phasedBuildAction.projectsLoadedAction),
            Companion.getHandler(phasedBuildAction.buildFinishedAction)
        )
        val internalPhasedAction: InternalPhasedAction = getPhasedAction(phasedBuildAction, operationParameters.getProjectDir(), getVersionDetails())
        try {
            connection.run(internalPhasedAction, listener, BuildCancellationTokenAdapter(operationParameters.getCancellationToken()), operationParameters)
        } catch (e: InternalBuildActionFailureException) {
            throw BuildActionFailureException("The supplied phased action failed with an exception.", e.cause)
        }
    }

    companion object {
        private fun <T> getHandler(wrapper: PhasedBuildAction.BuildActionWrapper<T?>?): IntermediateResultHandler<in T?>? {
            return if (wrapper == null) null else wrapper.handler
        }

        private fun getPhasedAction(phasedBuildAction: PhasedBuildAction, rootDir: File, versionDetails: VersionDetails): InternalPhasedAction {
            return InternalPhasedActionAdapter(
                Companion.getAction(phasedBuildAction.projectsLoadedAction, rootDir, versionDetails),
                Companion.getAction(phasedBuildAction.buildFinishedAction, rootDir, versionDetails)
            )
        }

        private fun <T> getAction(wrapper: PhasedBuildAction.BuildActionWrapper<T?>?, rootDir: File, versionDetails: VersionDetails): InternalBuildActionVersion2<T?>? {
            return if (wrapper == null) null else InternalBuildActionAdapter<T?>(wrapper.action, rootDir, versionDetails)
        }
    }
}
