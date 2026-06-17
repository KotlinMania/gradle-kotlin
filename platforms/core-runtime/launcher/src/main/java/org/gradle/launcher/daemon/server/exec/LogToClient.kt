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
package org.gradle.launcher.daemon.server.exec

import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.internal.logging.LoggingOutputInternal
import org.gradle.internal.logging.events.OutputEvent
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.logging.events.ProgressCompleteEvent
import org.gradle.internal.logging.events.ProgressEvent
import org.gradle.internal.logging.events.ProgressStartEvent
import org.gradle.launcher.daemon.diagnostics.DaemonDiagnostics
import org.gradle.launcher.daemon.logging.DaemonMessages
import org.gradle.launcher.daemon.protocol.Build
import org.gradle.launcher.daemon.server.api.DaemonCommandExecution
import org.gradle.launcher.daemon.server.api.DaemonConnection
import java.lang.Boolean
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import kotlin.Exception
import kotlin.String
import kotlin.also
import kotlin.concurrent.Volatile

class LogToClient(private val loggingOutput: LoggingOutputInternal, private val diagnostics: DaemonDiagnostics) : BuildCommandOnly() {
    @Volatile
    private var dispatcher: AsynchronousLogDispatcher? = null

    override fun doBuild(execution: DaemonCommandExecution, build: Build) {
        if (Boolean.getBoolean(DISABLE_OUTPUT)) {
            execution.proceed()
            return
        }

        dispatcher = LogToClient.AsynchronousLogDispatcher(execution.connection, build.getParameters().getLogLevel())
        LOGGER.info("{}{}). The daemon log file: {}", DaemonMessages.STARTED_RELAYING_LOGS, diagnostics.getPid(), diagnostics.getDaemonLog())
        dispatcher!!.start()
        try {
            execution.proceed()
        } finally {
            dispatcher!!.waitForCompletion()
        }
    }

    private inner class AsynchronousLogDispatcher(private val connection: DaemonConnection, buildLogLevel: LogLevel?) : Thread("Asynchronous log dispatcher for " + connection) {
        private val completionLock = CountDownLatch(1)
        private val eventQueue: Queue<OutputEvent?> = ConcurrentLinkedQueue<OutputEvent?>()
        private val listener: OutputEventListener

        @Volatile
        private var shouldStop = false
        private var unableToSend = false

        init {
            this.listener = object : OutputEventListener {
                override fun onOutput(event: OutputEvent) {
                    if (dispatcher != null && (isMatchingBuildLogLevel(event) || isProgressEvent(event))) {
                        dispatcher!!.submit(event)
                    }
                }

                fun isProgressEvent(event: OutputEvent?): kotlin.Boolean {
                    return event is ProgressStartEvent || event is ProgressEvent || event is ProgressCompleteEvent
                }

                fun isMatchingBuildLogLevel(event: OutputEvent): kotlin.Boolean {
                    return buildLogLevel != null && event.logLevel != null && event.logLevel!!.compareTo(buildLogLevel) >= 0
                }
            }
            LOGGER.debug(DaemonMessages.ABOUT_TO_START_RELAYING_LOGS)
            loggingOutput.addOutputEventListener(listener)
        }

        fun submit(event: OutputEvent?) {
            eventQueue.add(event)
        }

        override fun run() {
            try {
                while (!shouldStop) {
                    val event = eventQueue.poll()
                    if (event == null) {
                        sleep(10)
                    } else {
                        dispatchAsync(event)
                    }
                }
            } catch (ex: InterruptedException) {
                // we must not use interrupt() because it would automatically
                // close the connection (sending data from an interrupted thread
                // automatically closes the connection)
                shouldStop = true
            }
            sendRemainingEvents()
            completionLock.countDown()
        }

        fun sendRemainingEvents() {
            var event: OutputEvent?
            while ((eventQueue.poll().also { event = it }) != null) {
                dispatchAsync(event)
            }
        }

        fun dispatchAsync(event: OutputEvent?) {
            if (unableToSend) {
                return
            }
            try {
                connection.logEvent(event)
            } catch (ex: Exception) {
                shouldStop = true
                unableToSend = true
                //Ignore. It means the client has disconnected so no point sending him any log output.
                //we should be checking if client still listens elsewhere anyway.
            }
        }

        fun waitForCompletion() {
            loggingOutput.removeOutputEventListener(listener)
            shouldStop = true
            try {
                completionLock.await()
            } catch (e: InterruptedException) {
                currentThread().interrupt()
            }
        }
    }

    companion object {
        const val DISABLE_OUTPUT: String = "org.gradle.daemon.disable-output"
        private val LOGGER: Logger = Logging.getLogger(LogToClient::class.java)
    }
}
