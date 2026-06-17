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

import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.ModelBuilder
import org.gradle.tooling.ResultHandler
import org.gradle.tooling.internal.consumer.AbstractLongRunningOperation.Companion.rationalizeInput
import org.gradle.tooling.internal.consumer.async.AsyncConsumerActionExecutor
import org.gradle.tooling.internal.consumer.connection.ConsumerAction
import org.gradle.tooling.internal.consumer.connection.ConsumerConnection
import org.gradle.tooling.model.UnsupportedMethodException
import org.gradle.tooling.model.internal.Exceptions
import java.lang.String
import kotlin.IllegalStateException
import kotlin.Throwable
import kotlin.Throws
import kotlin.UnsupportedOperationException

class DefaultModelBuilder<T>(private val modelType: Class<T?>, private val connection: AsyncConsumerActionExecutor, parameters: ConnectionParameters) :
    AbstractLongRunningOperation<DefaultModelBuilder<T?>?>(parameters), ModelBuilder<T?> {
    init {
        operationParamsBuilder.setEntryPoint("ModelBuilder API")
    }

    val `this`: DefaultModelBuilder<T?>?
        get() = this

    @Throws(GradleConnectionException::class)
    override fun get(): T? {
        val handler = BlockingResultHandler<T?>(modelType)
        get(handler)
        return handler.result
    }

    @Throws(IllegalStateException::class)
    override fun get(handler: ResultHandler<in T?>?) {
        val parameters = consumerOperationParameters
        connection.run<T?>(object : ConsumerAction<T?> {
            override fun run(connection: ConsumerConnection): T? {
                val model = connection.run<T?>(modelType, this.parameters)
                return model
            }
        }, DefaultModelBuilder.ResultHandlerAdapter<T?>(handler))
    }

    override fun forTasks(vararg tasks: String?): DefaultModelBuilder<T?> {
        // only set a non-null task list on the operationParamsBuilder if at least one task has been given to this method,
        // this is needed since any non-null list, even if empty, is treated as 'execute these tasks before building the model'
        // this would cause an error when fetching the BuildEnvironment model
        val rationalizedTasks: MutableList<String?> = rationalizeInput(tasks)
        operationParamsBuilder.setTasks(rationalizedTasks)
        return this
    }

    override fun forTasks(tasks: Iterable<String?>?): ModelBuilder<T?> {
        operationParamsBuilder.setTasks(rationalizeInput(tasks))
        return this
    }

    private inner class ResultHandlerAdapter<R>(handler: ResultHandler<in R?>?) : org.gradle.tooling.internal.consumer.ResultHandlerAdapter<R?>(
        handler,
        this@DefaultModelBuilder.createExceptionTransformer(object : ConnectionExceptionTransformer.ConnectionFailureMessageProvider {
            override fun getConnectionFailureMessage(failure: Throwable?): String? {
                var message = String.format("Could not fetch model of type '%s' using %s.", modelType.getSimpleName(), connection.displayName)
                if (failure !is UnsupportedMethodException && failure is UnsupportedOperationException) {
                    message += "\n" + Exceptions.INCOMPATIBLE_VERSION_HINT
                }
                return message
            }
        })
    )
}
