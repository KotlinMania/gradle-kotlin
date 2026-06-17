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

/**
 * Instruments for observing time.
 */
object Time {
    private val CLOCK: Clock = MonotonicClock()

    /**
     * A clock that is guaranteed not to go backwards.
     *
     * This should generally be used by Gradle processes instead of System.currentTimeMillis().
     * For the gory details, see [MonotonicClock].
     *
     * For timing activities, where correlation with the current time is not required, use [.startTimer].
     */
    @JvmStatic
    fun clock(): Clock {
        return CLOCK
    }

    /**
     * Replacement for System.currentTimeMillis(), based on [.clock].
     */
    @JvmStatic
    fun currentTimeMillis(): Long {
        return CLOCK.currentTime
    }

    /**
     * Measures elapsed time.
     *
     * Timers use System.nanoTime() to measure elapsed time,
     * and are therefore not synchronized with [.clock] or the system wall clock.
     *
     * System.nanoTime() does not consider time elapsed while the system is in hibernation.
     * Therefore, timers effectively measure the elapsed time, of which the system was awake.
     */
    @JvmStatic
    fun startTimer(): Timer {
        return DefaultTimer(TimeSource.Companion.SYSTEM)
    }

    @JvmStatic
    fun startCountdownTimer(timeoutMillis: Long): CountdownTimer {
        return DefaultCountdownTimer(TimeSource.Companion.SYSTEM, timeoutMillis, TimeUnit.MILLISECONDS)
    }

    @JvmStatic
    fun startCountdownTimer(timeout: Long, unit: TimeUnit): CountdownTimer {
        return DefaultCountdownTimer(TimeSource.Companion.SYSTEM, timeout, unit)
    }
}
