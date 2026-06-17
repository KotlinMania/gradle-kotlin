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

import org.gradle.internal.remote.internal.hub.protocol.ChannelMessage
import org.gradle.internal.remote.internal.hub.protocol.EndOfStream
import org.gradle.internal.remote.internal.hub.protocol.InterHubMessage
import org.gradle.internal.remote.internal.hub.protocol.RejectedMessage
import org.gradle.internal.remote.internal.hub.queue.MultiEndPointQueue
import java.util.concurrent.locks.Lock

internal class OutgoingQueue(private val incomingQueue: IncomingQueue, lock: Lock) : MultiEndPointQueue(lock) {
    fun endOutput() {
        dispatch(EndOfStream())
    }

    fun discardQueued() {
        val rejected: MutableList<InterHubMessage?> = ArrayList<InterHubMessage?>()
        drain(rejected)
        for (message in rejected) {
            if (message is ChannelMessage) {
                val channelMessage = message
                incomingQueue.queue(RejectedMessage(channelMessage.getChannel(), channelMessage.payload))
            }
        }
    }
}
