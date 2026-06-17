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

import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableList
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.specs.Spec
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.util.internal.CollectionUtils
import java.lang.management.GarbageCollectorMXBean
import java.lang.management.ManagementFactory
import java.lang.management.MemoryPoolMXBean
import java.util.function.Function

@ServiceScope(Scope.Global::class)
class GarbageCollectorMonitoringStrategy @VisibleForTesting constructor(
    val heapPoolName: String?,
    val nonHeapPoolName: String?,
    val garbageCollectorName: String?,
    val gcRateThreshold: Double,
    val heapUsageThreshold: Int,
    val nonHeapUsageThreshold: Int,
    val thrashingThreshold: Double
) {
    fun isAboveHeapUsageThreshold(percent: Int): Boolean {
        return heapUsageThreshold != -1 && percent >= heapUsageThreshold
    }

    fun isAboveNonHeapUsageThreshold(percent: Int): Boolean {
        return nonHeapUsageThreshold != -1 && percent >= nonHeapUsageThreshold
    }

    fun isAboveGcRateThreshold(gcEventsPerSec: Double): Boolean {
        return gcRateThreshold != -1.0 && gcEventsPerSec >= gcRateThreshold
    }

    fun isAboveGcThrashingThreshold(gcEventsPerSec: Double): Boolean {
        return thrashingThreshold != -1.0 && gcEventsPerSec >= thrashingThreshold
    }

    companion object {
        val ORACLE_PARALLEL_CMS: GarbageCollectorMonitoringStrategy = GarbageCollectorMonitoringStrategy("PS Old Gen", "Metaspace", "PS MarkSweep", 1.2, 80, 80, 5.0)
        val ORACLE_6_CMS: GarbageCollectorMonitoringStrategy = GarbageCollectorMonitoringStrategy("CMS Old Gen", "Metaspace", "ConcurrentMarkSweep", 1.2, 80, 80, 5.0)
        val ORACLE_SERIAL: GarbageCollectorMonitoringStrategy = GarbageCollectorMonitoringStrategy("Tenured Gen", "Metaspace", "MarkSweepCompact", 1.2, 80, 80, 5.0)
        val ORACLE_G1: GarbageCollectorMonitoringStrategy = GarbageCollectorMonitoringStrategy("G1 Old Gen", "Metaspace", "G1 Old Generation", 0.4, 75, 80, 2.0)
        val IBM_ALL: GarbageCollectorMonitoringStrategy = GarbageCollectorMonitoringStrategy("Java heap", "Not Used", "MarkSweepCompact", 0.8, 70, -1, 6.0)
        val UNKNOWN: GarbageCollectorMonitoringStrategy = GarbageCollectorMonitoringStrategy(null, null, null, -1.0, -1, -1, -1.0)

        val STRATEGIES: MutableList<GarbageCollectorMonitoringStrategy?> = ImmutableList.of<GarbageCollectorMonitoringStrategy?>(
            ORACLE_PARALLEL_CMS, ORACLE_6_CMS, ORACLE_SERIAL, ORACLE_G1, IBM_ALL, UNKNOWN
        )

        private val LOGGER: Logger = Logging.getLogger(GarbageCollectionMonitor::class.java)

        @JvmStatic
        fun determineGcStrategy(): GarbageCollectorMonitoringStrategy? {
            val garbageCollectors =
                CollectionUtils.collect<String?, GarbageCollectorMXBean?>(ManagementFactory.getGarbageCollectorMXBeans(), Function { obj: GarbageCollectorMXBean? -> obj!!.getName() })
            val gcStrategy: GarbageCollectorMonitoringStrategy? = CollectionUtils.findFirst<GarbageCollectorMonitoringStrategy?>(STRATEGIES, Spec { strategy: GarbageCollectorMonitoringStrategy? ->
                garbageCollectors.contains(
                    strategy!!.garbageCollectorName
                )
            })

            // TODO: These messages we print below are not actionable. Ideally, we would instruct the user to file an issue
            // noting the GC parameters they are using so that we can add that GC to our STRATEGIES.
            if (gcStrategy == null) {
                LOGGER.info("Unable to determine a garbage collection monitoring strategy for {}", Jvm.current())
                return UNKNOWN
            }

            val memoryPools = CollectionUtils.collect<String?, MemoryPoolMXBean?>(ManagementFactory.getMemoryPoolMXBeans(), Function { obj: MemoryPoolMXBean? -> obj!!.getName() })
            if (!memoryPools.contains(gcStrategy.heapPoolName) || !memoryPools.contains(gcStrategy.nonHeapPoolName)) {
                LOGGER.info("Unable to determine which memory pools to monitor for {}", Jvm.current())
                return UNKNOWN
            }

            return gcStrategy
        }
    }
}
