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

import com.google.common.base.Preconditions
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.internal.util.NumberUtil
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationResult
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationStatus
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationStrategy
import org.gradle.process.internal.health.memory.OsMemoryStatus
import org.gradle.process.internal.health.memory.OsMemoryStatusAspect
import org.gradle.process.internal.health.memory.OsMemoryStatusListener
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.max
import kotlin.math.min

/**
 * An expiry strategy which only triggers when system memory falls below a threshold.
 */
class LowMemoryDaemonExpirationStrategy(minFreeMemoryPercentage: Double) : DaemonExpirationStrategy, OsMemoryStatusListener {
    private val lock = ReentrantLock()
    private var memoryStatus: OsMemoryStatus? = null
    private val minFreeMemoryPercentage: Double
    private var physicalMemoryThresholdInBytes: Long = 0
    private var virtualMemoryThresholdInBytes: Long = 0

    init {
        Preconditions.checkArgument(minFreeMemoryPercentage >= 0, "Free memory percentage must be >= 0")
        Preconditions.checkArgument(minFreeMemoryPercentage <= 1, "Free memory percentage must be <= 1")
        this.minFreeMemoryPercentage = minFreeMemoryPercentage
    }

    private fun normalizeThreshold(thresholdIn: Long, minValue: Long, maxValue: Long): Long {
        return min(maxValue, max(minValue, thresholdIn))
    }

    override fun checkExpiration(): DaemonExpirationResult? {
        lock.lock()
        try {
            if (memoryStatus != null) {
                var result = checkExpiry(memoryStatus!!.physicalMemory, physicalMemoryThresholdInBytes)
                if (result != null) {
                    return result
                }
                val virtualMemory = memoryStatus!!.virtualMemory
                if (virtualMemory is OsMemoryStatusAspect.Available) {
                    result = checkExpiry(virtualMemory, virtualMemoryThresholdInBytes)
                    if (result != null) {
                        return result
                    }
                }
            }
        } finally {
            lock.unlock()
        }
        return DaemonExpirationResult.NOT_TRIGGERED
    }

    private fun checkExpiry(memory: OsMemoryStatusAspect.Available, memoryThresholdInBytes: Long): DaemonExpirationResult? {
        val freeMem = memory.free
        if (freeMem < memoryThresholdInBytes) {
            LOGGER.info("after free system {} memory ({}) fell below threshold of {}", memory.getName(), NumberUtil.formatBytes(freeMem), NumberUtil.formatBytes(memoryThresholdInBytes))
            return DaemonExpirationResult(
                DaemonExpirationStatus.GRACEFUL_EXPIRE,
                "to reclaim system " + memory.getName() + " memory"
            )
        } else if (freeMem < memoryThresholdInBytes * 2) {
            LOGGER.debug("Nearing low {} memory threshold - {}", memory.getName(), memoryStatus)
        }
        return null
    }

    override fun onOsMemoryStatus(newStatus: OsMemoryStatus?) {
        lock.lock()
        try {
            this.memoryStatus = newStatus
            this.physicalMemoryThresholdInBytes = normalizeThreshold((memoryStatus!!.physicalMemory.total * minFreeMemoryPercentage).toLong(), MIN_THRESHOLD_BYTES, MAX_THRESHOLD_BYTES)
            val virtualMemory = memoryStatus!!.virtualMemory
            if (virtualMemory is OsMemoryStatusAspect.Available) {
                this.virtualMemoryThresholdInBytes = normalizeThreshold((virtualMemory.total * minFreeMemoryPercentage).toLong(), MIN_THRESHOLD_BYTES, MAX_THRESHOLD_BYTES)
            }
        } finally {
            lock.unlock()
        }
    }

    companion object {
        private val LOGGER: Logger = Logging.getLogger(LowMemoryDaemonExpirationStrategy::class.java)

        // Reasonable default threshold bounds: between 384M and 1G
        val MIN_THRESHOLD_BYTES: Long = (384 * 1024 * 1024).toLong()
        val MAX_THRESHOLD_BYTES: Long = (1024 * 1024 * 1024).toLong()
    }
}
