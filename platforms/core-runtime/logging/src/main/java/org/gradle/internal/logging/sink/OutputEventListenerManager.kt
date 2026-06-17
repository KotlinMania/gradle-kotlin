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
package org.gradle.internal.logging.sink

import org.gradle.internal.logging.events.OutputEvent
import org.gradle.internal.logging.events.OutputEventListener

/**
 * Manages dispatch of output events to the renderer and at most one other arbitrary listener.
 *
 * This is a degeneralisation of the standard ListenerManager pattern as a performance optimisation.
 * Builds generate many output events, and an extra layer of generic listener manager overhead,
 * on top of OutputEventRenderer noticeably degraded performance.
 */
class OutputEventListenerManager(private val renderer: OutputEventListener) {
    private var other: OutputEventListener? = null

    val broadcaster: OutputEventListener = object : OutputEventListener {
        override fun onOutput(event: OutputEvent) {
            renderer.onOutput(event)

            val otherRef = other
            if (otherRef != null) {
                otherRef.onOutput(event)
            }
        }
    }

    fun setListener(listener: OutputEventListener?) {
        other = listener
    }

    fun removeListener(listener: OutputEventListener?) {
        if (other === listener) {
            other = null
        }
    }
}
