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

import org.gradle.api.Action
import org.gradle.internal.Cast
import org.gradle.internal.concurrent.AsyncStoppable
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.concurrent.ManagedExecutor
import org.gradle.internal.dispatch.BoundedDispatch
import org.gradle.internal.dispatch.Dispatch
import org.gradle.internal.remote.internal.Connection
import org.gradle.internal.remote.internal.RecoverableMessageIOException
import org.gradle.internal.remote.internal.RemoteConnection
import org.gradle.internal.remote.internal.hub.protocol.ChannelIdentifier
import org.gradle.internal.remote.internal.hub.protocol.ChannelMessage
import org.gradle.internal.remote.internal.hub.protocol.EndOfStream
import org.gradle.internal.remote.internal.hub.protocol.InterHubMessage
import org.gradle.internal.remote.internal.hub.protocol.RejectedMessage
import org.gradle.internal.remote.internal.hub.protocol.StreamFailureMessage
import org.gradle.internal.remote.internal.hub.queue.EndPointQueue
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

/**
 * A multi-channel message router.
 *
 * Use [.getOutgoing] to create a [Dispatch] to send unicast messages on a given channel.
 * Use [.addHandler] to create a worker for incoming messages on a given channel.
 * Use [.addConnection] to attach another router to this router.
 *
 * TODO - this type could be simplified, as there is no longer any need to send/receive messages to/from multiple connections
 */
class MessageHub(private val displayName: String?, executorFactory: ExecutorFactory, private val errorHandler: Action<in Throwable?>) : AsyncStoppable {
    private enum class State {
        Running, Stopping, Stopped
    }

    private val workers: ManagedExecutor
    private val lock: Lock = ReentrantLock()
    private var state = State.Running
    private val incomingQueue = IncomingQueue(lock)
    private val outgoingQueue = OutgoingQueue(incomingQueue, lock)
    private val connections = ConnectionSet(incomingQueue, outgoingQueue)

    /**
     * @param errorHandler Notified when some async activity fails. Must be thread-safe.
     */
    init {
        workers = executorFactory.create(displayName + " workers")
    }

    /**
     *
     * Adds a [Dispatch] implementation that can be used to send outgoing unicast messages on the given channel. Messages are queued in the order that they are
     * dispatched, and are forwarded to at most one handler.
     *
     *
     * The returned value is thread-safe.
     */
    fun <T> getOutgoing(channelName: String, type: Class<T>): Dispatch<T?> {
        lock.lock()
        try {
            assertRunning("create outgoing dispatch")
            return ChannelDispatch(type, ChannelIdentifier(channelName))
        } finally {
            lock.unlock()
        }
    }

    /**
     * Adds a handler for messages on the given channel. The handler may implement any of the following:
     *
     *
     *
     *  * [Dispatch] to handle incoming messages received from any connections attached to this hub. Each incoming message is passed to exactly one handler associated to the given channel.
     *
     *
     *  * [RejectedMessageListener] to receive notifications of outgoing messages that cannot be sent on the given channel.
     *
     *  * [BoundedDispatch] to receive notifications of the end of incoming messages.
     *
     *
     *
     *
     * The given handler does not need to be thread-safe, and is notified by at most one thread at a time. Multiple handlers can be added for a given channel.
     *
     *
     * NOTE: If any method of the handler fails with an exception, the handler is discarded and will receive no further notifications.
     */
    fun addHandler(channelName: String, handler: Any?) {
        lock.lock()
        try {
            assertRunning("add handler")

            val rejectedMessageListener: RejectedMessageListener?
            if (handler is RejectedMessageListener) {
                rejectedMessageListener = handler
            } else {
                rejectedMessageListener = DISCARD
            }
            val dispatch: Dispatch<Any?>
            if (handler is Dispatch<*>) {
                @Suppress("UNCHECKED_CAST")
                dispatch = handler as Dispatch<Any?>
            } else {
                dispatch = DISCARD
            }
            val boundedDispatch: BoundedDispatch<Any?>
            if (dispatch is BoundedDispatch<*>) {
                @Suppress("UNCHECKED_CAST")
                boundedDispatch = dispatch as BoundedDispatch<Any?>
            } else {
                boundedDispatch = DISCARD
            }
            val streamFailureHandler: StreamFailureHandler?
            if (handler is StreamFailureHandler) {
                streamFailureHandler = handler
            } else {
                streamFailureHandler = DISCARD
            }
            val identifier = ChannelIdentifier(channelName)
            val queue = incomingQueue.getChannel(identifier).newEndpoint()
            workers.execute(Handler(queue, dispatch, boundedDispatch, rejectedMessageListener, streamFailureHandler))
        } finally {
            lock.unlock()
        }
    }

    /**
     * Adds a connection to some other message hub. Outgoing messages are forwarded to this connection, and incoming messages are received from it.
     *
     *
     * Does not cleanup connections on stop or disconnect. It is the caller's responsibility to manage the connection lifecycle.
     */
    fun addConnection(connection: RemoteConnection<InterHubMessage?>) {
        lock.lock()
        try {
            assertRunning("add connection")
            val connectionState = connections.add(connection)
            workers.execute(ConnectionDispatch(connectionState))
            workers.execute(ConnectionReceive(connectionState))
        } finally {
            lock.unlock()
        }
    }

    /**
     * Signals that no further connections will be added.
     */
    fun noFurtherConnections() {
        lock.lock()
        try {
            connections.noFurtherConnections()
        } finally {
            lock.unlock()
        }
    }

    private fun assertRunning(action: String?) {
        check(state == State.Running) { String.format("Cannot %s, as %s has been stopped.", action, displayName) }
    }

