/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.tooling.internal.consumer.async

import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.concurrent.AsyncStoppable
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

/**
 * Manages the lifecycle of some thread-safe service or resource.
 */
class ServiceLifecycle(private val displayName: String?) : AsyncStoppable {
    private enum class State {
        RUNNING, STOPPING, STOPPED
    }

    private val lock: Lock = ReentrantLock()
    private val condition: Condition = lock.newCondition()
    private val usages: MutableMap<Thread?, Int> = HashMap<Thread?, Int>()
    private var state = State.RUNNING

    fun use(runnable: Runnable) {
        lock.lock()
        try {
            when (state) {
                State.RUNNING -> {}
                State.STOPPING -> throw IllegalStateException(String.format("Cannot use %s as it is currently stopping.", displayName))
                State.STOPPED -> throw IllegalStateException(String.format("Cannot use %s as it has been stopped.", displayName))
            }
            val depth = usages.get(Thread.currentThread())
            if (depth == null) {
                usages.put(Thread.currentThread(), 1)
            } else {
                usages.put(Thread.currentThread(), depth + 1)
            }
        } finally {
            lock.unlock()
        }

        try {
            runnable.run()
        } finally {
            lock.lock()
            try {
                val depth: Int = usages.remove(Thread.currentThread())!!
                if (depth > 1) {
                    usages.put(Thread.currentThread(), depth - 1)
                }
                if (usages.isEmpty()) {
                    condition.signalAll()
                    if (state == State.STOPPING) {
                        state = State.STOPPED
                    }
                }
            } finally {
                lock.unlock()
            }
        }
    }

    override fun requestStop() {
        lock.lock()
        try {
            if (state == State.RUNNING) {
                if (usages.isEmpty()) {
                    state = State.STOPPED
                } else {
                    state = State.STOPPING
                }
            }
        } finally {
            lock.unlock()
        }
    }

    override fun stop() {
        lock.lock()
        try {
            check(!usages.containsKey(Thread.currentThread())) { String.format("Cannot stop %s from a thread that is using it.", displayName) }
            if (state == State.RUNNING) {
                state = State.STOPPING
            }
            while (!usages.isEmpty()) {
                try {
                    condition.await()
                } catch (e: InterruptedException) {
                    throw throwAsUncheckedException(e)
                }
            }
            if (state != State.STOPPED) {
                state = State.STOPPED
            }
        } finally {
            lock.unlock()
        }
    }
}
