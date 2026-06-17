/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.internal.logging.config

import org.gradle.api.logging.LogLevel

/**
 * Adapts a [LoggingConfigurer] to a [LoggingSourceSystem].
 */
class LoggingSystemAdapter(private val configurer: LoggingConfigurer) : LoggingSourceSystem {
    private var enabled = false
    private var logLevel: LogLevel? = LogLevel.LIFECYCLE

    override fun snapshot(): LoggingSystem.Snapshot {
        return SnapshotImpl(enabled, logLevel)
    }

    override fun setLevel(logLevel: LogLevel?): LoggingSystem.Snapshot {
        val snapshot = snapshot()
        if (this.logLevel != logLevel) {
            this.logLevel = logLevel
            if (enabled) {
                configurer.configure(logLevel)
            }
        }
        return snapshot
    }

    override fun startCapture(): LoggingSystem.Snapshot {
        val snapshot = snapshot()
        if (!enabled) {
            enabled = true
            configurer.configure(logLevel)
        }
        return snapshot
    }

    override fun restore(state: LoggingSystem.Snapshot?) {
        val snapshot = state as SnapshotImpl
        logLevel = snapshot.level
        enabled = snapshot.enabled
        configurer.configure(logLevel)
    }

    private class SnapshotImpl(private val enabled: Boolean, private val level: LogLevel?) : LoggingSystem.Snapshot
}
