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

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration

object TimeFormatting {
    private const val MILLIS_PER_SECOND = 1000
    private val MILLIS_PER_MINUTE = 60 * MILLIS_PER_SECOND
    private val MILLIS_PER_HOUR = 60 * MILLIS_PER_MINUTE
    private val MILLIS_PER_DAY = 24 * MILLIS_PER_HOUR

    @JvmStatic
    fun formatDurationVerbose(durationMillis: Long): String {
        val result = StringBuilder()
        if (durationMillis > MILLIS_PER_HOUR) {
            result.append(durationMillis / MILLIS_PER_HOUR).append(" hrs ")
        }
        if (durationMillis > MILLIS_PER_MINUTE.toLong()) {
            result.append((durationMillis % MILLIS_PER_HOUR) / MILLIS_PER_MINUTE).append(" mins ")
        }
        result.append((durationMillis % MILLIS_PER_MINUTE) / 1000.0).append(" secs")
        return result.toString()
    }

    @JvmStatic
    fun formatDurationTerse(elapsedTimeInMs: Long): String {
        val result = StringBuilder()
        if (elapsedTimeInMs >= MILLIS_PER_HOUR) {
            result.append(elapsedTimeInMs / MILLIS_PER_HOUR).append("h ")
        }
        if (elapsedTimeInMs >= MILLIS_PER_MINUTE) {
            val hours = (elapsedTimeInMs % MILLIS_PER_HOUR) / MILLIS_PER_MINUTE
            if (hours > 0) {
                result.append(hours).append("m ")
            }
        }
        if (elapsedTimeInMs >= MILLIS_PER_SECOND) {
            val seconds = (elapsedTimeInMs % MILLIS_PER_MINUTE) / 1000
            if (seconds > 0) {
                result.append(seconds).append("s")
            }
        } else {
            result.append(elapsedTimeInMs).append("ms")
        }
        return result.toString().trim { it <= ' ' }
    }

    @JvmStatic
    fun formatDurationVeryTerse(duration: Long): String {
        var duration = duration
        if (duration == 0L) {
            return "0s"
        }

        val result = StringBuilder()

        // Whereas it doesn't make sense to pass negative values to this method,
        // the duration passed on call sited is often a result of some math, what is not guarantees positive-values-only.
        // So let's make an output more predictable in accidental negative-values scenarios.
        if (duration < 0) {
            result.append("-")
            result.append(formatDurationVeryTerse(-duration))
            return result.toString()
        }

        // TODO Consider rewriting this using Duration methods once we move to Java 9+
        val days = duration / MILLIS_PER_DAY
        duration = duration % MILLIS_PER_DAY
        if (days > 0) {
            result.append(days)
            result.append("d")
        }
        val hours = duration / MILLIS_PER_HOUR
        duration = duration % MILLIS_PER_HOUR
        if (hours > 0 || result.length > 0) {
            result.append(hours)
            result.append("h")
        }
        val minutes = duration / MILLIS_PER_MINUTE
        duration = duration % MILLIS_PER_MINUTE
        if (minutes > 0 || result.length > 0) {
            result.append(minutes)
            result.append("m")
        }
        val secondsScale = if (result.length > 0) 2 else 3
        result.append(BigDecimal.valueOf(duration).divide(BigDecimal.valueOf(MILLIS_PER_SECOND.toLong())).setScale(secondsScale, RoundingMode.HALF_UP))
        result.append("s")
        return result.toString()
    }

    fun parseDurationVeryTerse(text: String): Duration {
        val dayAndRest = splitOrFirstEmpty(text, "d")
        val hourAndRest = splitOrFirstEmpty(dayAndRest[1], "h")
        val minuteAndRest = splitOrFirstEmpty(hourAndRest[1], "m")
        val secondAndRest = splitOrFirstEmpty(minuteAndRest[1], "s")
        val days = if (dayAndRest[0].isEmpty()) 0 else dayAndRest[0].toLong()
        val hours = if (hourAndRest[0].isEmpty()) 0 else hourAndRest[0].toLong()
        val minutes = if (minuteAndRest[0].isEmpty()) 0 else minuteAndRest[0].toLong()
        val seconds = if (secondAndRest[0].isEmpty()) BigDecimal.ZERO else BigDecimal(secondAndRest[0])
        return Duration.ofDays(days)
            .plusHours(hours)
            .plusMinutes(minutes)
            .plusMillis(seconds.multiply(BigDecimal.valueOf(MILLIS_PER_SECOND.toLong())).toLong())
    }

    private fun splitOrFirstEmpty(text: String, delimiter: String): Array<String> {
        val parts = text.split(delimiter.toRegex(), limit = 2).toTypedArray()
        if (parts.size == 1) {
            return arrayOf<String>("", text)
        }
        return parts
    }
}
