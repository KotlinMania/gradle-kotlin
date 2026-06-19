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
package org.gradle.internal.remote.internal.hub

import org.gradle.internal.remote.internal.RemoteConnection
import org.gradle.internal.remote.internal.hub.protocol.InterHubMessage
import org.gradle.internal.remote.internal.hub.queue.EndPointQueue

internal class ConnectionState(private val owner: ConnectionSet, val connection: RemoteConnection<InterHubMessage?>, val dispatchQueue: EndPointQueue) {
    private var receiveFinished = false
    private var dispatchFinished = false

    fun receiveFinished() {
        receiveFinished = true
        if (!dispatchFinished) {
            dispatchQueue.stop()
        }
        maybeDisconnected()
    }

    fun dispatchFinished() {
        dispatchFinished = true
        maybeDisconnected()
    }

    private fun maybeDisconnected() {
        if (dispatchFinished && receiveFinished) {
            owner.finished(this)
        }
    }
}
