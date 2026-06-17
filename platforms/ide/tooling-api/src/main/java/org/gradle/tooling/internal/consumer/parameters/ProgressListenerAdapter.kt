/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.internal.event.ListenerBroadcast
import org.gradle.tooling.ProgressEvent
import org.gradle.tooling.ProgressListener
import org.gradle.tooling.internal.protocol.ProgressListenerVersion1
import java.util.LinkedList

internal class ProgressListenerAdapter(listeners: MutableList<ProgressListener>) : ProgressListenerVersion1 {
    private val listeners = ListenerBroadcast<ProgressListener?>(ProgressListener::class.java)
    private val stack = LinkedList<String>()

    init {
        this.listeners.addAll(listeners)
    }

    override fun onOperationStart(description: String) {
        stack.addFirst(if (description == null) "" else description)
        fireChangeEvent()
    }

    override fun onOperationEnd() {
        stack.removeFirst()
        fireChangeEvent()
    }

    private fun fireChangeEvent() {
        val description = if (stack.isEmpty()) "" else stack.getFirst()
        listeners.getSource()!!.statusChanged(object : ProgressEvent {
            override fun getDescription(): String {
                return description
            }
        })
    }
}
