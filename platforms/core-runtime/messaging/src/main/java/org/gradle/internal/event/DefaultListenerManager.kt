/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.internal.event

import com.google.common.collect.ImmutableList
import org.gradle.internal.Cast
import org.gradle.internal.dispatch.Dispatch
import org.gradle.internal.dispatch.MethodInvocation
import org.gradle.internal.dispatch.ProxyDispatchAdapter
import org.gradle.internal.dispatch.ReflectionDispatch
import org.gradle.internal.reflect.JavaReflectionUtil
import org.gradle.internal.service.AnnotatedServiceLifecycleHandler
import org.gradle.internal.service.scopes.EventScope
import org.gradle.internal.service.scopes.ListenerService
import org.gradle.internal.service.scopes.ParallelListener
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.StatefulListener
import org.gradle.util.internal.ArrayUtils
import org.gradle.util.internal.CollectionUtils
import java.util.LinkedList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Function
import kotlin.concurrent.Volatile

open class DefaultListenerManager private constructor(private val scope: Class<out Scope>, private val parent: DefaultListenerManager?) : ScopedListenerManager {
    private val allListeners: MutableMap<Any, ListenerDetails> = LinkedHashMap<Any, ListenerDetails>()
    private val allLoggers: MutableMap<Any, ListenerDetails> = LinkedHashMap<Any, ListenerDetails>()
    private val broadcasters: MutableMap<Class<*>, EventBroadcast<*>> = ConcurrentHashMap<Class<*>, EventBroadcast<*>>()
    private val pendingServices: MutableList<AnnotatedServiceLifecycleHandler.Registration> = ArrayList<AnnotatedServiceLifecycleHandler.Registration>()
    private val pendingRegistrations: MutableList<AnnotatedServiceLifecycleHandler.Registration> = ArrayList<AnnotatedServiceLifecycleHandler.Registration>()
    private val lock = Any()

    constructor(scope: Class<out Scope>) : this(scope, null)

    override fun getAnnotations(): MutableList<Class<out Annotation>> {
        return ANNOTATIONS
    }

    override fun getImplicitAnnotation(): Class<out Annotation>? {
        return null
    }

    override fun whenRegistered(annotation: Class<out Annotation>, registration: AnnotatedServiceLifecycleHandler.Registration) {
        synchronized(lock) {
            if (annotation == ListenerService::class.java) {
                pendingServices.add(registration)
            } else {
                pendingRegistrations.add(registration)
                for (broadcast in broadcasters.values) {
                    if (registrationProvides(broadcast.type, registration)) {
                        broadcast.assertMutable("add listener")
                    }
                }
            }
        }
    }

    private fun maybeAddPendingRegistrations(type: Class<*>) {
        synchronized(lock) {
            for (registration in pendingServices) {
                addListener(registration.instance)
            }
            pendingServices.clear()

            var i = 0
            while (i < pendingRegistrations.size) {
                val registration = pendingRegistrations.get(i)
                if (registrationProvides(type, registration)) {
                    addListener(registration.instance)
                    pendingRegistrations.removeAt(i)
                } else {
                    i++
                }
            }
        }
    }

    override fun addListener(listener: Any) {
        var details: ListenerDetails? = null
        synchronized(lock) {
            if (!allListeners.containsKey(listener)) {
                details = DefaultListenerManager.ListenerDetails(listener)
                allListeners.put(listener, details!!)
            }
        }
        if (details != null) {
            details.useAsListener()
        }
    }

    override fun removeListener(listener: Any) {
        val details: ListenerDetails?
        synchronized(lock) {
            details = allListeners.remove(listener)
            if (details != null) {
                details.disconnect()
            }
        }
        if (details != null) {
            details.remove()
        }
    }

    override fun useLogger(logger: Any) {
        var details: ListenerDetails? = null
        synchronized(lock) {
            if (!allLoggers.containsKey(logger)) {
                details = DefaultListenerManager.ListenerDetails(logger)
                allLoggers.put(logger, details!!)
            }
        }
        if (details != null) {
            details.useAsLogger()
        }
    }

    override fun <T> hasListeners(listenerClass: Class<T?>): Boolean {
        val broadcaster = getBroadcasterInternal<T?>(listenerClass)
        return !broadcaster.listeners.isEmpty()
    }

