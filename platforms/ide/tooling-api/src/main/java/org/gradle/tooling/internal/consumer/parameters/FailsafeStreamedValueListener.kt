/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.tooling.internal.consumer.parameters

import org.gradle.internal.event.ListenerNotificationException
import org.gradle.tooling.StreamedValueListener

class FailsafeStreamedValueListener(private val delegate: StreamedValueListener?) : StreamedValueListener {
    private var failure: RuntimeException? = null

    override fun onValue(value: Any) {
        if (failure != null) {
            // Stop handling further values after a failure
            return
        }

        if (delegate != null) {
            try {
                delegate.onValue(value)
            } catch (e: Throwable) {
                failure = ListenerNotificationException(null, "Streaming model listener failed with an exception.", mutableListOf<Throwable>(e))
            }
        } else {
            failure = IllegalStateException("No streaming model listener registered.")
        }
    }

    fun rethrowErrors() {
        if (failure != null) {
            throw failure
        }
    }
}
