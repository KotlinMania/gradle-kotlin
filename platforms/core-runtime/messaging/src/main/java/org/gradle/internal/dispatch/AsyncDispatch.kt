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

import org.gradle.internal.concurrent.AsyncStoppable
import org.gradle.internal.concurrent.InterruptibleRunnable
import org.gradle.internal.operations.CurrentBuildOperationPreservingRunnable
import java.util.LinkedList
import java.util.concurrent.Executor
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

/**
 *
 * A [Dispatch] implementation which delivers messages asynchronously. Calls to
 * [.dispatch] queue the message. Worker threads deliver the messages in the order they have been received to one
 * of a pool of delegate [Dispatch] instances.
 */
class AsyncDispatch<T> @JvmOverloads constructor(private val executor: Executor, dispatch: Dispatch<in T?>? = null, private val maxQueueSize: Int = MAX_QUEUE_SIZE) : Dispatch<T?>, AsyncStoppable {
    private enum class State {
        Init, Stopped
    }

    private val lock: Lock = ReentrantLock()
    private val condition: Condition = lock.newCondition()
    private val queue = LinkedList<T?>()
    private val dispatchers: MutableMap<Dispatch<*>?, InterruptibleRunnable> = HashMap<Dispatch<*>?, InterruptibleRunnable>()
    private var state: State?

    init {
        state = State.Init
        if (dispatch != null) {
            dispatchTo(dispatch)
        }
    }

    /**
     * Starts dispatching messages to the given handler. The handler does not need to be thread-safe.
     */
    fun dispatchTo(dispatch: Dispatch<in T?>) {
        val dispatcher = InterruptibleRunnable(object : Runnable {
            override fun run() {
                try {
                    dispatchMessages(dispatch)
                } finally {
                    onDispatchThreadExit(dispatch)
                }
            }
        })
        onDispatchThreadStart(dispatch, dispatcher)
        executor.execute(CurrentBuildOperationPreservingRunnable.wrapIfNeeded(dispatcher))
    }

    private fun dispatchMessages(dispatch: Dispatch<in T?>) {
        while (true) {
            val message = waitForNextMessage()
            if (message == null) {
                return
            }
            dispatch.dispatch(message)
        }
    }

    private fun waitForNextMessage(): T? {
        lock.lock()
        try {
            var interrupted = false
            while (state != State.Stopped && queue.isEmpty()) {
                try {
                    condition.await()
                } catch (e: InterruptedException) {
                    interrupted = true
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt()
            }
            if (!queue.isEmpty()) {
                val message = queue.remove()
                condition.signalAll()
                return message
            }
        } finally {
            lock.unlock()
        }
        return null
    }

    private fun onDispatchThreadStart(dispatch: Dispatch<in T?>?, dispatcher: InterruptibleRunnable?) {
        lock.lock()
        try {
            check(state == State.Init) { "This dispatch has been stopped." }
            dispatchers.put(dispatch, dispatcher!!)
        } finally {
            lock.unlock()
        }
    }

    private fun onDispatchThreadExit(dispatch: Dispatch<in T?>?) {
        lock.lock()
        try {
            dispatchers.remove(dispatch)
            condition.signalAll()
        } finally {
            lock.unlock()
        }
    }

    private fun setState(state: State?) {
        this.state = state
        condition.signalAll()
    }

    override fun dispatch(message: T?) {
        lock.lock()
        try {
            var interrupted = false
            while (state != State.Stopped && queue.size >= maxQueueSize) {
                try {
                    condition.await()
                } catch (e: InterruptedException) {
                    interrupted = true
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt()
            }
            check(state != State.Stopped) { "Cannot dispatch message, as this message dispatch has been stopped. Message: " + message }
            queue.add(message)
            condition.signalAll()
        } finally {
            lock.unlock()
        }
    }

    /**
     * Commences a shutdown of this dispatch.
     */
    override fun requestStop() {
        lock.lock()
        try {
            doRequestStop()
        } finally {
            lock.unlock()
        }
    }

    private fun doRequestStop() {
        setState(State.Stopped)
    }

    /**
     * Stops accepting new messages, and blocks until all queued messages have been dispatched.
     */
    override fun stop() {
        lock.lock()
        try {
            setState(State.Stopped)
            waitForAllMessages()
        } finally {
            lock.unlock()
        }
    }

    private fun waitForAllMessages() {
        var interrupted = false
        while (!dispatchers.isEmpty()) {
            try {
                condition.await()
            } catch (e: InterruptedException) {
                interrupted = true
                for (dispatcher in dispatchers.values) {
                    dispatcher.interrupt()
                }
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt()
        }
        check(queue.isEmpty()) { "Cannot wait for messages to be dispatched, as there are no dispatch threads running." }
    }

    companion object {
        private const val MAX_QUEUE_SIZE = 200
    }
}
