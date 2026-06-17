/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter
import org.gradle.tooling.internal.consumer.versioning.ModelMapping
import org.gradle.tooling.internal.consumer.versioning.VersionDetails
import org.gradle.tooling.internal.protocol.ConnectionVersion4
import org.gradle.tooling.internal.protocol.InternalCancellableConnection

/**
 * An adapter for [InternalCancellableConnection].
 *
 *
 * Used for providers &gt;= 2.1.
 */
open class CancellableConsumerConnection(delegate: ConnectionVersion4, modelMapping: ModelMapping, adapter: ProtocolToModelAdapter) :
    AbstractPost12ConsumerConnection(delegate, VersionDetails.from(delegate.metaData.version)) {
    private val actionRunner: ActionRunner
    private val modelProducer: ModelProducer

    init {
        val exceptionTransformer: CancellationExceptionTransformer = CancellationExceptionTransformer.Companion.transformerFor(getVersionDetails())
        val connection = delegate as InternalCancellableConnection
        modelProducer = createModelProducer(connection, modelMapping, adapter, exceptionTransformer)
        actionRunner = CancellableActionRunner(connection, exceptionTransformer, getVersionDetails())
    }

    private fun createModelProducer(
        connection: InternalCancellableConnection,
        modelMapping: ModelMapping,
        adapter: ProtocolToModelAdapter,
        exceptionTransformer: CancellationExceptionTransformer
    ): ModelProducer {
        return PluginClasspathInjectionSupportedCheckModelProducer(
            CancellableModelBuilderBackedModelProducer(adapter, getVersionDetails(), modelMapping, connection, exceptionTransformer),
            getVersionDetails()
        )
    }

    override fun getActionRunner(): ActionRunner {
        return actionRunner
    }

    override fun getModelProducer(): ModelProducer {
        return modelProducer
    }
}
