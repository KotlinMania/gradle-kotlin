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

import com.google.common.base.Joiner
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.internal.util.NumberUtil
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationResult
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationStatus
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationStrategy
import org.gradle.launcher.daemon.server.health.gc.GarbageCollectorMonitoringStrategy
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.Boolean
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.String

/**
 * A [DaemonExpirationStrategy] which monitors daemon health and expires the daemon
 * whenever unhealthy conditions are detected. Currently, this strategy monitors JVM memory
 * health by detecting GC thrashing and excessive heap or metaspace usage. In addition to
 * expiring the daemon, whenever unhealthy conditions are detected, this strategy will
 * print a warning log to the console informing the user of the issue and instructing them
 * on how to adjust daemon memory settings.
 */
@ServiceScope(Scope.Global::class)
class HealthExpirationStrategy internal constructor(private val stats: DaemonHealthStats, private val strategy: GarbageCollectorMonitoringStrategy, private val logger: Logger) :
    DaemonExpirationStrategy {
    /**
     * Used to determine if a status of a given severity has already been logged.
     * We use this to ensure we don't print the same warning multiple times to the user
     * if [.checkExpiration] is called multiple times while an unhealthy
     * memory condition persists.
     */
    private var mostSevereStatus = DaemonExpirationStatus.DO_NOT_EXPIRE
    private val statusLock: Lock = ReentrantLock()

    constructor(stats: DaemonHealthStats, strategy: GarbageCollectorMonitoringStrategy) : this(stats, strategy, LoggerFactory.getLogger(HealthExpirationStrategy::class.java))

    override fun checkExpiration(): DaemonExpirationResult? {
        // We cannot check this in the constructor since system properties are copied to the daemon after initialization.
        if (!System.getProperty(ENABLE_PERFORMANCE_MONITORING, "true").toBoolean()) {
            return DaemonExpirationResult.NOT_TRIGGERED
        }

        var expirationStatus = DaemonExpirationStatus.DO_NOT_EXPIRE
        val reasons: MutableList<String?> = ArrayList<String?>()

        val heapStats = stats.getHeapStats()
        if (heapStats.isValid && heapStats.eventCount >= 5 && strategy.isAboveHeapUsageThreshold(heapStats.usedPercent)
        ) {
            if (strategy.isAboveGcThrashingThreshold(heapStats.gcRate)) {
                reasons.add("since the JVM garbage collector is thrashing")
                expirationStatus = DaemonExpirationStatus.highestPriorityOf(DaemonExpirationStatus.IMMEDIATE_EXPIRE, expirationStatus)
            } else if (strategy.isAboveGcRateThreshold(heapStats.gcRate)) {
                reasons.add("after running out of JVM heap space")
                expirationStatus = DaemonExpirationStatus.highestPriorityOf(DaemonExpirationStatus.GRACEFUL_EXPIRE, expirationStatus)
            }
        }

        val nonHeapStats = stats.getNonHeapStats()
        if (nonHeapStats.isValid && nonHeapStats.eventCount >= 5 && strategy.isAboveNonHeapUsageThreshold(nonHeapStats.usedPercent)
        ) {
            reasons.add("after running out of JVM Metaspace")
            expirationStatus = DaemonExpirationStatus.highestPriorityOf(DaemonExpirationStatus.GRACEFUL_EXPIRE, expirationStatus)
        }

        if (expirationStatus == DaemonExpirationStatus.DO_NOT_EXPIRE) {
            return DaemonExpirationResult.NOT_TRIGGERED
        }

        // We've encountered an unhealthy condition. Log if necessary.
        val reason = Joiner.on(" and ").join(reasons)
        if (shouldPrintLog(expirationStatus)) {
            val `when` = if (expirationStatus == DaemonExpirationStatus.GRACEFUL_EXPIRE) "after the build" else "immediately"
            val extraInfo = if (expirationStatus == DaemonExpirationStatus.GRACEFUL_EXPIRE)
                "The daemon will restart for the next build, which may increase subsequent build times"
            else
                "The memory settings for this project must be adjusted to avoid this failure"

            val maxHeap = if (heapStats.isValid) NumberUtil.formatBytes(heapStats.maxSizeInBytes) else "unknown"
            val maxMetaspace = if (nonHeapStats.isValid) NumberUtil.formatBytes(nonHeapStats.maxSizeInBytes) else "unknown"
            val url = DocumentationRegistry().getDocumentationRecommendationFor("information on how to set these values", "build_environment", "sec:configuring_jvm_memory")

            logger.warn(
                (EXPIRE_DAEMON_MESSAGE + `when` + " " + reason + ".\n"
                        + "The project memory settings are likely not configured or are configured to an insufficient value.\n"
                        + extraInfo + ".\n"
                        + "These settings can be adjusted by setting 'org.gradle.jvmargs' in 'gradle.properties'.\n"
                        + "The currently configured max heap space is '" + maxHeap + "' and the configured max metaspace is '" + maxMetaspace + "'.\n"
                        + url + "\n"
                        + "To disable this warning, set '" + DISABLE_PERFORMANCE_LOGGING + "=true'.")
            )
        }

        logger.debug("Daemon health: {}", stats.getHealthInfo())

        return DaemonExpirationResult(expirationStatus, reason)
    }

    private fun shouldPrintLog(newStatus: DaemonExpirationStatus): Boolean {
        if (Boolean.getBoolean(DISABLE_PERFORMANCE_LOGGING)) {
            return false
        }

        statusLock.lock()
        try {
            val previous = mostSevereStatus
            mostSevereStatus = DaemonExpirationStatus.highestPriorityOf(previous, newStatus)
            return previous != mostSevereStatus
        } finally {
            statusLock.unlock()
        }
    }

    companion object {
        /**
         * A system property which enables this strategy. Defaults to true.
         */
        const val ENABLE_PERFORMANCE_MONITORING: String = "org.gradle.daemon.performance.enable-monitoring"

        /**
         * A system property which disables logging upon expiration events. Defaults to false.
         */
        const val DISABLE_PERFORMANCE_LOGGING: String = "org.gradle.daemon.performance.disable-logging"

        /**
         * The prefix for the message logged when an unhealthy condition is detected.
         * Used to strip this message from the logs during integration testing.
         */
        const val EXPIRE_DAEMON_MESSAGE: String = "The Daemon will expire "
    }
}
