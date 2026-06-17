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
package org.gradle.launcher.daemon.server.health.gc

import org.gradle.internal.time.Clock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.management.GarbageCollectorMXBean
import java.lang.management.ManagementFactory
import java.lang.management.MemoryType

class GarbageCollectionCheck(
    private val clock: Clock,
    private val garbageCollectorMXBean: GarbageCollectorMXBean,
    private val heapMemoryPool: String?,
    private val heapEvents: SlidingWindow<GarbageCollectionEvent?>,
    private val nonHeapMemoryPool: String?,
    private val nonHeapEvents: SlidingWindow<GarbageCollectionEvent?>
) : Runnable {
    override fun run() {
        try {
            val memoryPoolMXBeans = ManagementFactory.getMemoryPoolMXBeans()
            for (memoryPoolMXBean in memoryPoolMXBeans) {
                val poolName = memoryPoolMXBean.getName()
                if (memoryPoolMXBean.getType() == MemoryType.HEAP && poolName == heapMemoryPool) {
                    val latest = heapEvents.latest()
                    val currentCount = garbageCollectorMXBean.getCollectionCount()
                    // There has been a GC event
                    if (latest == null || latest.getCount() != currentCount) {
                        heapEvents.slideAndInsert(GarbageCollectionEvent(clock.currentTime, memoryPoolMXBean.getCollectionUsage(), currentCount))
                    }
                }
                if (memoryPoolMXBean.getType() == MemoryType.NON_HEAP && poolName == nonHeapMemoryPool) {
                    nonHeapEvents.slideAndInsert(GarbageCollectionEvent(clock.currentTime, memoryPoolMXBean.getUsage(), -1))
                }
            }
        } catch (t: Throwable) {
            // this class is used as task in a scheduled executor service, so it must not throw any throwable,
            // otherwise the further invocations of this task get automatically and silently cancelled
            LOGGER.debug("Exception while checking garbage collection", t)
        }
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(GarbageCollectionCheck::class.java)
    }
}