    override fun <T> getBroadcaster(listenerClass: Class<T?>): T? {
        assertCanBroadcast<T?>(listenerClass)
        return getBroadcasterInternal<T?>(listenerClass).broadcaster
    }

    override fun <T> createAnonymousBroadcaster(listenerClass: Class<T?>): AnonymousListenerBroadcast<T?> {
        assertCanBroadcast<T?>(listenerClass)
        return AnonymousListenerBroadcast<T?>(listenerClass, getBroadcasterInternal<T?>(listenerClass).getDispatch(true))
    }

    private fun <T> getBroadcasterInternal(listenerClass: Class<T?>): EventBroadcast<T?> {
        // Try once before locking to avoid contention
        var broadcaster = Cast.uncheckedCast<EventBroadcast<T?>>(broadcasters.get(listenerClass))
        if (broadcaster != null) {
            return broadcaster
        }
        synchronized(lock) {
            broadcaster = Cast.uncheckedCast<EventBroadcast<T?>>(broadcasters.get(listenerClass))
            if (broadcaster == null) {
                if (listenerClass.getAnnotation<StatefulListener>(StatefulListener::class.java) != null) {
                    broadcaster = DefaultListenerManager.ParallelEventBroadcast<T?>(listenerClass)
                } else {
                    broadcaster = DefaultListenerManager.ExclusiveEventBroadcast<T?>(listenerClass)
                }

                for (listener in allListeners.values) {
                    broadcaster!!.maybeAdd(listener)
                }
                for (logger in allLoggers.values) {
                    broadcaster!!.maybeSetLogger(logger)
                }

                broadcasters.put(listenerClass, broadcaster!!)
            }
            return broadcaster
        }
    }

    private fun <T> assertCanBroadcast(listenerClass: Class<T?>) {
        val scope = listenerClass.getAnnotation<EventScope>(EventScope::class.java)
        requireNotNull(scope) { String.format("Listener type %s is not annotated with @EventScope.", listenerClass.getName()) }
        require(ArrayUtils.contains<Class<out Scope>>(scope.value, this.scope)) {
            String.format(
                "Listener type %s with %s cannot be used to generate events in scope '%s'.",
                listenerClass.getName(),
                Companion.displayScopes(scope.value),
                this.scope.getSimpleName()
            )
        }
    }

    override fun createChild(scope: Class<out Scope>): DefaultListenerManager {
        return DefaultListenerManager(scope, this)
    }

