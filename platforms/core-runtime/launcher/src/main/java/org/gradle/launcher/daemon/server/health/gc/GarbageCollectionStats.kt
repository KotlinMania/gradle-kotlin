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

import com.google.common.collect.Iterables
import org.gradle.internal.util.NumberUtil
import java.util.concurrent.TimeUnit

class GarbageCollectionStats private constructor(@JvmField val gcRate: Double, usedSizeInBytes: Long, maxSizeInBytes: Long, eventCount: Long) {
    @JvmField
    val usedPercent: Int
    @JvmField
    val maxSizeInBytes: Long
    @JvmField
    val eventCount: Long

    init {
        if (maxSizeInBytes > 0) {
            this.usedPercent = NumberUtil.percentOf(usedSizeInBytes, maxSizeInBytes)
        } else {
            this.usedPercent = 0
        }
        this.maxSizeInBytes = maxSizeInBytes
        this.eventCount = eventCount
    }

    val isValid: Boolean
        get() = maxSizeInBytes > 0

    companion object {
        fun forHeap(events: MutableCollection<GarbageCollectionEvent>): GarbageCollectionStats {
            if (events.isEmpty()) {
                return noData()
            } else {
                return GarbageCollectionStats(
                    calculateRate(events),
                    calculateAverageUsage(events),
                    findMaxSize(events),
                    events.size.toLong()
                )
            }
        }

        fun forNonHeap(events: MutableCollection<GarbageCollectionEvent>): GarbageCollectionStats {
            if (events.isEmpty()) {
                return noData()
            } else {
                return GarbageCollectionStats(
                    0.0,  // non-heap spaces are not garbage collected
                    calculateAverageUsage(events),
                    findMaxSize(events),
                    events.size.toLong()
                )
            }
        }

        private fun noData(): GarbageCollectionStats {
            return GarbageCollectionStats(0.0, 0, -1, 0)
        }

        /**
         * @return a rate in unit 'GC events per second'
         */
        private fun calculateRate(events: MutableCollection<GarbageCollectionEvent>): Double {
            if (events.size < 2) {
                // not enough data points
                return 0.0
            }
            val first = events.iterator().next()
            val last = Iterables.getLast<GarbageCollectionEvent?>(events)
            // Total number of garbage collection events observed in the window
            val gcCountDelta = last!!.getCount() - first.getCount()
            // Time interval between the first event in the window and the last
            val timeDelta = TimeUnit.MILLISECONDS.toSeconds(last.getTimestamp() - first.getTimestamp())
            return gcCountDelta.toDouble() / timeDelta
        }

        private fun calculateAverageUsage(events: MutableCollection<GarbageCollectionEvent>): Long {
            var sum: Long = 0
            for (event in events) {
                sum += event.getUsage().getUsed()
            }
            return sum / events.size
        }

        private fun findMaxSize(events: MutableCollection<GarbageCollectionEvent>): Long {
            // Maximum pool size is fixed, so we should only need to get it from the first event
            val first = events.iterator().next()
            return first.getUsage().getMax()
        }
    }
}
