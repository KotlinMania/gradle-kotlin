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

import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.ResultHandler
import org.gradle.tooling.internal.consumer.async.AsyncConsumerActionExecutor
import org.gradle.tooling.internal.consumer.connection.ConsumerAction
import org.gradle.tooling.internal.consumer.connection.ConsumerConnection
import org.gradle.tooling.model.Launchable
import org.gradle.tooling.model.Task
import java.lang.String
import java.util.Arrays
import kotlin.Throwable

class DefaultBuildLauncher(connection: AsyncConsumerActionExecutor, parameters: ConnectionParameters?) : AbstractLongRunningOperation<DefaultBuildLauncher?>(parameters), BuildLauncher {
    protected val connection: AsyncConsumerActionExecutor

    init {
        operationParamsBuilder.setEntryPoint("BuildLauncher API")
        operationParamsBuilder.setTasks(mutableListOf<String?>())
        this.connection = connection
    }

    override fun getThis(): DefaultBuildLauncher {
        return this
    }

    override fun forTasks(vararg tasks: String?): BuildLauncher {
        operationParamsBuilder.setTasks(Arrays.asList<String>(*tasks))
        return this
    }

    override fun forTasks(vararg tasks: Task?): BuildLauncher {
        forTasks(Arrays.asList<Task?>(*tasks))
        return this
    }

    override fun forTasks(tasks: Iterable<out Task?>): BuildLauncher {
        forLaunchables(tasks)
        return this
    }

    override fun forLaunchables(vararg launchables: Launchable?): BuildLauncher {
        return forLaunchables(Arrays.asList<Launchable?>(*launchables))
    }

    override fun forLaunchables(launchables: Iterable<out Launchable?>): BuildLauncher {
        preprocessLaunchables(launchables)
        operationParamsBuilder.setLaunchables(launchables)
        return this
    }

    protected fun preprocessLaunchables(launchables: Iterable<out Launchable?>?) {
    }

    override fun run() {
        val handler = BlockingResultHandler<Void?>(Void::class.java)
        run(handler)
        handler.getResult()
    }

    override fun run(handler: ResultHandler<in Void?>?) {
        val parameters = getConsumerOperationParameters()
        connection.run<Void?>(object : ConsumerAction<Void?> {
            override fun run(connection: ConsumerConnection): Void? {
                return connection.run<Void?>(Void::class.java, this.parameters)
            }
        }, DefaultBuildLauncher.ResultHandlerAdapter(handler))
    }

    private inner class ResultHandlerAdapter(handler: ResultHandler<in Void?>?) : org.gradle.tooling.internal.consumer.ResultHandlerAdapter<Void?>(
        handler,
        this@DefaultBuildLauncher.createExceptionTransformer(object : ConnectionExceptionTransformer.ConnectionFailureMessageProvider {
            override fun getConnectionFailureMessage(throwable: Throwable?): String? {
                return String.format("Could not execute build using %s.", connection.displayName)
            }
        })
    )
}
