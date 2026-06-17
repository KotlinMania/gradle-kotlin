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

import org.gradle.api.logging.LogLevel
import org.gradle.internal.logging.events.OutputEvent
import org.gradle.internal.logging.events.OutputEventListener

class LogEventDispatcher(private val stdoutChain: OutputEventListener?, private val stderrChain: OutputEventListener?) : OutputEventListener {
    override fun onOutput(event: OutputEvent) {
        if (event.logLevel == null) {
            dispatch(event, stdoutChain)
            dispatch(event, stderrChain)
        } else if (event.logLevel === LogLevel.ERROR) {
            dispatch(event, stderrChain)
        } else {
            dispatch(event, stdoutChain)
        }
    }

    protected fun dispatch(event: OutputEvent, listener: OutputEventListener?) {
        if (listener != null) {
            listener.onOutput(event)
        }
    }
}
