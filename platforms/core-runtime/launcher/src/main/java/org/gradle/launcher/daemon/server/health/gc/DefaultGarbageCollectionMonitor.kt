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
import org.gradle.api.specs.Spec
import org.gradle.internal.time.Time
import org.gradle.util.internal.CollectionUtils
import java.lang.Boolean
import java.lang.management.GarbageCollectorMXBean
import java.lang.management.ManagementFactory
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.String

class DefaultGarbageCollectionMonitor(private val gcStrategy: GarbageCollectorMonitoringStrategy, private val pollingExecutor: ScheduledExecutorService) : GarbageCollectionMonitor {
    @get:VisibleForTesting
    val heapEvents: SlidingWindow<GarbageCollectionEvent?>

    @get:VisibleForTesting
    val nonHeapEvents: SlidingWindow<GarbageCollectionEvent?>

    init {
        this.heapEvents = DefaultSlidingWindow<GarbageCollectionEvent?>(EVENT_WINDOW)
        this.nonHeapEvents = DefaultSlidingWindow<GarbageCollectionEvent?>(EVENT_WINDOW)
        if (gcStrategy !== GarbageCollectorMonitoringStrategy.Companion.UNKNOWN && !Boolean.getBoolean(DISABLE_POLLING_SYSTEM_PROPERTY)) {
            pollForValues()
        }
    }

    private fun pollForValues() {
        val garbageCollectorMXBean = CollectionUtils.findFirst<GarbageCollectorMXBean?>(
            ManagementFactory.getGarbageCollectorMXBeans(),
            Spec { gcBean: GarbageCollectorMXBean? -> gcBean!!.getName() == gcStrategy.getGarbageCollectorName() })
        val ignored = pollingExecutor.scheduleAtFixedRate(
            GarbageCollectionCheck(Time.clock(), garbageCollectorMXBean, gcStrategy.getHeapPoolName(), heapEvents, gcStrategy.getNonHeapPoolName(), nonHeapEvents),
            POLL_DELAY_SECONDS.toLong(),
            POLL_INTERVAL_SECONDS.toLong(),
            TimeUnit.SECONDS
        )
    }

    override fun getHeapStats(): GarbageCollectionStats {
        return GarbageCollectionStats.Companion.forHeap(heapEvents.snapshot())
    }

    override fun getNonHeapStats(): GarbageCollectionStats {
        return GarbageCollectionStats.Companion.forNonHeap(nonHeapEvents.snapshot())
    }

    companion object {
        const val DISABLE_POLLING_SYSTEM_PROPERTY: String = "org.gradle.daemon.gc.polling.disabled"

        private const val POLL_INTERVAL_SECONDS = 1
        private const val POLL_DELAY_SECONDS = 1
        private const val EVENT_WINDOW = 20
    }
}
