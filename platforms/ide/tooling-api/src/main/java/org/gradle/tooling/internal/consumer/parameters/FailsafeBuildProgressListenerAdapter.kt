/*
 * Copyright 2015 the original author or authors.
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
import org.gradle.tooling.Failure
import org.gradle.tooling.internal.protocol.InternalBuildProgressListener

class FailsafeBuildProgressListenerAdapter(private val delegate: InternalBuildProgressListener) : InternalBuildProgressListener {
    private var listenerFailure: Throwable? = null

    override fun onEvent(event: Any?) {
        if (listenerFailure != null) {
            // Discard event
            return
        }
        try {
            delegate.onEvent(event)
        } catch (t: Throwable) {
            listenerFailure = t
        }
    }

    override val subscribedOperations: MutableList<String?>?
        get() = delegate.subscribedOperations

    fun rethrowErrors() {
        if (listenerFailure != null) {
            throw ListenerNotificationException(null, "One or more progress listeners failed with an exception.", mutableListOf(listenerFailure!!))
        }
    }
}
