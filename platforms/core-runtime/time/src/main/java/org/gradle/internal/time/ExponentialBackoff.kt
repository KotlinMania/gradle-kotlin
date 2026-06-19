/*
 * Copyright 2019 the original author or authors.
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

import java.io.IOException
import java.util.Random
import java.util.concurrent.TimeUnit
import kotlin.math.min

class ExponentialBackoff<S : ExponentialBackoff.Signal?> private constructor(private val timeoutMs: Int, @JvmField val signal: S?, private val slotTime: Long) {
    private val random = Random()

    private var timer: CountdownTimer? = null

    init {
        restartTimer()
    }

    fun restartTimer() {
        timer = Time.startCountdownTimer(timeoutMs.toLong())
    }

    /**
     * Retries the given query until it returns a 'sucessful' result.
     *
     * @param query which returns non-null value when successful.
     * @param <T> the result type.
     * @return the last value returned by the query.
     * @throws IOException thrown by the query.
     * @throws InterruptedException if interrupted while waiting.
    </T> */
    @Throws(IOException::class, InterruptedException::class)
    fun <T> retryUntil(query: Query<T?>): T? {
        var iteration = 0
        var result: Result<T?>?
        while (!(query.run().also { result = it })!!.isSuccessful) {
            if (timer!!.hasExpired()) {
                break
            }
            val signaled = signal!!.await(backoffPeriodFor(++iteration))
            if (signaled) {
                iteration = 0
            }
        }
        return result!!.value
    }

    fun backoffPeriodFor(iteration: Int): Long {
        // The +1 ensures every retry waits at least one slot. Without it, iteration 1 would
        // produce a 0ms backoff (since nextInt(1) is always 0), turning the first retry into a
        // tight loop. Standard exponential-backoff guidance is to always sleep at least one slot.
        return (random.nextInt(min(iteration, CAP_FACTOR)) + 1) * slotTime
    }

    fun getTimer(): CountdownTimer {
        return timer!!
    }

    interface Signal {
        @Throws(InterruptedException::class)
        fun await(period: Long): Boolean

        companion object {
            val SLEEP: Signal = object : Signal {
                @Throws(InterruptedException::class)
                override fun await(period: Long): Boolean {
                    Thread.sleep(period)
                    return false
                }
            }
        }
    }

    interface Query<T> {
        @Throws(IOException::class, InterruptedException::class)
        fun run(): Result<T?>?
    }

    abstract class Result<T> {
        abstract val isSuccessful: Boolean

        abstract val value: T?

        companion object {
            /**
             * Creates a result that indicates that the operation was successful and should not be repeated.
             */
            @JvmStatic
            fun <T> successful(value: T?): Result<T?> {
                requireNotNull(value)
                return object : Result<T?>() {
                    private val result = value
                    override val isSuccessful: Boolean
                        get() = true

                    override val value: T?
                        get() = result
                }
            }

            /**
             * Creates a result that indicates that the operation was not successful and should be repeated.
             */
            @JvmStatic
            fun <T> notSuccessful(value: T?): Result<T?> {
                requireNotNull(value)
                return object : Result<T?>() {
                    private val result = value
                    override val isSuccessful: Boolean
                        get() = false

                    override val value: T?
                        get() = result
                }
            }
        }
    }

    companion object {
        private const val CAP_FACTOR = 100
        private const val SLOT_TIME: Long = 25

        @JvmStatic
        fun of(amount: Int, unit: TimeUnit): ExponentialBackoff<Signal> {
            return of<Signal>(amount, unit, Signal.Companion.SLEEP)
        }

        @JvmStatic
        fun <T : Signal?> of(amount: Int, unit: TimeUnit, signal: T): ExponentialBackoff<T> {
            return ExponentialBackoff(TimeUnit.MILLISECONDS.convert(amount.toLong(), unit).toInt(), signal, SLOT_TIME)
        }

        @JvmStatic
        fun of(amount: Int, unit: TimeUnit, slotTime: Int, slotTimeUnit: TimeUnit): ExponentialBackoff<Signal> {
            return ExponentialBackoff<Signal>(TimeUnit.MILLISECONDS.convert(amount.toLong(), unit).toInt(), Signal.Companion.SLEEP, TimeUnit.MILLISECONDS.convert(slotTime.toLong(), slotTimeUnit))
        }
    }
}
