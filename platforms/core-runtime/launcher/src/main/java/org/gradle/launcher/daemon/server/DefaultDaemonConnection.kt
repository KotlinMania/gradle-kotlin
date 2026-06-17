/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.internal.UncheckedException
import org.gradle.internal.concurrent.CompositeStoppable
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.concurrent.ManagedExecutor
import org.gradle.internal.concurrent.Stoppable
import org.gradle.internal.daemon.clientinput.StdinHandler
import org.gradle.internal.logging.events.OutputEvent
import org.gradle.launcher.daemon.protocol.BuildEvent
import org.gradle.launcher.daemon.protocol.BuildStarted
import org.gradle.launcher.daemon.protocol.Cancel
import org.gradle.launcher.daemon.protocol.CloseInput
import org.gradle.launcher.daemon.protocol.DaemonUnavailable
import org.gradle.launcher.daemon.protocol.ForwardInput
import org.gradle.launcher.daemon.protocol.InputMessage
import org.gradle.launcher.daemon.protocol.Message
import org.gradle.launcher.daemon.protocol.OutputMessage
import org.gradle.launcher.daemon.protocol.Result
import org.gradle.launcher.daemon.protocol.UserResponse
import org.gradle.launcher.daemon.server.api.DaemonConnection
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.LinkedList
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.Volatile

class DefaultDaemonConnection(private val connection: SynchronizedDispatchConnection<Message>, executorFactory: ExecutorFactory) : DaemonConnection {
    private val executor: ManagedExecutor
    private val stdinQueue: StdinQueue
    private val disconnectQueue: DisconnectQueue
    private val cancelQueue: CancelQueue
    private val receiveQueue: ReceiveQueue

    @Volatile
    private var stopping = false

    init {
        stdinQueue = StdinQueue(executorFactory)
        disconnectQueue = DisconnectQueue()
        cancelQueue = CancelQueue(executorFactory)
        receiveQueue = ReceiveQueue()
        executor = executorFactory.create("Handler for " + connection.toString())
        executor.execute(object : Runnable {
            override fun run() {
                var failure: Throwable? = null
                try {
                    while (true) {
                        val message: Any?
                        try {
                            message = connection.receive()
                        } catch (e: Exception) {
                            if (!stopping && LOGGER.isDebugEnabled()) {
                                LOGGER.debug(String.format("thread %s: Could not receive message from client.", Thread.currentThread().getId()), e)
                            }
                            failure = e
                            return
                        }
                        if (message == null) {
                            LOGGER.debug("thread {}: Received end-of-input from client.", Thread.currentThread().getId())
                            return
                        }

                        if (message is InputMessage) {
                            LOGGER.debug("thread {}: Received IO message from client: {}", Thread.currentThread().getId(), message)
                            stdinQueue.add(message)
                        } else if (message is Cancel) {
                            LOGGER.debug("thread {}: Received cancel message from client: {}", Thread.currentThread().getId(), message)
                            cancelQueue.add(message)
                        } else {
                            LOGGER.debug("thread {}: Received non-IO message from client: {}", Thread.currentThread().getId(), message)
                            receiveQueue.add(message)
                        }
                    }
                } finally {
                    stdinQueue.disconnect()
                    cancelQueue.disconnect()
                    disconnectQueue.disconnect()
                    receiveQueue.disconnect(failure!!)
                }
            }
        })
    }

    override fun onStdin(handler: StdinHandler) {
        stdinQueue.useHandler(handler)
    }

    override fun onDisconnect(handler: Runnable) {
        disconnectQueue.useHandler(handler)
    }

    override fun onCancel(handler: Runnable) {
        cancelQueue.useHandler(handler)
    }

    override fun receive(timeoutValue: Long, timeoutUnits: TimeUnit): Any {
        return receiveQueue.take(timeoutValue, timeoutUnits)
    }

