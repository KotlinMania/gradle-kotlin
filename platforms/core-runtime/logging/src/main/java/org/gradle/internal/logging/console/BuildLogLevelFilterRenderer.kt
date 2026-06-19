/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.logging.LogLevel
import org.gradle.internal.logging.events.LogLevelChangeEvent
import org.gradle.internal.logging.events.OutputEvent
import org.gradle.internal.logging.events.OutputEventListener

class BuildLogLevelFilterRenderer(private val listener: OutputEventListener) : OutputEventListener {
    private var logLevel = LogLevel.LIFECYCLE

    override fun onOutput(event: OutputEvent) {
        val eventLogLevel = event.logLevel
        if (eventLogLevel != null && eventLogLevel.ordinal < logLevel.ordinal) {
            return
        }
        if (event is LogLevelChangeEvent) {
            val changeEvent = event
            logLevel = changeEvent.newLogLevel
        }
        listener.onOutput(event)
    }
}
