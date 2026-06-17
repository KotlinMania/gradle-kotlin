/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.internal.time

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * A clock that is guaranteed to not go backwards.
 *
 *
 * It aims to strike a balance between never going backwards (allowing timestamps to represent causality)
 * and keeping in sync with the system wall clock so that time values make sense in comparison with the system wall clock,
 * including timestamps generated from other processes.
 *
 *
 * This clock effectively measures time by duration (according to System.nanoTime()),
 * in between syncs with the system wall clock.
 * When issuing the first timestamp after the sync interval has expired,
 * The system wall clock will be read, and the current time set to the max of wall clock time or the most recently issued timestamp.
 * All other timestamps are calculated as the wall clock time at last sync + elapsed time since.
 *
 *
 * This clock deals relatively well when the system wall clock shift is adjusted by small amounts.
 * It also deals relatively well when the system wall clock jumps forward by large amounts (this clock will jump with it).
 * It does not deal as well with large jumps back in time.
 *
 *
 * When the system wall clock jumps back in time, this clock will effectively slow down until it is back in sync.
 * All syncing timestamps will be the same as the previously issued timestamp.
 * The rate by which this clock slows, and therefore the time it takes to resync,
 * is determined by how frequently the clock is read.
 * If timestamps are only requested at a rate greater than the sync interval,
 * all timestamps will have the same value until the clocks synchronize (i.e. this clock will pause).
 * If timestamps are requested more frequently than the sync interval,
 * timestamps before and after the sync point will under represent the actual elapsed time,
 * gradually bringing the clocks back into sync.
 */
internal class MonotonicClock @JvmOverloads constructor(timeSource: TimeSource = TimeSource.Companion.SYSTEM, syncIntervalMillis: Long = SYNC_INTERVAL_MILLIS) : Clock {
    private val syncIntervalMillis: Long
    private val timeSource: TimeSource

    private val syncMillisRef: AtomicLong
    private val syncNanosRef: AtomicLong
    private val currentTime = AtomicLong()

    init {
        val nanoTime = timeSource.nanoTime()
        val currentTimeMillis = timeSource.currentTimeMillis()

        this.timeSource = timeSource
        this.syncIntervalMillis = syncIntervalMillis
        this.syncNanosRef = AtomicLong(nanoTime)
        this.syncMillisRef = AtomicLong(currentTimeMillis)
        this.currentTime.set(currentTimeMillis)
    }

    override fun getCurrentTime(): Long {
        val nowNanos = timeSource.nanoTime()
        val syncNanos = syncNanosRef.get()
        val syncMillis = syncMillisRef.get()
        val sinceSyncNanos = nowNanos - syncNanos
        val sinceSyncMillis = TimeUnit.NANOSECONDS.toMillis(sinceSyncNanos)

        if (syncIsDue(nowNanos, syncNanos, sinceSyncMillis)) {
            return sync(syncMillis)
        } else {
            return advance(syncMillis + sinceSyncMillis)
        }
    }

    private fun syncIsDue(nowNanos: Long, syncNanos: Long, sinceSyncMillis: Long): Boolean {
        return sinceSyncMillis >= syncIntervalMillis && syncNanosRef.compareAndSet(syncNanos, nowNanos)
    }

    /**
     * Syncs our internal clock with the system clock and returns the new time.
     * Marks the current time as the last synchronization point, unless another thread already did a synchronization in the meantime.
     */
    private fun sync(syncMillis: Long): Long {
        val newSyncMillis = advance(timeSource.currentTimeMillis())
        syncMillisRef.compareAndSet(syncMillis, newSyncMillis)
        return newSyncMillis
    }

    /**
     * Advance the clock to the given timestamp and return the new time.
     * The returned time may not be the one passed in, in case another thread already advanced the clock further.
     * This ensures that all threads share a consistent time.
     */
    private fun advance(newTime: Long): Long {
        while (true) {
            val current = currentTime.get()
            if (newTime <= current) {
                return current
            } else if (currentTime.compareAndSet(current, newTime)) {
                return newTime
            }
        }
    }

    companion object {
        private val SYNC_INTERVAL_MILLIS = TimeUnit.SECONDS.toMillis(3)
    }
}
