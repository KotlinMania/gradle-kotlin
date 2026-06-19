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
package org.gradle.internal.dispatch

import org.gradle.internal.concurrent.Stoppable
import org.slf4j.Logger

class ExceptionTrackingFailureHandler(private val logger: Logger) : DispatchFailureHandler<Any?>, Stoppable {
    private var failure: DispatchException? = null

    override fun dispatchFailed(message: Any?, failure: Throwable) {
        if (this.failure != null && !Thread.currentThread().isInterrupted()) {
            logger.error(failure.message, failure)
        } else {
            this.failure = DispatchException(String.format("Could not dispatch message %s.", message), failure)
        }
    }

    @Throws(DispatchException::class)
    override fun stop() {
        if (failure != null) {
            try {
                throw failure!!
            } finally {
                failure = null
            }
        }
    }
}
