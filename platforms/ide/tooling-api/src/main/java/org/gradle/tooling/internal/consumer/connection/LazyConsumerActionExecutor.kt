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
package org.gradle.tooling.internal.consumer.connection

import com.google.common.util.concurrent.MoreExecutors
import org.gradle.initialization.BuildCancellationToken
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.tooling.internal.consumer.ConnectionParameters
import org.gradle.tooling.internal.consumer.DefaultCancellationTokenSource
import org.gradle.tooling.internal.consumer.Distribution
import org.gradle.tooling.internal.consumer.LoggingProvider
import org.gradle.tooling.internal.consumer.loader.ToolingImplementationLoader
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters
import org.gradle.tooling.internal.protocol.InternalBuildProgressListener
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

/**
 * Creates the actual executor implementation on demand.
 */
class LazyConsumerActionExecutor(
    private val distribution: Distribution,
    private val implementationLoader: ToolingImplementationLoader,
    private val loggingProvider: LoggingProvider,
    private val connectionParameters: ConnectionParameters
) : ConsumerActionExecutor {
    private val lock: Lock = ReentrantLock()
    private val condition: Condition = lock.newCondition()
    private val executing: MutableSet<Thread> = HashSet<Thread>()
    private var stopped = false
    private var connection: ConsumerConnection? = null

    private var cancellationToken: BuildCancellationToken? = null

    override fun stop() {
        lock.lock()
        try {
            stopped = true
            while (!executing.isEmpty()) {
                try {
                    condition.await()
                } catch (e: InterruptedException) {
                    throw throwAsUncheckedException(e)
                }
            }
            this.connection = null
        } finally {
            lock.unlock()
        }
    }

    override fun disconnect() {
        lock.lock()
        try {
            if (stopped || connection == null) {
                return
            }
            requestCancellation()
            sendStopWhenIdleMessageToDaemons()
        } finally {
            stopped = true
            lock.unlock()
        }
    }

    private fun requestCancellation() {
        if (cancellationToken != null && !cancellationToken!!.isCancellationRequested()) {
            cancellationToken!!.cancel()
        }
    }

    private fun sendStopWhenIdleMessageToDaemons() {
        val builder = ConsumerOperationParameters.builder()
        builder.setCancellationToken(DefaultCancellationTokenSource().token())
        builder.setParameters(connectionParameters)
        builder.setEntryPoint("Request daemon shutdown when idle")

        run<Void?>(object : ConsumerAction<Void?> {
            override val parameters: ConsumerOperationParameters?
                get() = builder.build()

            //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
            override fun run(c: ConsumerConnection): Void? {
                val executor = Executors.newFixedThreadPool(1) as ThreadPoolExecutor
                val executorService = MoreExecutors.getExitingExecutorService(executor, 3, TimeUnit.SECONDS)
                executorService.submit(object : Runnable {
                    override fun run() {
                        c.stopWhenIdle(parameters!!)
                    }
                })
                executor.shutdown()
                return null
            }
        })
    }

    override val displayName: String
        get() = "connection to " + distribution.displayName

    @Throws(UnsupportedOperationException::class, IllegalStateException::class)
    override fun <T> run(action: ConsumerAction<T?>): T? {
        try {
            val parameters = action.parameters!!
            this.cancellationToken = parameters.getCancellationToken()
            val buildProgressListener: InternalBuildProgressListener = parameters.buildProgressListener
            val connection = onStartAction(cancellationToken!!, buildProgressListener)
            return action.run(connection)
        } finally {
            onEndAction()
        }
    }

    private fun onStartAction(cancellationToken: BuildCancellationToken, buildProgressListener: InternalBuildProgressListener): ConsumerConnection {
        lock.lock()
        try {
            check(!stopped) { "This connection has been stopped." }
            executing.add(Thread.currentThread())
            if (connection == null) {
                // Hold the lock while creating the connection. Not generally good form.
                // In this instance, blocks other threads from creating the connection at the same time
                val progressLoggerFactory = loggingProvider.progressLoggerFactory
                connection = implementationLoader.create(distribution, progressLoggerFactory, buildProgressListener, connectionParameters, cancellationToken)
            }
            return connection!!
        } finally {
            lock.unlock()
        }
    }

    private fun onEndAction() {
        lock.lock()
        try {
            executing.remove(Thread.currentThread())
            condition.signalAll()
        } finally {
            lock.unlock()
        }
    }
}
