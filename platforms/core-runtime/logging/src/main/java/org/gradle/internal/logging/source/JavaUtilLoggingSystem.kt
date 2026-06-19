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
package org.gradle.internal.logging.source

import org.gradle.api.logging.LogLevel
import org.gradle.internal.logging.config.LoggingSourceSystem
import org.gradle.internal.logging.config.LoggingSystem
import org.slf4j.bridge.SLF4JBridgeHandler
import java.util.logging.Level
import java.util.logging.LogManager
import java.util.logging.Logger

/**
 * A [LoggingSourceSystem] which configures JUL to route logging events to SLF4J.
 */
class JavaUtilLoggingSystem : LoggingSourceSystem {
    private val logger: Logger
    private var requestedLevel: LogLevel? = null
    private var installed = false

    init {
        logger = Logger.getLogger("")
    }

    override fun setLevel(logLevel: LogLevel?): LoggingSystem.Snapshot? {
        val snapshot = snapshot()
        if (logLevel != requestedLevel) {
            requestedLevel = logLevel
            if (installed) {
                logger.setLevel(LOG_LEVEL_MAPPING.get(logLevel))
            }
        }
        return snapshot
    }

    override fun startCapture(): LoggingSystem.Snapshot? {
        val snapshot = snapshot()
        install(LOG_LEVEL_MAPPING.get(requestedLevel))
        return snapshot
    }

    override fun restore(state: LoggingSystem.Snapshot?) {
        val snapshot = state as SnapshotImpl
        requestedLevel = snapshot.requestedLevel
        if (snapshot.installed) {
            install(snapshot.javaUtilLevel)
        } else {
            uninstall(snapshot.javaUtilLevel)
        }
    }

    override fun snapshot(): LoggingSystem.Snapshot? {
        return SnapshotImpl(installed, logger.getLevel(), requestedLevel)
    }

    private fun uninstall(level: Level?) {
        if (!installed) {
            return
        }

        LogManager.getLogManager().reset()
        logger.setLevel(level)
        installed = false
    }

    private fun install(level: Level?) {
        if (!installed) {
            LogManager.getLogManager().reset()
            SLF4JBridgeHandler.install()
            installed = true
        }

        logger.setLevel(level)
    }

    private class SnapshotImpl(val installed: Boolean, val javaUtilLevel: Level?, val requestedLevel: LogLevel?) : LoggingSystem.Snapshot
    companion object {
        private val LOG_LEVEL_MAPPING: MutableMap<LogLevel?, Level?> = HashMap<LogLevel?, Level?>()

        // Gradle's log levels correspond to slf4j log levels
        // as implemented in OutputEventListenerBackedLogger.
        // These levels are mapped to java.util.logging.Levels
        // corresponding to the mapping implemented in the
        // SLF4JBridgeHandler which is installed by this logging system.
        init {
            LOG_LEVEL_MAPPING.put(LogLevel.DEBUG, Level.FINE)
            LOG_LEVEL_MAPPING.put(LogLevel.INFO, Level.CONFIG)
            LOG_LEVEL_MAPPING.put(LogLevel.LIFECYCLE, Level.WARNING)
            LOG_LEVEL_MAPPING.put(LogLevel.WARN, Level.WARNING)
            LOG_LEVEL_MAPPING.put(LogLevel.QUIET, Level.SEVERE)
            LOG_LEVEL_MAPPING.put(LogLevel.ERROR, Level.SEVERE)
        }
    }
}