    override fun daemonUnavailable(unavailable: DaemonUnavailable) {
        connection.dispatchAndFlush(unavailable)
    }

    override fun buildStarted(buildStarted: BuildStarted) {
        connection.dispatchAndFlush(buildStarted)
    }

    override fun logEvent(logEvent: OutputEvent) {
        connection.dispatchAndFlush(OutputMessage(logEvent))
    }

    override fun event(event: Any) {
        connection.dispatchAndFlush(BuildEvent(event))
    }

    override fun completed(result: Result<*>) {
        connection.dispatchAndFlush(result)
    }

    override fun stop() {
        stopping = true

        // 1. Stop handling disconnects. Blocks until the handler has finished.
        // 2. Stop the connection. This means that the thread receiving from the connection will receive a null and finish up.
        // 3. Stop receiving incoming messages. Blocks until the receive thread has finished. This will notify the stdin and receive queues to signal end of input.
        // 4. Stop the receive queue, to unblock any threads blocked in receive().
        // 5. Stop handling stdin. Blocks until the handler has finished. Discards any queued input.
        CompositeStoppable.stoppable(disconnectQueue, connection, executor, receiveQueue, stdinQueue, cancelQueue).stop()
    }

    override fun toString(): String {
        return "DefaultDaemonConnection: " + connection
    }

    private abstract class CommandQueue<C : Message?, H>(private val executorFactory: ExecutorFactory, private val name: String) : Stoppable {
        private val lock: Lock = ReentrantLock()
        private val condition: Condition = lock.newCondition()
        protected val queue: LinkedList<C?> = LinkedList<C?>()
        private var executor: ManagedExecutor? = null
        private var removed = false

        override fun stop() {
            var executor: ManagedExecutor?
            lock.lock()
            try {
                executor = this.executor
            } finally {
                lock.unlock()
            }
            if (executor != null) {
                executor.stop()
            }
        }

        fun add(command: C?) {
            lock.lock()
            try {
                queue.add(command)
                condition.signalAll()
            } finally {
                lock.unlock()
            }
        }

        fun useHandler(handler: H?) {
            if (handler != null) {
                startConsuming(handler)
            } else {
                stopConsuming()
            }
        }

        protected fun stopConsuming() {
            var executor: ManagedExecutor?
            lock.lock()
            try {
                queue.clear()
                removed = true
                condition.signalAll()
                executor = this.executor
            } finally {
                lock.unlock()
            }
            if (executor != null) {
                executor.stop()
            }
        }

        protected fun startConsuming(handler: H?) {
            lock.lock()
            try {
                if (executor != null) {
                    throw UnsupportedOperationException("More instances of " + name + " not supported.")
                }
                executor = executorFactory.create(name)
                executor!!.execute(object : Runnable {
                    override fun run() {
                        while (true) {
                            var command: C?
                            lock.lock()
                            try {
                                while (!removed && queue.isEmpty()) {
                                    try {
                                        condition.await()
                                    } catch (e: InterruptedException) {
                                        throw UncheckedException.throwAsUncheckedException(e)
                                    }
                                }
                                if (removed) {
                                    return
                                }
                                command = queue.removeFirst()
                            } finally {
                                lock.unlock()
                            }
                            if (doHandleCommand(handler, command)) {
                                return
                            }
                        }
                    }
                })
            } finally {
                lock.unlock()
            }
        }

        /**
         * @return true if the queue should stop processing.
         */
        protected abstract fun doHandleCommand(handler: H?, command: C?): Boolean

        // Called under lock
        protected abstract fun doHandleDisconnect()

        fun disconnect() {
            lock.lock()
            try {
                doHandleDisconnect()
                condition.signalAll()
            } finally {
                lock.unlock()
            }
        }
    }

