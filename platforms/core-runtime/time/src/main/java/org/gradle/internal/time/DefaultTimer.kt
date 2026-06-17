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
import kotlin.math.max

internal open class DefaultTimer(private val timeSource: TimeSource) : Timer {
    private var startTime: Long = 0

    init {
        reset()
    }

    override val elapsed: String
        get() {
        return TimeFormatting.formatDurationVerbose(elapsedMillis)
    }

    override val elapsedMillis: Long
        get() {
        val elapsedNanos = timeSource.nanoTime() - startTime
        val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(elapsedNanos)

        // System.nanoTime() can go backwards under some circumstances.
        // http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6458294
        // This max() call ensures that we don't return negative durations.
        return max(elapsedMillis, 0)
    }

    override fun reset() {
        startTime = timeSource.nanoTime()
    }
}
