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
package org.gradle.launcher.daemon.server.exec

import org.gradle.initialization.BuildEventConsumer
import org.gradle.launcher.daemon.server.api.DaemonCommandExecution
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.Volatile

/**
 * An event consumer that asynchronously dispatches events to the client.
 */
internal class DaemonConnectionBackedEventConsumer(private val execution: DaemonCommandExecution) : BuildEventConsumer {
    private val queue: BlockingQueue<Any?> = LinkedBlockingQueue<Any?>()
    private val forwarder: ForwardEvents = DaemonConnectionBackedEventConsumer.ForwardEvents()

    init {
        forwarder.start()
    }

    override fun dispatch(event: Any) {
        queue.offer(event)
    }

    fun waitForFinish() {
        forwarder.waitForFinish()
    }

    private inner class ForwardEvents : Thread("Daemon client event forwarder") {
        @Volatile
        private var stopped = false
        private var ableToSend = true

        override fun run() {
            while (moreMessagesToSend()) {
                val event = this.nextEvent
                if (event != null) {
                    dispatchEvent(event)
                }
            }
        }

        fun moreMessagesToSend(): Boolean {
            return ableToSend && !(stopped && queue.isEmpty())
        }

        val nextEvent: Any?
            get() {
                try {
                    return queue.poll(10, TimeUnit.MILLISECONDS)
                } catch (e: InterruptedException) {
                    stopped = true
                    return null
                }
            }

        fun dispatchEvent(event: Any?) {
            try {
                execution.connection!!.event(event)
            } catch (e: RuntimeException) {
                ableToSend = false
            }
        }

        fun waitForFinish() {
            stopped = true
            try {
                join()
            } catch (e: InterruptedException) {
                currentThread().interrupt()
            }
        }
    }
}