    private class StdinQueue(executorFactory: ExecutorFactory) : CommandQueue<InputMessage, StdinHandler>(executorFactory, "Stdin handler") {
        override fun doHandleCommand(handler: StdinHandler, command: InputMessage): Boolean {
            try {
                if (command is CloseInput) {
                    handler.onEndOfInput()
                    return true
                } else if (command is UserResponse) {
                    handler.onUserResponse(command)
                } else {
                    handler.onInput(command as ForwardInput)
                }
            } catch (e: Exception) {
                LOGGER.warn("Could not forward client stdin.", e)
                return true
            }
            return false
        }

        override fun doHandleDisconnect() {
            queue.clear()
            queue.add(CloseInput())
        }
    }

    private class CancelQueue(executorFactory: ExecutorFactory) : CommandQueue<Cancel, Runnable>(executorFactory, "Cancel handler") {
        override fun doHandleCommand(handler: Runnable, command: Cancel): Boolean {
            try {
                handler.run()
            } catch (e: Exception) {
                LOGGER.warn("Could not process cancel request from client.", e)
            }
            return true
        }

        override fun doHandleDisconnect() {
            queue.clear()
        }
    }

    private class DisconnectQueue : Stoppable {
        private val lock: Lock = ReentrantLock()
        private val condition: Condition = lock.newCondition()
        private var handler: Runnable? = null
        private var notifying = false
        private var disconnected = false

        fun disconnect() {
            var action: Runnable?
            lock.lock()
            try {
                disconnected = true
                if (handler == null) {
                    return
                }
                action = handler
                notifying = true
            } finally {
                lock.unlock()
            }
            runAction(action!!)
        }

        fun runAction(action: Runnable) {
            try {
                action.run()
            } catch (e: Exception) {
                LOGGER.warn("Failed to notify disconnect handler.", e)
            } finally {
                lock.lock()
                try {
                    notifying = false
                    condition.signalAll()
                } finally {
                    lock.unlock()
                }
            }
        }

        override fun stop() {
            useHandler(null)
        }

        fun useHandler(handler: Runnable) {
            if (handler != null) {
                startMonitoring(handler)
            } else {
                stopMonitoring()
            }
        }

        fun startMonitoring(handler: Runnable) {
            var action: Runnable

            lock.lock()
            try {
                if (this.handler != null) {
                    throw UnsupportedOperationException("Multiple disconnect handlers not supported.")
                }
                this.handler = handler
                if (!disconnected) {
                    return
                }
                action = handler
                notifying = true
            } finally {
                lock.unlock()
            }

            runAction(action)
        }

        fun stopMonitoring() {
            lock.lock()
            try {
                while (notifying) {
                    try {
                        condition.await()
                    } catch (e: InterruptedException) {
                        throw UncheckedException.throwAsUncheckedException(e)
                    }
                }
                handler = null
            } finally {
                lock.unlock()
            }
        }
    }

    private class ReceiveQueue : Stoppable {
        private val queue: BlockingQueue<Any> = LinkedBlockingQueue<Any>()

        override fun stop() {
        }

        fun disconnect(failure: Throwable) {
            queue.clear()
            if (failure != null) {
                add(failure)
            }
            add(END)
        }

        fun add(message: Any) {
            try {
                queue.put(message)
            } catch (e: InterruptedException) {
                throw UncheckedException.throwAsUncheckedException(e)
            }
        }

        fun take(timeoutValue: Long, timeoutUnits: TimeUnit): Any {
            val result: Any?
            try {
                result = queue.poll(timeoutValue, timeoutUnits)
            } catch (e: InterruptedException) {
                throw UncheckedException.throwAsUncheckedException(e)
            }
            if (result is Throwable) {
                val failure = result
                throw UncheckedException.throwAsUncheckedException(failure)
            }
            return (if (result === org.gradle.launcher.daemon.server.DefaultDaemonConnection.ReceiveQueue.Companion.END) null else result)!!
        }

        companion object {
            private val END = Any()
        }
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(DefaultDaemonConnection::class.java)
    }
}