    /**
     * A broadcaster. Manages all state and registered listener implementations for a given
     * listener interface.
     */
    private abstract inner class EventBroadcast<T>(val type: Class<T?>) {
        private val dispatch: ListenerDispatch
        private val dispatchNoLogger: ListenerDispatch

        private val listeners: MutableSet<ListenerDetails> = LinkedHashSet<ListenerDetails>()

        @Volatile
        private var source: ProxyDispatchAdapter<T?>? = null

        private var logger: ListenerDetails? = null

        private var parentDispatch: Dispatch<MethodInvocation?>? = null

        private var allWithLogger: ImmutableList<Dispatch<MethodInvocation>> = ImmutableList.of<Dispatch<MethodInvocation?>>()
        private var allWithNoLogger: ImmutableList<Dispatch<MethodInvocation>> = ImmutableList.of<Dispatch<MethodInvocation?>>()

        @Volatile
        protected var initialized: Boolean = false
        protected val initializationLock: Any = Any()

        init {
            dispatch = EventBroadcast.ListenerDispatch(type, true)
            dispatchNoLogger = EventBroadcast.ListenerDispatch(type, false)
            if (parent != null) {
                parentDispatch = parent.getBroadcasterInternal<T?>(type).getDispatch(true)
                initLoggers()
            }
        }

        fun getDispatch(includeLogger: Boolean): Dispatch<MethodInvocation?> {
            return if (includeLogger) dispatch else dispatchNoLogger
        }

        val broadcaster: T?
            get() {
                if (source == null) {
                    synchronized(this) {
                        if (source == null) {
                            source = ProxyDispatchAdapter<T?>(dispatch, type)
                        }
                    }
                }
                return source!!.source
            }

        protected fun getListeners(withLogger: Boolean): MutableList<Dispatch<MethodInvocation>> {
            return if (withLogger) allWithLogger else allWithNoLogger
        }

        protected fun initLoggers() {
            this.allWithLogger = initAllWithLogger()
            this.allWithNoLogger = initAllWithNoLogger()
        }

        /**
         * Add a listener to this broadcaster if it is of the correct type
         *
         * @throws IllegalStateException If this broadcaster is immutable
         */
        fun maybeAdd(listener: ListenerDetails) {
            if (!type.isInstance(listener.listener)) {
                return
            }

            assertMutable("add listener")
            doAdd(listener)
        }

        protected open fun doAdd(listener: ListenerDetails) {
            listeners.add(listener)
        }

        /**
         * Remove a listener from this broadcaster if it is of the correct type
         *
         * @throws IllegalStateException If this broadcaster is immutable
         */
        fun maybeRemove(listener: ListenerDetails) {
            if (!type.isInstance(listener.listener)) {
                return
            }

            assertMutable("remove listener")
            doRemove(listener)
        }

        protected open fun doRemove(listener: ListenerDetails) {
            listeners.remove(listener)
        }

        /**
         * Set the logger for this broadcaster if it is of the correct type
         *
         * @throws IllegalStateException If this broadcaster is immutable
         */
        fun maybeSetLogger(candidate: ListenerDetails) {
            if (!type.isInstance(candidate.listener)) {
                return
            }

            assertMutable("set logger")
            doSetLogger(candidate)
        }

        protected open fun doSetLogger(candidate: ListenerDetails) {
            if (logger == null && parent != null) {
                parentDispatch = parent.getBroadcasterInternal<T?>(type).getDispatch(false)
            }
            logger = candidate
        }

        fun initAllWithNoLogger(): ImmutableList<Dispatch<MethodInvocation>> {
            if (parentDispatch == null && listeners.isEmpty()) {
                return ImmutableList.of<Dispatch<MethodInvocation?>>()
            }

            val dispatchers: ImmutableList.Builder<Dispatch<MethodInvocation>> = ImmutableList.builder<Dispatch<MethodInvocation?>>()
            if (parentDispatch != null) {
                dispatchers.add(parentDispatch)
            }
            dispatchers.addAll(listeners)
            return dispatchers.build()
        }

        fun initAllWithLogger(): ImmutableList<Dispatch<MethodInvocation>> {
            if (logger == null && parentDispatch == null && listeners.isEmpty()) {
                return ImmutableList.of<Dispatch<MethodInvocation?>>()
            }

            val result: ImmutableList.Builder<Dispatch<MethodInvocation>> = ImmutableList.builder<Dispatch<MethodInvocation?>>()
            if (logger != null) {
                result.add(logger)
            }
            if (parentDispatch != null) {
                result.add(parentDispatch)
            }
            result.addAll(listeners)
            return result.build()
        }

        protected fun maybeInitialize() {
            if (!initialized) {
                synchronized(initializationLock) {
                    if (!initialized) {
                        maybeAddPendingRegistrations(this@EventBroadcast.type)
                        initLoggers()
                        initialized = true
                    }
                }
            }
        }

        protected abstract fun startDispatch(includeLogger: Boolean): MutableList<Dispatch<MethodInvocation>>

        protected abstract fun endDispatch()

        abstract fun assertMutable(operation: String)

        private inner class ListenerDispatch(type: Class<T?>, private val includeLogger: Boolean) : AbstractBroadcastDispatch<T?>(type) {
            override fun dispatch(invocation: MethodInvocation) {
                maybeInitialize()

                val dispatchers = startDispatch(includeLogger)
                try {
                    dispatch(invocation, dispatchers)
                } finally {
                    endDispatch()
                }
            }
        }
    }

