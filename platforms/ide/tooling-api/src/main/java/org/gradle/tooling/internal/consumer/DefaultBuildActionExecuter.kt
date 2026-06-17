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
package org.gradle.tooling.internal.consumer

import org.gradle.internal.Cast.uncheckedNonnullCast
import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildActionExecuter
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.IntermediateResultHandler
import org.gradle.tooling.ResultHandler
import org.gradle.tooling.StreamedValueListener
import org.gradle.tooling.internal.consumer.async.AsyncConsumerActionExecutor
import org.gradle.tooling.internal.consumer.connection.ConsumerAction
import org.gradle.tooling.internal.consumer.connection.ConsumerConnection
import org.gradle.util.internal.CollectionUtils.toList
import java.lang.String
import kotlin.Any
import kotlin.IllegalArgumentException
import kotlin.IllegalStateException
import kotlin.Throwable
import kotlin.Throws
import kotlin.text.format

internal class DefaultBuildActionExecuter<T>(buildAction: BuildAction<T?>, connection: AsyncConsumerActionExecutor, parameters: ConnectionParameters?) :
    AbstractLongRunningOperation<DefaultBuildActionExecuter<T?>?>(parameters), BuildActionExecuter<T?> {
    private val buildAction: BuildAction<T?>
    private val connection: AsyncConsumerActionExecutor

    init {
        operationParamsBuilder.setEntryPoint("BuildActionExecuter API")
        this.buildAction = buildAction
        this.connection = connection
    }

    override fun getThis(): DefaultBuildActionExecuter<T?> {
        return this
    }

    override fun setStreamedValueListener(listener: StreamedValueListener) {
        operationParamsBuilder.setStreamedValueListener(listener)
    }

    override fun forTasks(vararg tasks: String?): BuildActionExecuter<T?> {
        operationParamsBuilder.setTasks((if (tasks != null) java.util.Arrays.asList<kotlin.String>(*tasks) else null)!!)
        return getThis()
    }

    override fun forTasks(tasks: Iterable<String?>?): BuildActionExecuter<T?> {
        operationParamsBuilder.setTasks(if (tasks != null) toList<String>(tasks) else null)
        return getThis()
    }

    @Throws(GradleConnectionException::class)
    override fun run(): T? {
        val handler = BlockingResultHandler<Any?>(Any::class.java)
        run(handler)
        return uncheckedNonnullCast<T?>(handler.getResult())
    }

    @Throws(IllegalStateException::class)
    override fun run(handler: ResultHandler<in T?>?) {
        val parameters = getConsumerOperationParameters()
        connection.run<T?>(object : ConsumerAction<T?> {
            override fun run(connection: ConsumerConnection): T? {
                val result = connection.run<T?>(buildAction, this.parameters)
                return result
            }
        }, ResultHandlerAdapter<T?>(handler, createExceptionTransformer(object : ConnectionExceptionTransformer.ConnectionFailureMessageProvider {
            override fun getConnectionFailureMessage(throwable: Throwable?): String? {
                return String.format("Could not run build action using %s.", connection.displayName)
            }
        })))
    }

    internal class Builder(private val connection: AsyncConsumerActionExecutor?, private val parameters: ConnectionParameters?) : BuildActionExecuter.Builder {
        private var projectsLoadedAction: PhasedBuildAction.BuildActionWrapper<*>? = null
        private var buildFinishedAction: PhasedBuildAction.BuildActionWrapper<*>? = null

        @Throws(IllegalArgumentException::class)
        override fun <T> projectsLoaded(action: BuildAction<T?>?, handler: IntermediateResultHandler<in T?>?): Builder {
            if (projectsLoadedAction != null) {
                throw getException("ProjectsLoadedAction")
            }
            projectsLoadedAction = DefaultPhasedBuildAction.DefaultBuildActionWrapper<T?>(action, handler)
            return this@Builder
        }

        @Throws(IllegalArgumentException::class)
        override fun <T> buildFinished(action: BuildAction<T?>?, handler: IntermediateResultHandler<in T?>?): Builder {
            if (buildFinishedAction != null) {
                throw getException("BuildFinishedAction")
            }
            buildFinishedAction = DefaultPhasedBuildAction.DefaultBuildActionWrapper<T?>(action, handler)
            return this@Builder
        }

        override fun build(): BuildActionExecuter<Void?> {
            return DefaultPhasedBuildActionExecuter(DefaultPhasedBuildAction(projectsLoadedAction, buildFinishedAction), connection, parameters)
        }

        companion object {
            private fun getException(phase: kotlin.String?): IllegalArgumentException {
                return IllegalArgumentException(kotlin.String.format("%s has already been added. Only one action per phase is allowed.", phase))
            }
        }
    }
}
