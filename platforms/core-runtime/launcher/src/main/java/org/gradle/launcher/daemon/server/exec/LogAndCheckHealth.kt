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
package org.gradle.launcher.daemon.server.exec

import com.google.common.annotations.VisibleForTesting
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.internal.util.NumberUtil
import org.gradle.launcher.daemon.server.api.DaemonCommandAction
import org.gradle.launcher.daemon.server.api.DaemonCommandExecution
import org.gradle.launcher.daemon.server.health.DaemonHealthCheck
import org.gradle.launcher.daemon.server.health.DaemonHealthStats
import org.gradle.launcher.daemon.server.stats.DaemonRunningStats
import java.lang.Boolean
import kotlin.String

class LogAndCheckHealth @VisibleForTesting internal constructor(
    private val stats: DaemonHealthStats,
    private val healthCheck: DaemonHealthCheck,
    private val runningStats: DaemonRunningStats,
    private val logger: Logger
) : DaemonCommandAction {
    constructor(stats: DaemonHealthStats, healthCheck: DaemonHealthCheck, runningStats: DaemonRunningStats) : this(stats, healthCheck, runningStats, Logging.getLogger(LogAndCheckHealth::class.java))

    override fun execute(execution: DaemonCommandExecution) {
        if (execution.isSingleUseDaemon) {
            execution.proceed()
            return
        }

        if (Boolean.getBoolean(HEALTH_MESSAGE_PROPERTY)) {
            logger.lifecycle(this.startBuildMessage)
        } else {
            // The default.
            logger.info(this.startBuildMessage)
        }

        execution.proceed()

        // Execute the health check that should send out a DaemonExpiration event
        // if the daemon is unhealthy
        healthCheck.executeHealthCheck()
    }

    private val startBuildMessage: String
        get() {
            val nextBuildNum = runningStats.getBuildCount() + 1
            if (nextBuildNum == 1) {
                return String.format("Starting build in new daemon [memory: %s]", NumberUtil.formatBytes(Runtime.getRuntime().maxMemory()))
            } else {
                return String.format("Starting %s build in daemon %s", NumberUtil.ordinal(nextBuildNum), stats.getHealthInfo())
            }
        }

    companion object {
        const val HEALTH_MESSAGE_PROPERTY: String = "org.gradle.daemon.performance.logging"
    }
}
