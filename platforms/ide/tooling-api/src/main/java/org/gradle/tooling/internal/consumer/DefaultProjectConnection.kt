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

import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildActionExecuter
import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.ModelBuilder
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.ResultHandler
import org.gradle.tooling.TestLauncher
import org.gradle.tooling.internal.consumer.async.AsyncConsumerActionExecutor
import org.gradle.tooling.internal.consumer.connection.ConsumerAction
import org.gradle.tooling.internal.consumer.connection.ConsumerConnection
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters.Companion.builder
import java.lang.String
import java.nio.file.Path
import kotlin.Throwable
import kotlin.require

internal class DefaultProjectConnection(private val connection: AsyncConsumerActionExecutor, private val parameters: ConnectionParameters, private val listener: ProjectConnectionCloseListener) :
    ProjectConnection {
    override fun close() {
        connection.stop()
        listener.connectionClosed(this)
    }

    fun disconnect() {
        connection.disconnect()
    }

    override fun <T> getModel(modelType: Class<T?>): T? {
        return model<T?>(modelType).get()
    }

    override fun <T> getModel(modelType: Class<T?>, handler: ResultHandler<in T?>?) {
        model<T?>(modelType).get(handler)
    }

    override fun newBuild(): BuildLauncher {
        return DefaultBuildLauncher(connection, parameters)
    }

    override fun newTestLauncher(): TestLauncher {
        return DefaultTestLauncher(connection, parameters)
    }

    override fun <T> model(modelType: Class<T?>): ModelBuilder<T?> {
        require(modelType.isInterface()) { String.format("Cannot fetch a model of type '%s' as this type is not an interface.", modelType.getName()) }
        return DefaultModelBuilder<T?>(modelType, connection, parameters)
    }

    override fun <T> action(buildAction: BuildAction<T?>): BuildActionExecuter<T?> {
        return DefaultBuildActionExecuter<T?>(buildAction, connection, parameters)
    }

    override fun action(): BuildActionExecuter.Builder {
        return DefaultBuildActionExecuter.Builder(connection, parameters)
    }

    override fun notifyDaemonsAboutChangedPaths(changedPaths: MutableList<Path>) {
        val absolutePaths: MutableList<String?> = ArrayList<String?>(changedPaths.size)
        for (changedPath in changedPaths) {
            require(changedPath.isAbsolute()) { String.format("Changed path '%s' is not absolute", changedPath) }
            absolutePaths.add(changedPath.toString())
        }
        val operationParamsBuilder = builder()
        operationParamsBuilder.setCancellationToken(DefaultCancellationTokenSource().token())
        operationParamsBuilder.setParameters(parameters)
        operationParamsBuilder.setEntryPoint("Notify daemons about changed paths API")
        connection.run<Void?>(
            object : ConsumerAction<Void?> {
                val parameters: ConsumerOperationParameters
                    get() = operationParamsBuilder.build()

                override fun run(connection: ConsumerConnection): Void? {
                    connection.notifyDaemonsAboutChangedPaths(absolutePaths, parameters)
                    return null
                }
            },
            ResultHandlerAdapter<Void?>(
                BlockingResultHandler<Void?>(Void::class.java),
                ConnectionExceptionTransformer(object : ConnectionExceptionTransformer.ConnectionFailureMessageProvider {
                    override fun getConnectionFailureMessage(throwable: Throwable?): String? {
                        return String.format("Could not notify daemons about changed paths: %s.", connection.displayName)
                    }
                })
            )
        )
    }
}