    private inner class ExclusiveEventBroadcast<T>(type: Class<T?>) : EventBroadcast<T?>(type) {
        private val broadcasterLock = ReentrantLock()
        private val queuedOperations: MutableList<Runnable> = LinkedList<Runnable>()

        override fun doAdd(listener: ListenerDetails) {
            executeNowOrLater(Runnable { super@ExclusiveEventBroadcast.doAdd(listener) })
        }

        override fun doRemove(listener: ListenerDetails) {
            executeNowOrLater(Runnable { super@ExclusiveEventBroadcast.doRemove(listener) })
        }

        override fun doSetLogger(candidate: ListenerDetails) {
            executeNowOrLater(Runnable { super@ExclusiveEventBroadcast.doSetLogger(candidate) })
        }

        /**
         * Try to execute the given operation now if the broadcast lock is
         * uncontested, otherwise queue it for later.
         */
        fun executeNowOrLater(operation: Runnable) {
            if (broadcasterLock.tryLock()) {
                try {
                    operation.run()
                    initLoggers()
                } finally {
                    broadcasterLock.unlock()
                }
            } else {
                synchronized(queuedOperations) {
                    queuedOperations.add(operation)
                }
            }
        }

        override fun startDispatch(includeLogger: Boolean): MutableList<Dispatch<MethodInvocation>> {
            check(!broadcasterLock.isHeldByCurrentThread()) {
                String.format(
                    "Cannot notify listeners of type %s as these listeners are already being notified.",
                    type.getSimpleName()
                )
            }

            broadcasterLock.lock()

            // Ensure we retrieve listeners while holding lock.
            return getListeners(includeLogger)
        }

        override fun endDispatch() {
            try {
                synchronized(queuedOperations) {
                    if (!queuedOperations.isEmpty()) {
                        for (queuedOperation in queuedOperations) {
                            queuedOperation.run()
                        }
                        initLoggers()
                    }
                }
            } finally {
                broadcasterLock.unlock()
            }
        }

        protected override fun assertMutable(operation: String) {
            // Since we perform locking when operating on listeners,
            // the exclusive broadcaster is always mutable.
        }
    }

    /**
     * An [EventBroadcast] that allows listeners to be notified in parallel.
     * This is accomplished by forbidding the mutation of the listeners to notify
     * after an event has been broadcast.
     */
    private inner class ParallelEventBroadcast<T>(type: Class<T?>) : EventBroadcast<T?>(type) {
        override fun startDispatch(includeLogger: Boolean): MutableList<Dispatch<MethodInvocation>> {
            return getListeners(includeLogger)
        }

        override fun endDispatch() {}

        protected override fun assertMutable(operation: String) {
            synchronized(initializationLock) {
                check(!initialized) {
                    String.format(
                        "Cannot %s of type %s after events have been broadcast.",
                        operation,
                        type.getSimpleName()
                    )
                }
            }
        }
    }

    /**
     * Holds state about a particular listener implementation. A listener implementation may
     * implement multiple listener interfaces and therefore may receive events from multiple
     * broadcasters.
     */
    private inner class ListenerDetails(val listener: Any) : Dispatch<MethodInvocation?> {
        val dispatch: Dispatch<MethodInvocation?>
        val parallel: Boolean
        val removed: AtomicBoolean = AtomicBoolean()
        val notifyingLock: ReentrantLock = ReentrantLock()

        init {
            this.dispatch = ReflectionDispatch(listener)
            this.parallel = JavaReflectionUtil.hasAnnotation(listener.javaClass, ParallelListener::class.java)
        }

        fun disconnect() {
            removed.set(true)
        }

        override fun dispatch(message: MethodInvocation) {
            if (removed.get()) {
                return
            }

            if (parallel) {
                dispatch.dispatch(message)
                return
            }

            notifyingLock.lock()
            try {
                dispatch.dispatch(message)
            } finally {
                notifyingLock.unlock()
            }
        }

        fun remove() {
            // block until the listener has finished notifying.
            notifyingLock.lock()
            try {
                for (broadcaster in broadcasters.values) {
                    broadcaster.maybeRemove(this)
                }
            } finally {
                notifyingLock.unlock()
            }
        }

        fun useAsLogger() {
            for (broadcaster in broadcasters.values) {
                broadcaster.maybeSetLogger(this)
            }
        }

        fun useAsListener() {
            for (broadcaster in broadcasters.values) {
                broadcaster.maybeAdd(this)
            }
        }
    }

    companion object {
        private val ANNOTATIONS: MutableList<Class<out Annotation>> = ImmutableList.of<Class<out Annotation>>(StatefulListener::class.java, ListenerService::class.java)
        private fun registrationProvides(type: Class<*>, registration: AnnotatedServiceLifecycleHandler.Registration): Boolean {
            for (declaredType in registration.declaredTypes) {
                if (type.isAssignableFrom(declaredType)) {
                    return true
                }
            }
            return false
        }

        private fun displayScopes(scopes: Array<Class<out Scope>>): String {
            if (scopes.size == 1) {
                return "service scope '" + scopes[0].getSimpleName() + "'"
            }

            return "service scopes " + CollectionUtils.join<String, Class<out Scope>>(", ", scopes, Function { aClass: Class<out Scope> -> "'" + aClass.getSimpleName() + "'" })
        }
    }
}
