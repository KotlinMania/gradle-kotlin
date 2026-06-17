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
package org.gradle.tooling.internal.consumer.async

import org.gradle.internal.concurrent.CompositeStoppable
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.concurrent.ManagedExecutor
import org.gradle.tooling.internal.consumer.connection.ConsumerAction
import org.gradle.tooling.internal.consumer.connection.ConsumerActionExecutor
import org.gradle.tooling.internal.protocol.ResultHandlerVersion1

/**
 * Adapts a [ConsumerActionExecutor] to an [AsyncConsumerActionExecutor].
 */
class DefaultAsyncConsumerActionExecutor(private val actionExecutor: ConsumerActionExecutor, executorFactory: ExecutorFactory) : AsyncConsumerActionExecutor {
    private val executor: ManagedExecutor
    private val lifecycle: ServiceLifecycle

    init {
        executor = executorFactory.create("Connection worker")
        lifecycle = ServiceLifecycle(actionExecutor.getDisplayName())
    }

    override fun getDisplayName(): String {
        return actionExecutor.getDisplayName()
    }

    override fun stop() {
        CompositeStoppable.stoppable(lifecycle, executor, actionExecutor).stop()
    }

    override fun disconnect() {
        lifecycle.requestStop()
        executor.requestStop()
        actionExecutor.disconnect()
    }

    override fun <T> run(action: ConsumerAction<out T?>, handler: ResultHandlerVersion1<in T?>) {
        lifecycle.use(object : Runnable {
            override fun run() {
                executor.execute(object : Runnable {
                    override fun run() {
                        val result: T?
                        try {
                            result = actionExecutor.run(action)
                        } catch (t: Throwable) {
                            handler.onFailure(t)
                            return
                        }
                        handler.onComplete(result)
                    }
                })
            }
        })
    }
}
