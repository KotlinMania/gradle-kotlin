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

internal class ErrorOutputDispatchingListener(private val stderrChain: OutputEventListener, private val stdoutChain: OutputEventListener) : OutputEventListener {
    override fun onOutput(event: OutputEvent) {
        if (event.logLevel == null) {
            stderrChain.onOutput(event)
            stdoutChain.onOutput(event)
        } else if (event.logLevel !== LogLevel.ERROR) {
            stdoutChain.onOutput(event)
        } else {
            // TODO - should attempt to flush the output stream prior to writing to the error stream (and vice versa)
            stderrChain.onOutput(event)
        }
    }
}
