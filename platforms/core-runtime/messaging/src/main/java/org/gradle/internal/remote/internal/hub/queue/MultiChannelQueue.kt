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
package org.gradle.internal.remote.internal.hub.queue

import org.gradle.internal.remote.internal.hub.protocol.ChannelIdentifier
import org.gradle.internal.remote.internal.hub.protocol.InterHubMessage
import org.gradle.internal.remote.internal.hub.protocol.Routable
import java.util.concurrent.locks.Lock

open class MultiChannelQueue(private val lock: Lock) {
    private val channels: MutableMap<ChannelIdentifier?, MultiEndPointQueue> = HashMap<ChannelIdentifier?, MultiEndPointQueue>()
    private val initializer = QueueInitializer()

    fun getChannel(channel: ChannelIdentifier?): MultiEndPointQueue {
        var queue = channels.get(channel)
        if (queue == null) {
            queue = MultiEndPointQueue(lock)
            channels.put(channel, queue)
            initializer.onQueueAdded(queue)
        }
        return queue
    }

    fun queue(message: InterHubMessage) {
        if (message.getDelivery() === InterHubMessage.Delivery.Stateful) {
            initializer.onStatefulMessage(message)
        }
        if (message is Routable) {
            val routableMessage = message as Routable
            getChannel(routableMessage.getChannel()).dispatch(message)
        } else if (message.getDelivery() === InterHubMessage.Delivery.Stateful || message.getDelivery() === InterHubMessage.Delivery.AllHandlers) {
            for (queue in channels.values) {
                queue.dispatch(message)
            }
        } else {
            throw IllegalArgumentException(String.format("Don't know how to route message %s.", message))
        }
    }
}
