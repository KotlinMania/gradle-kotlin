/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.internal.logging.events

import org.gradle.api.logging.LogLevel
import org.gradle.internal.operations.logging.LogEventLevel

internal object LogLevelConverter {
    @JvmStatic
    fun convert(level: LogLevel): LogEventLevel {
        when (level) {
            LogLevel.DEBUG -> return LogEventLevel.DEBUG
            LogLevel.QUIET -> return LogEventLevel.QUIET
            LogLevel.INFO -> return LogEventLevel.INFO
            LogLevel.LIFECYCLE -> return LogEventLevel.LIFECYCLE
            LogLevel.WARN -> return LogEventLevel.WARN
            LogLevel.ERROR -> return LogEventLevel.ERROR
            else -> throw AssertionError()
        }
    }
}