    /**
     * Requests that this message hub commence shutting down. Does the following:
     *
     *
     *
     *  * Stops accepting any further outgoing messages.
     *
     *  * If no connections are available, dispatches queued messages to any handlers that implement [RejectedMessageListener].
     *
     *
     */
    override fun requestStop() {
        lock.lock()
        try {
            if (state != State.Running) {
                return
            }
            try {
                outgoingQueue.endOutput()
                connections.noFurtherConnections()
            } finally {
                state = State.Stopping
            }
        } finally {
            lock.unlock()
        }
    }

    /**
     * Requests that this message hub stop. First requests stop as per [.requestStop], then blocks until stop has completed. This means that:
     *
     *
     *
     *  * All calls to [Dispatch.dispatch] for outgoing messages have returned.
     *
     *  * All dispatches to handlers have completed.
     *
     *  * All internal threads have completed.
     *
     *
     */
    override fun stop() {
        lock.lock()
        try {
            try {
                requestStop()
            } finally {
                lock.unlock()
            }
            workers.stop()
        } finally {
            lock.lock()
            try {
                state = State.Stopped
            } finally {
                lock.unlock()
            }
        }
    }

    private class Discard : BoundedDispatch<Any?>, RejectedMessageListener, StreamFailureHandler {
        override fun dispatch(message: Any?) {
        }

        override fun endStream() {
        }

        override fun messageDiscarded(message: Any?) {
        }

        override fun handleStreamFailure(t: Throwable?) {
        }
    }

    private inner class ConnectionReceive(private val connectionState: ConnectionState) : Runnable {
        private val connection: Connection<InterHubMessage?>

        init {
            this.connection = connectionState.connection
        }

        override fun run() {
            try {
                try {
                    while (true) {
                        val message: InterHubMessage?
                        try {
                            message = connection.receive()
                        } catch (e: RecoverableMessageIOException) {
                            addToIncoming(StreamFailureMessage(e))
                            continue
                        }
                        if (message == null || message is EndOfStream) {
                            return
                        }
                        addToIncoming(message)
                    }
                } finally {
                    lock.lock()
                    try {
                        connectionState.receiveFinished()
                    } finally {
                        lock.unlock()
                    }
                }
            } catch (e: Throwable) {
                errorHandler.execute(e)
            }
        }
    }

    private fun addToIncoming(message: InterHubMessage) {
        lock.lock()
        try {
            incomingQueue.queue(message)
        } finally {
            lock.unlock()
        }
    }

    private inner class ConnectionDispatch(private val connectionState: ConnectionState) : Runnable {
        private val connection: RemoteConnection<InterHubMessage?>
        private val queue: EndPointQueue

        init {
            this.connection = connectionState.connection
            this.queue = connectionState.dispatchQueue
        }

        override fun run() {
            try {
                val messages: MutableList<InterHubMessage?> = ArrayList<InterHubMessage?>()
                try {
                    while (true) {
                        lock.lock()
                        try {
                            queue.take(messages)
                        } finally {
                            lock.unlock()
                        }
                        for (message in messages) {
                            try {
                                connection.dispatch(message)
                            } catch (e: RecoverableMessageIOException) {
                                addToIncoming(StreamFailureMessage(e))
                            }
                            if (message is EndOfStream) {
                                connection.flush()
                                return
                            }
                        }
                        connection.flush()
                        messages.clear()
                    }
                } finally {
                    lock.lock()
                    try {
                        connectionState.dispatchFinished()
                    } finally {
                        lock.unlock()
                    }
                }
            } catch (t: Throwable) {
                errorHandler.execute(t)
            }
        }
    }

    private inner class ChannelDispatch<T>(private val type: Class<T>, private val channelIdentifier: ChannelIdentifier?) : Dispatch<T?> {
        override fun toString(): String {
            return "Dispatch " + type.getSimpleName() + " to " + displayName + " channel " + channelIdentifier
        }

        override fun dispatch(message: T?) {
            lock.lock()
            try {
                assertRunning("dispatch message")
                outgoingQueue.dispatch(ChannelMessage(channelIdentifier, message))
            } finally {
                lock.unlock()
            }
        }
    }

    private inner class Handler(
        private val queue: EndPointQueue,
        private val dispatch: Dispatch<Any?>,
        private val boundedDispatch: BoundedDispatch<Any?>,
        private val listener: RejectedMessageListener,
        private val streamFailureHandler: StreamFailureHandler
    ) : Runnable {
        override fun run() {
            try {
                val messages: MutableList<InterHubMessage?> = ArrayList<InterHubMessage?>()
                try {
                    while (true) {
                        lock.lock()
                        try {
                            queue.take(messages)
                        } finally {
                            lock.unlock()
                        }
                        for (message in messages) {
                            if (message is EndOfStream) {
                                boundedDispatch.endStream()
                                return
                            }
                            if (message is ChannelMessage) {
                                val channelMessage = message
                                dispatch.dispatch(channelMessage.payload)
                            } else if (message is RejectedMessage) {
                                val rejectedMessage = message
                                listener.messageDiscarded(rejectedMessage.payload)
                            } else if (message is StreamFailureMessage) {
                                val streamFailureMessage = message
                                streamFailureHandler.handleStreamFailure(streamFailureMessage.failure)
                            } else {
                                throw IllegalArgumentException(String.format("Don't know how to handle message %s", message))
                            }
                        }
                        messages.clear()
                    }
                } finally {
                    lock.lock()
                    try {
                        queue.stop()
                    } finally {
                        lock.unlock()
                    }
                }
            } catch (t: Throwable) {
                errorHandler.execute(t)
            }
        }
    }

    companion object {
        private val DISCARD = Discard()
    }
}
