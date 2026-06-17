/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.internal.logging.console

import org.gradle.internal.logging.events.EndOutputEvent
import org.gradle.internal.logging.events.InteractiveEvent
import org.gradle.internal.logging.events.OutputEvent
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.logging.events.UpdateNowEvent
import org.gradle.internal.time.Clock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Queue output events to be forwarded and schedule flush when time passed or if end of build is signalled.
 */
class ThrottlingOutputEventListener internal constructor(
    private val listener: OutputEventListener,
    private val throttleMs: Int,
    private val executor: ScheduledExecutorService,
    private val clock: Clock
) : OutputEventListener {
    private val lock = Any()

    private val queue: MutableList<OutputEvent> = ArrayList<OutputEvent>()

    constructor(listener: OutputEventListener, clock: Clock) : this(listener, Integer.getInteger("org.gradle.internal.console.throttle", 100), Executors.newSingleThreadScheduledExecutor(), clock)

    init {
        scheduleUpdateNow()
    }

    private fun scheduleUpdateNow() {
        val ignored = executor.scheduleAtFixedRate(Runnable {
            try {
                onOutput(UpdateNowEvent(clock.currentTime))
            } catch (t: Throwable) {
                // this class is used as task in a scheduled executor service, so it must not throw any throwable,
                // otherwise the further invocations of this task get automatically and silently cancelled
                LOGGER.debug("Exception while displaying output", t)
            }
        }, throttleMs.toLong(), throttleMs.toLong(), TimeUnit.MILLISECONDS)
    }

    override fun onOutput(newEvent: OutputEvent) {
        synchronized(lock) {
            queue.add(newEvent)
            if (queue.size == 10000) {
                renderNow()
                return
            }

            if (newEvent is InteractiveEvent) {
                renderNow()
                return
            }
            if (newEvent is EndOutputEvent) {
                // Flush and clean up
                renderNow()
                executor.shutdown()
            }
        }
    }

    private fun renderNow() {
        // Remove event only as it is handled, and leave unhandled events in the queue
        while (!queue.isEmpty()) {
            val event = queue.removeAt(0)
            listener.onOutput(event)
        }
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(ThrottlingOutputEventListener::class.java)
    }
}
