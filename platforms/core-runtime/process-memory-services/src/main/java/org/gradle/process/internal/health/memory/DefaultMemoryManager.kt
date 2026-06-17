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
package org.gradle.process.internal.health.memory

import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Preconditions
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.concurrent.ManagedScheduledExecutor
import org.gradle.internal.concurrent.Stoppable
import org.gradle.internal.event.ListenerManager
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import kotlin.collections.MutableList
import kotlin.math.max

class DefaultMemoryManager @VisibleForTesting internal constructor(
    osMemoryInfo: OsMemoryInfo,
    jvmMemoryInfo: JvmMemoryInfo,
    listenerManager: ListenerManager,
    executorFactory: ExecutorFactory,
    minFreeMemoryPercentage: Double,
    autoFree: Boolean
) : MemoryManager, Stoppable {
    private val minFreeMemoryPercentage: Double
    private val osMemoryInfo: OsMemoryInfo
    private val jvmMemoryInfo: JvmMemoryInfo
    private val listenerManager: ListenerManager
    private val scheduler: ManagedScheduledExecutor
    private val jvmBroadcast: JvmMemoryStatusListener?
    private val osBroadcast: OsMemoryStatusListener?
    private val osMemoryStatusSupported: Boolean
    private val holdersLock = Any()
    private val memoryLock = Any()
    private val holders: MutableList<MemoryHolder?> = ArrayList<MemoryHolder?>()
    private var currentOsMemoryStatus: OsMemoryStatus? = null
    private val osMemoryStatusListener: OsMemoryStatusListener

    constructor(osMemoryInfo: OsMemoryInfo, jvmMemoryInfo: JvmMemoryInfo, listenerManager: ListenerManager, executorFactory: ExecutorFactory) : this(
        osMemoryInfo,
        jvmMemoryInfo,
        listenerManager,
        executorFactory,
        DEFAULT_MIN_FREE_MEMORY_PERCENTAGE,
        true
    )

    init {
        Preconditions.checkArgument(minFreeMemoryPercentage >= 0, "Free memory percentage must be >= 0")
        Preconditions.checkArgument(minFreeMemoryPercentage <= 1, "Free memory percentage must be <= 1")
        this.minFreeMemoryPercentage = minFreeMemoryPercentage
        this.osMemoryInfo = osMemoryInfo
        this.jvmMemoryInfo = jvmMemoryInfo
        this.listenerManager = listenerManager
        this.scheduler = executorFactory.createScheduled("Memory manager", 1)
        this.jvmBroadcast = listenerManager.getBroadcaster<JvmMemoryStatusListener?>(JvmMemoryStatusListener::class.java)
        this.osBroadcast = listenerManager.getBroadcaster<OsMemoryStatusListener?>(OsMemoryStatusListener::class.java)
        this.osMemoryStatusSupported = supportsOsMemoryStatus()
        this.osMemoryStatusListener = DefaultMemoryManager.OsMemoryListener(autoFree)
        start()
    }

    private fun supportsOsMemoryStatus(): Boolean {
        try {
            osMemoryInfo.getOsSnapshot()
            return true
        } catch (ex: UnsupportedOperationException) {
            return false
        }
    }

    private fun start() {
        val ignored = scheduler.scheduleAtFixedRate(DefaultMemoryManager.MemoryCheck(), STATUS_INTERVAL_SECONDS.toLong(), STATUS_INTERVAL_SECONDS.toLong(), TimeUnit.SECONDS)
        LOGGER.debug("Memory status broadcaster started")
        if (osMemoryStatusSupported) {
            addListener(osMemoryStatusListener)
        } else {
            LOGGER.info("This JVM does not support getting OS memory, so no OS memory status updates will be broadcast")
        }
    }

    override fun stop() {
        scheduler.stop()
        listenerManager.removeListener(osMemoryStatusListener)
    }

    override fun requestFreeMemory(memoryAmountBytes: Long) {
        synchronized(memoryLock) {
            if (currentOsMemoryStatus != null) {
                val freedPhysical = freeSpecificMemory(currentOsMemoryStatus!!.getPhysicalMemory(), memoryAmountBytes)
                val freedVirtual = freeSpecificMemory(currentOsMemoryStatus!!.getVirtualMemory(), memoryAmountBytes)
                // If we've freed memory, invalidate the current OS memory snapshot
                if (freedPhysical || freedVirtual) {
                    currentOsMemoryStatus = null
                }
            } else {
                LOGGER.debug("There is no current snapshot of OS memory available - memory cannot be freed until a new memory status update occurs")
            }
        }
    }

    private fun freeSpecificMemory(status: OsMemoryStatusAspect, memoryAmountBytes: Long): Boolean {
        if (status is OsMemoryStatusAspect.Unavailable) {
            // no need to free
            return false
        }
        val totalMemory = (status as OsMemoryStatusAspect.Available).getTotal()
        val freeMemory = status.getFree()
        val requestedFreeMemory = getMemoryThresholdInBytes(totalMemory) + (if (memoryAmountBytes > 0) memoryAmountBytes else 0)
        val newFreeMemory = doRequestFreeMemory(status.getName(), requestedFreeMemory, freeMemory)
        return newFreeMemory > freeMemory
    }

    private fun doRequestFreeMemory(name: String?, requestedFreeMemory: Long, freeMemory: Long): Long {
        var freeMemory = freeMemory
        var toReleaseMemory = requestedFreeMemory
        if (freeMemory < requestedFreeMemory) {
            LOGGER.debug("{} {} memory requested, {} free", requestedFreeMemory, name, freeMemory)
            val memoryHolders: MutableList<MemoryHolder>?
            synchronized(holdersLock) {
                memoryHolders = ArrayList<MemoryHolder>(holders)
            }
            for (holder in memoryHolders!!) {
                val released = holder.attemptToRelease(toReleaseMemory)
                toReleaseMemory -= released
                freeMemory += released
                if (freeMemory >= requestedFreeMemory) {
                    break
                }
            }

            LOGGER.debug("{} {} memory requested, {} released, {} free", requestedFreeMemory, name, requestedFreeMemory - toReleaseMemory, freeMemory)
        }
        return freeMemory
    }

    private fun getMemoryThresholdInBytes(totalMemory: Long): Long {
        return max(MIN_THRESHOLD_BYTES, (totalMemory * minFreeMemoryPercentage).toLong())
    }

    private inner class MemoryCheck : Runnable {
        override fun run() {
            try {
                if (osMemoryStatusSupported) {
                    val os = osMemoryInfo.getOsSnapshot()
                    osBroadcast!!.onOsMemoryStatus(os)
                }
                val jvm = jvmMemoryInfo.getJvmSnapshot()
                jvmBroadcast!!.onJvmMemoryStatus(jvm)
            } catch (t: Throwable) {
                // this class is used as task in a scheduled executor service, so it must not throw any throwable,
                // otherwise the further invocations of this task get automatically and silently cancelled
                LOGGER.debug("Failed to collect memory status: {}", t.message, t)
            }
        }
    }

    private inner class OsMemoryListener(private val autoFree: Boolean) : OsMemoryStatusListener {
        override fun onOsMemoryStatus(os: OsMemoryStatus?) {
            currentOsMemoryStatus = os
            if (autoFree) {
                requestFreeMemory(0)
            }
        }
    }

    override fun addMemoryHolder(holder: MemoryHolder?) {
        synchronized(holdersLock) {
            holders.add(holder)
        }
    }

    override fun removeMemoryHolder(holder: MemoryHolder?) {
        synchronized(holdersLock) {
            holders.remove(holder)
        }
    }

    override fun addListener(listener: JvmMemoryStatusListener) {
        listenerManager.addListener(listener)
    }

    override fun addListener(listener: OsMemoryStatusListener) {
        listenerManager.addListener(listener)
    }

    override fun removeListener(listener: JvmMemoryStatusListener) {
        listenerManager.removeListener(listener)
    }

    override fun removeListener(listener: OsMemoryStatusListener) {
        listenerManager.removeListener(listener)
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(DefaultMemoryManager::class.java)
        const val STATUS_INTERVAL_SECONDS: Int = 5
        private const val DEFAULT_MIN_FREE_MEMORY_PERCENTAGE = 0.1 // 10%
        private val MIN_THRESHOLD_BYTES = (384 * 1024 * 1024 // 384M
                ).toLong()
    }
}
