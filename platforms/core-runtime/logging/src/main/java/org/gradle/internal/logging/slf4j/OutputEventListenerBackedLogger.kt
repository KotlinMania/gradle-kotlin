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
package org.gradle.internal.logging.slf4j

import org.gradle.api.logging.LogLevel
import org.gradle.internal.logging.events.LogEvent
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.time.Clock

open class OutputEventListenerBackedLogger(private val name: String, private val context: OutputEventListenerBackedLoggerContext, private val clock: Clock) : BuildOperationAwareLogger() {
    override fun getName(): String {
        return name
    }

    override fun isLevelAtMost(levelLimit: LogLevel?): Boolean {
        return levelLimit != null && levelLimit.compareTo(context.getLevel()!!) >= 0
    }

    override fun log(logLevel: LogLevel?, throwable: Throwable?, message: String?, operationIdentifier: OperationIdentifier?) {
        val logEvent = LogEvent(clock.currentTime, name, logLevel ?: LogLevel.LIFECYCLE, message ?: "", throwable, operationIdentifier)
        val outputEventListener = context.getOutputEventListener()
        try {
            outputEventListener!!.onOutput(logEvent)
        } catch (e: Throwable) {
            // fall back to standard out
            e.printStackTrace(System.out)
        }
    }
}
