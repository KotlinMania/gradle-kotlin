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

import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter
import org.gradle.tooling.internal.consumer.versioning.ModelMapping
import org.gradle.tooling.internal.protocol.ConnectionVersion4
import org.gradle.tooling.internal.protocol.InternalParameterAcceptingConnection

/**
 * An adapter for [InternalParameterAcceptingConnection].
 *
 *
 * Used for providers &gt;= 4.4.
 */
open class ParameterAcceptingConsumerConnection(delegate: ConnectionVersion4, modelMapping: ModelMapping, adapter: ProtocolToModelAdapter) :
    TestExecutionConsumerConnection(delegate, modelMapping, adapter) {
    private val parameterizedActionRunner: ActionRunner

    override val actionRunner: ActionRunner
        get() = parameterizedActionRunner

    init {
        val connection = delegate as InternalParameterAcceptingConnection
        val exceptionTransformer: CancellationExceptionTransformer = CancellationExceptionTransformer.Companion.transformerFor(versionDetails)
        parameterizedActionRunner = ParameterizedActionRunner(connection, exceptionTransformer, versionDetails)
    }
}
