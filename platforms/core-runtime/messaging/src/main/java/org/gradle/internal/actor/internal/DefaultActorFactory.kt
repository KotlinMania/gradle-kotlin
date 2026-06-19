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
package org.gradle.internal.actor.internal

import org.gradle.internal.actor.Actor
import org.gradle.internal.actor.ActorFactory
import org.gradle.internal.concurrent.CompositeStoppable
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.concurrent.ManagedExecutor
import org.gradle.internal.concurrent.Stoppable
import org.gradle.internal.concurrent.ThreadSafe
import org.gradle.internal.dispatch.AsyncDispatch
import org.gradle.internal.dispatch.Dispatch
import org.gradle.internal.dispatch.DispatchException
import org.gradle.internal.dispatch.ExceptionTrackingFailureHandler
import org.gradle.internal.dispatch.FailureHandlingDispatch
import org.gradle.internal.dispatch.MethodInvocation
import org.gradle.internal.dispatch.ProxyDispatchAdapter
import org.gradle.internal.dispatch.ReflectionDispatch
import org.slf4j.LoggerFactory
import java.util.IdentityHashMap

/**
 * A basic [ActorFactory] implementation. Currently cannot support creating both a blocking and non-blocking actor for the same target object.
 */
class DefaultActorFactory(private val executorFactory: ExecutorFactory) : ActorFactory, Stoppable {
    private val nonBlockingActors = IdentityHashMap<Any?, NonBlockingActor?>()
    private val blockingActors = IdentityHashMap<Any?, BlockingActor?>()
    private val lock = Any()

    /**
     * Stops all actors.
     */
    override fun stop() {
        synchronized(lock) {
            try {
                CompositeStoppable.stoppable(nonBlockingActors.values).add(blockingActors.values).stop()
            } finally {
                nonBlockingActors.clear()
            }
        }
    }

    override fun createActor(target: Any): Actor {
        if (target is NonBlockingActor) {
            return target
        }
        synchronized(lock) {
            if (blockingActors.containsKey(target)) {
                throw UnsupportedOperationException("Cannot create a non-blocking and blocking actor for the same object. This is not implemented yet.")
            }
            var actor = nonBlockingActors.get(target)
            if (actor == null) {
                actor = NonBlockingActor(target)
                nonBlockingActors.put(target, actor)
            }
            return actor!!
        }
    }

    override fun createBlockingActor(target: Any): Actor {
        synchronized(lock) {
            if (nonBlockingActors.containsKey(target)) {
                throw UnsupportedOperationException("Cannot create a non-blocking and blocking actor for the same object. This is not implemented yet.")
            }
            var actor = blockingActors.get(target)
            if (actor == null) {
                actor = BlockingActor(target)
                blockingActors.put(target, actor)
            }
            return actor!!
        }
    }

    private fun stopped(actor: NonBlockingActor?) {
        synchronized(lock) {
            nonBlockingActors.values.remove(actor)
        }
    }

    private fun stopped(actor: BlockingActor?) {
        synchronized(lock) {
            blockingActors.values.remove(actor)
        }
    }

    private inner class BlockingActor(target: Any?) : Actor {
        private val dispatch: Dispatch<MethodInvocation?>
        private val lock = Any()
        private var stopped = false

        init {
            dispatch = ReflectionDispatch(target)
        }

        override fun <T> getProxy(type: Class<T?>): T? {
            return ProxyDispatchAdapter<T?>(this, type, ThreadSafe::class.java).source
        }

        @Throws(DispatchException::class)
        override fun stop() {
            synchronized(lock) {
                stopped = true
            }
            stopped(this)
        }

        override fun dispatch(message: MethodInvocation?) {
            synchronized(lock) {
                check(!stopped) { "This actor has been stopped." }
                dispatch.dispatch(message)
            }
        }
    }

    private inner class NonBlockingActor(targetObject: Any?) : Actor {
        private val dispatch: Dispatch<MethodInvocation?>
        private val executor: ManagedExecutor
        private val failureHandler: ExceptionTrackingFailureHandler

        init {
            executor = executorFactory.create("Dispatch " + targetObject)
            failureHandler = ExceptionTrackingFailureHandler(LoggerFactory.getLogger(NonBlockingActor::class.java))
            dispatch = AsyncDispatch<MethodInvocation>(
                executor,
                FailureHandlingDispatch<MethodInvocation>(
                    ReflectionDispatch(targetObject),
                    failureHandler
                ), Int.MAX_VALUE
            )
        }

        override fun <T> getProxy(type: Class<T?>): T? {
            return ProxyDispatchAdapter<T?>(this, type, ThreadSafe::class.java).source
        }

        override fun stop() {
            try {
                CompositeStoppable.stoppable(dispatch, executor, failureHandler).stop()
            } finally {
                stopped(this)
            }
        }

        override fun dispatch(message: MethodInvocation?) {
            dispatch.dispatch(message)
        }
    }
}
