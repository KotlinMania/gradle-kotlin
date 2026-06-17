/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.tooling.internal.provider.continuous

import org.gradle.deployment.internal.ContinuousExecutionGate
import org.gradle.initialization.BuildCancellationToken
import org.gradle.internal.UncheckedException
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.Volatile

class ContinuousBuildTriggerHandler(
    private val cancellationToken: BuildCancellationToken,
    private val continuousExecutionGate: ContinuousExecutionGate,
    private val quietPeriod: Duration
) {
    private val changeOrCancellationArrived = CountDownLatch(1)
    private val cancellationArrived = CountDownLatch(1)

    @Volatile
    private var lastChangeAt: Instant = nowFromMonotonicClock()

    @Volatile
    private var changeArrived = false

    fun wait(notifier: Runnable) {
        val cancellationHandler = Runnable {
            changeOrCancellationArrived.countDown()
            cancellationArrived.countDown()
        }
        if (cancellationToken.isCancellationRequested()) {
            return
        }
        try {
            cancellationToken.addCallback(cancellationHandler)
            notifier.run()
            changeOrCancellationArrived.await()
            while (!cancellationToken.isCancellationRequested()) {
                val now: Instant = nowFromMonotonicClock()
                val endOfQuietPeriod = lastChangeAt.plus(quietPeriod)
                if (!endOfQuietPeriod.isAfter(now)) {
                    break
                }
                val remainingQuietPeriod = Duration.between(now, endOfQuietPeriod)
                cancellationArrived.await(remainingQuietPeriod.toMillis(), TimeUnit.MILLISECONDS)
            }
            if (!cancellationToken.isCancellationRequested()) {
                continuousExecutionGate.waitForOpen()
            }
        } catch (e: InterruptedException) {
            throw UncheckedException.throwAsUncheckedException(e)
        } finally {
            cancellationToken.removeCallback(cancellationHandler)
        }
    }

    fun hasBeenTriggered(): Boolean {
        return changeArrived
    }

    fun notifyFileChangeArrived() {
        changeArrived = true
        lastChangeAt = nowFromMonotonicClock()
        changeOrCancellationArrived.countDown()
    }

    companion object {
        private fun nowFromMonotonicClock(): Instant {
            return Instant.ofEpochMilli(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()))
        }
    }
}
