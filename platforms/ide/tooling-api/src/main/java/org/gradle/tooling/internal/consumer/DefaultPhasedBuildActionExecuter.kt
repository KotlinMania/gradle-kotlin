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
package org.gradle.tooling.internal.consumer

import kotlin.IllegalStateException
import kotlin.Throwable
import kotlin.Throws
import org.gradle.tooling.BuildActionExecuter
import org.gradle.tooling.Failure
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.ResultHandler
import org.gradle.tooling.StreamedValueListener
import org.gradle.tooling.internal.consumer.async.AsyncConsumerActionExecutor
import org.gradle.tooling.internal.consumer.connection.ConsumerAction
import org.gradle.tooling.internal.consumer.connection.ConsumerConnection
import org.gradle.util.internal.CollectionUtils.toList

class DefaultPhasedBuildActionExecuter internal constructor(phasedBuildAction: PhasedBuildAction, connection: AsyncConsumerActionExecutor, parameters: ConnectionParameters) :
    AbstractLongRunningOperation<DefaultPhasedBuildActionExecuter>(parameters), BuildActionExecuter<Void?> {
    private val phasedBuildAction: PhasedBuildAction
    private val connection: AsyncConsumerActionExecutor

    init {
        operationParamsBuilder.setEntryPoint("PhasedBuildActionExecuter API")
        this.phasedBuildAction = phasedBuildAction
        this.connection = connection
    }

    override val `this`: DefaultPhasedBuildActionExecuter
        get() = this

    override fun setStreamedValueListener(listener: StreamedValueListener?) {
        operationParamsBuilder.setStreamedValueListener(listener!!)
    }

    override fun forTasks(vararg tasks: String?): BuildActionExecuter<Void?>? {
        operationParamsBuilder.setTasks(tasks.filterNotNull().toMutableList())
        return `this`
    }

    override fun forTasks(tasks: Iterable<String?>?): BuildActionExecuter<Void?>? {
        operationParamsBuilder.setTasks(tasks?.filterNotNull()?.toMutableList())
        return `this`
    }

    @Throws(GradleConnectionException::class, IllegalStateException::class)
    override fun run(): Void? {
        val handler = BlockingResultHandler<Void>(Void::class.java as Class<Void?>)
        run(handler)
        handler.result
        return null
    }

    @Throws(IllegalStateException::class)
    override fun run(handler: ResultHandler<in Void?>?) {
        val operationParameters = consumerOperationParameters
        connection.run<Void?>(object : ConsumerAction<Void?> {
            override val parameters = operationParameters

            override fun run(connection: ConsumerConnection): Void? {
                connection.run(phasedBuildAction, parameters)
                return null
            }
        }, ResultHandlerAdapter<Void?>(handler, createExceptionTransformer(object : ConnectionExceptionTransformer.ConnectionFailureMessageProvider {
            override fun getConnectionFailureMessage(throwable: Throwable?): String? {
                return String.format("Could not run phased build action using %s.", connection.displayName)
            }
        })))
    }
}
