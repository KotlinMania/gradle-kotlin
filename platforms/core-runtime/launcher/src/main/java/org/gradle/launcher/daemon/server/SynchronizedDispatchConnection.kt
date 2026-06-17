/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.launcher.daemon.server

import org.gradle.internal.concurrent.Stoppable
import org.gradle.internal.dispatch.Receive
import org.gradle.internal.remote.internal.RemoteConnection
import org.gradle.launcher.daemon.protocol.OutputMessage
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

/**
 * Connection decorator that synchronizes dispatching and always flushes after each message.
 *
 * The plan is to replace this with a Connection implementation that queues outgoing messages and dispatches them from a worker thread.
 */
class SynchronizedDispatchConnection<T>(private val delegate: RemoteConnection<T?>) : Receive<T?>, Stoppable {
    private val lock: Lock = ReentrantLock()
    private var dispatching = false

    fun dispatchAndFlush(message: T?) {
        if (message !is OutputMessage) {
            LOGGER.debug("thread {}: dispatching {}", Thread.currentThread().getId(), message)
        }
        lock.lock()
        try {
            check(!dispatching) { "This thread is already dispatching a message." }
            dispatching = true
            try {
                delegate.dispatch(message)
                delegate.flush()
            } finally {
                dispatching = false
            }
        } finally {
            lock.unlock()
        }
    }

    override fun receive(): T? {
        //in case one wants to synchronize this method,
        //bear in mind that it is blocking so it cannot share the same lock as others
        val result = delegate.receive()
        LOGGER.debug("thread {}: received {}", Thread.currentThread().getId(), if (result == null) "null" else result.javaClass)
        return result
    }

    override fun stop() {
        LOGGER.debug("thread {}: stopping connection", Thread.currentThread().getId())
        delegate.stop()
    }

    override fun toString(): String {
        return delegate.toString()
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(SynchronizedDispatchConnection::class.java)
    }
}
