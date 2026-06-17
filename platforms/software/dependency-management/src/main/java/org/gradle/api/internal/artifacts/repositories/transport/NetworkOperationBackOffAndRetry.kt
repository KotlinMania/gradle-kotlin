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
package org.gradle.api.internal.artifacts.repositories.transport

import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.Callable

class NetworkOperationBackOffAndRetry<T> @JvmOverloads constructor(
    private val maxDeployAttempts: Int = Integer.getInteger(MAX_ATTEMPTS, 3), private val initialBackOff: Int = Integer.getInteger(
        INITIAL_BACKOFF_MS, 1000
    )
) {
    init {
        assert(maxDeployAttempts > 0)
        assert(initialBackOff > 0)
    }

    fun withBackoffAndRetry(operation: Callable<T?>): T? {
        var backoff = initialBackOff
        var retries = 0
        var returnValue: T? = null
        while (retries < maxDeployAttempts) {
            retries++
            val failure: Throwable?
            try {
                returnValue = operation.call()
                if (retries > 1) {
                    LOGGER.info("Successfully ran '{}' after {} retries", operation, retries - 1)
                }
                break
            } catch (throwable: Exception) {
                failure = throwable
            }
            if (!NetworkingIssueVerifier.isLikelyTransientNetworkingIssue<Throwable?>(failure) || retries == maxDeployAttempts) {
                throw throwAsUncheckedException(failure)
            } else {
                LOGGER.info("Error in '{}'. Waiting {}ms before next retry, {} retries left", operation, backoff, maxDeployAttempts - retries)
                LOGGER.debug("Network operation failed", failure)
                try {
                    Thread.sleep(backoff.toLong())
                    backoff *= 2
                } catch (e: InterruptedException) {
                    throw throwAsUncheckedException(e)
                }
            }
        }
        return returnValue
    }

    companion object {
        private const val MAX_ATTEMPTS = "org.gradle.internal.network.retry.max.attempts"
        private const val INITIAL_BACKOFF_MS = "org.gradle.internal.network.retry.initial.backOff"

        private val LOGGER: Logger = LoggerFactory.getLogger(NetworkOperationBackOffAndRetry::class.java)
    }
}
