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

import org.gradle.api.Action
import org.gradle.internal.dispatch.Dispatch
import org.gradle.internal.dispatch.MethodInvocation
import org.gradle.internal.dispatch.ProxyDispatchAdapter
import javax.annotation.concurrent.ThreadSafe
import kotlin.concurrent.Volatile

/**
 *
 * Manages a set of listeners of type T. Provides an implementation of T which can be used to broadcast to all
 * registered listeners.
 *
 *
 * Ordering is maintained for events, so that events are delivered to listeners in the order they are generated.
 * Events are delivered to listeners in the order that listeners are added to this broadcaster.
 *
 * @param <T> The listener type.
</T> */
@ThreadSafe
open class ListenerBroadcast<T>(
    /**
     * Returns the type of listener to which this class broadcasts.
     *
     * @return The type of the broadcaster.
     */
    val type: Class<T?>
) : Dispatch<MethodInvocation?> {
    private var source: ProxyDispatchAdapter<T?>? = null

    @Volatile
    private var broadcast: BroadcastDispatch<T?>

    init {
        this.broadcast = BroadcastDispatch.Companion.empty<T?>(type)
    }

    /**
     * Returns the broadcaster. Any method call on this object is broadcast to all listeners.
     *
     * @return The broadcaster.
     */
    @Synchronized
    fun getSource(): T? {
        if (source == null) {
            source = ProxyDispatchAdapter<T?>(this, type)
        }
        return source!!.source
    }

    val isEmpty: Boolean
        /**
         * Returns `true` if no listeners are registered with this object.
         *
         * @return `true` if no listeners are registered with this object, `false` otherwise
         */
        get() = broadcast.isEmpty()

    /**
     * Returns the number of listeners that are registered with this object.
     */
    fun size(): Int {
        return broadcast.size()
    }

    /**
     * Adds a listener.
     *
     * @param listener The listener.
     */
    @Synchronized
    fun add(listener: T?) {
        broadcast = broadcast.add(listener)
    }

    /**
     * Adds the given listeners.
     *
     * @param listeners The listeners
     */
    @Synchronized
    fun addAll(listeners: MutableCollection<out T>) {
        broadcast = broadcast.addAll(listeners)!!
    }

    /**
     * Adds a [Dispatch] to receive events from this broadcast.
     */
    @Synchronized
    fun add(dispatch: Dispatch<MethodInvocation?>) {
        broadcast = broadcast.add(dispatch)
    }

    /**
     * Adds an action to be executed when the given method is called.
     */
    @Synchronized
    fun add(methodName: String, action: Action<*>) {
        broadcast = broadcast.add(methodName, action)
    }

    /**
     * Removes the given listener.
     *
     * @param listener The listener.
     */
    @Synchronized
    fun remove(listener: Any) {
        broadcast = broadcast.remove(listener)!!
    }

    /**
     * Removes the given listeners.
     *
     * @param listeners The listeners
     */
    @Synchronized
    fun removeAll(listeners: MutableCollection<*>) {
        broadcast = broadcast.removeAll(listeners)!!
    }

    /**
     * Removes all listeners.
     */
    @Synchronized
    open fun removeAll() {
        broadcast = BroadcastDispatch.Companion.empty<T?>(type)
    }

    /**
     * Removes all listeners and replaces them with the given listener.
     */
    @Synchronized
    fun replaceWith(dispatch: Dispatch<MethodInvocation?>) {
        broadcast = BroadcastDispatch.Companion.empty<T?>(type).add(dispatch)
    }

    /**
     * Broadcasts the given event to all listeners.
     *
     * @param event The event
     */
    override fun dispatch(event: MethodInvocation?) {
        broadcast.dispatch(event)
    }

    fun visitListeners(visitor: Action<T?>) {
        broadcast.visitListeners(visitor)
    }

    fun visitListenersUntyped(visitor: Action<Any>) {
        broadcast.visitListenersUntyped(visitor)
    }

    /**
     * Returns a new [ListenerBroadcast] with the same [BroadcastDispatch] as this class.
     */
    fun copy(): ListenerBroadcast<T?> {
        val result = ListenerBroadcast<T?>(type)
        result.broadcast = this.broadcast
        return result
    }
}
