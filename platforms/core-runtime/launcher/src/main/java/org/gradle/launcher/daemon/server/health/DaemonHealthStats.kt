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
package org.gradle.launcher.daemon.server.health

import com.google.common.annotations.VisibleForTesting
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.concurrent.ManagedScheduledExecutor
import org.gradle.internal.concurrent.Stoppable
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.internal.util.NumberUtil
import org.gradle.launcher.daemon.server.health.gc.DefaultGarbageCollectionMonitor
import org.gradle.launcher.daemon.server.health.gc.GarbageCollectionInfo
import org.gradle.launcher.daemon.server.health.gc.GarbageCollectionMonitor
import org.gradle.launcher.daemon.server.health.gc.GarbageCollectionStats
import org.gradle.launcher.daemon.server.health.gc.GarbageCollectorMonitoringStrategy
import org.gradle.launcher.daemon.server.stats.DaemonRunningStats
import java.util.Locale

@ServiceScope(Scope.Global::class)
class DaemonHealthStats : Stoppable {
    private val runningStats: DaemonRunningStats
    private val scheduler: ManagedScheduledExecutor?
    private val gcInfo: GarbageCollectionInfo

    @get:VisibleForTesting
    val gcMonitor: GarbageCollectionMonitor

    constructor(runningStats: DaemonRunningStats, strategy: GarbageCollectorMonitoringStrategy, executorFactory: ExecutorFactory) {
        this.runningStats = runningStats
        this.scheduler = executorFactory.createScheduled("Daemon health stats", 1)
        this.gcInfo = GarbageCollectionInfo()
        this.gcMonitor = DefaultGarbageCollectionMonitor(strategy, scheduler)
    }

    @VisibleForTesting
    internal constructor(runningStats: DaemonRunningStats, gcInfo: GarbageCollectionInfo, gcMonitor: GarbageCollectionMonitor) {
        this.runningStats = runningStats
        this.scheduler = null
        this.gcInfo = gcInfo
        this.gcMonitor = gcMonitor
    }

    override fun stop() {
        if (scheduler != null) {
            scheduler.stop()
        }
    }

    val heapStats: GarbageCollectionStats
        get() = gcMonitor.heapStats

    val nonHeapStats: GarbageCollectionStats
        get() = gcMonitor.nonHeapStats

    val healthInfo: String
        /**
         * Elegant description of daemon's health
         */
        get() {
            val message = StringBuilder()
            message.append(String.format("[uptime: %s, performance: %s%%", runningStats.getPrettyUpTime(), this.currentPerformance))

            val heapStats: GarbageCollectionStats = gcMonitor.heapStats!!
            if (heapStats.isValid) {
                message.append(String.format(Locale.ENGLISH, ", GC rate: %.2f/s", heapStats.gcRate))
                message.append(String.format(", heap usage: %s%% of %s", heapStats.usedPercent, NumberUtil.formatBytes(heapStats.maxSizeInBytes)))
            }

            val nonHeapStats: GarbageCollectionStats = gcMonitor.nonHeapStats!!
            if (nonHeapStats.isValid) {
                message.append(String.format(", non-heap usage: %s%% of %s", nonHeapStats.usedPercent, NumberUtil.formatBytes(nonHeapStats.maxSizeInBytes)))
            }
            message.append("]")

            return message.toString()
        }

    private val currentPerformance: Int
        /**
         * 0-100, the percentage of time spent on doing the work vs time spent in gc
         */
        get() {
            val collectionTime = gcInfo.collectionTime
            val allBuildsTime = runningStats.getAllBuildsTime()

            if (collectionTime > 0 && collectionTime < allBuildsTime) {
                return 100 - NumberUtil.percentOf(collectionTime, allBuildsTime)
            } else {
                return 100
            }
        }
}
