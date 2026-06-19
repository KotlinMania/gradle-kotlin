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
import org.gradle.internal.Cast
import org.gradle.internal.dispatch.Dispatch
import org.gradle.internal.dispatch.MethodInvocation
import org.gradle.internal.dispatch.ReflectionDispatch
import org.gradle.util.internal.CollectionUtils

/**
 * An immutable composite [Dispatch] implementation. Optimized for a small number of elements, and for infrequent modification.
 */
abstract class BroadcastDispatch<T> private constructor(type: Class<T?>) : AbstractBroadcastDispatch<T?>(type) {
    abstract fun isEmpty(): Boolean

    abstract fun size(): Int

    fun add(dispatch: Dispatch<MethodInvocation?>): BroadcastDispatch<T?> {
        return add(dispatch, dispatch)
    }

    fun add(listener: T?): BroadcastDispatch<T?> {
        return add(listener!!, ReflectionDispatch(listener))
    }

    fun add(methodName: String, action: Action<*>): BroadcastDispatch<T?> {
        assertIsMethod(methodName)
        return add(action, ActionInvocationHandler(methodName, Cast.uncheckedNonnullCast<Action<Any>>(action)))
    }

    abstract fun add(handler: Any, dispatch: Dispatch<MethodInvocation?>): BroadcastDispatch<T?>

    private fun assertIsMethod(methodName: String) {
        for (method in type.getMethods()) {
            if (method.getName() == methodName) {
                return
            }
        }
        throw IllegalArgumentException(
            String.format(
                "Method %s() not found for listener type %s.", methodName,
                type.getSimpleName()
            )
        )
    }

    abstract fun remove(listener: Any): BroadcastDispatch<T?>?

    abstract fun addAll(listeners: MutableCollection<out T?>): BroadcastDispatch<T?>?

    abstract fun removeAll(listeners: MutableCollection<*>): BroadcastDispatch<T?>?

    abstract fun visitListeners(visitor: Action<T?>)

    abstract fun visitListenersUntyped(visitor: Action<Any>)

    private class ActionInvocationHandler(private val methodName: String, private val action: Action<Any>) : Dispatch<MethodInvocation?> {
        override fun dispatch(message: MethodInvocation?) {
            if (message!!.methodName == methodName) {
                action.execute(message.arguments[0])
            }
        }
    }

    private class EmptyDispatch<T>(type: Class<T?>) : BroadcastDispatch<T?>(type) {
        override fun toString(): String {
            return "<empty>"
        }

        override fun isEmpty(): Boolean {
            return true
        }

        override fun size(): Int {
            return 0
        }

        override fun remove(listener: Any): BroadcastDispatch<T?> {
            return this
        }

        override fun removeAll(listeners: MutableCollection<*>): BroadcastDispatch<T?> {
            return this
        }

        override fun add(handler: Any, dispatch: Dispatch<MethodInvocation?>): BroadcastDispatch<T?> {
            return SingletonDispatch<T?>(type, handler, dispatch)
        }

        override fun visitListeners(visitor: Action<T?>) {
        }

        override fun visitListenersUntyped(visitor: Action<Any>) {
        }

        override fun addAll(listeners: MutableCollection<out T?>): BroadcastDispatch<T?> {
            val result: MutableList<SingletonDispatch<T?>> = ArrayList<SingletonDispatch<T?>>()
            for (listener in listeners) {
                val dispatch = BroadcastDispatch.SingletonDispatch<T?>(type, listener!!, ReflectionDispatch(listener))
                if (!result.contains(dispatch)) {
                    result.add(dispatch)
                }
            }
            if (result.isEmpty()) {
                return this
            }
            if (result.size == 1) {
                return result.get(0)
            }
            return CompositeDispatch<T?>(type, result)
        }

        override fun dispatch(message: MethodInvocation?) {
        }
    }

    private class SingletonDispatch<T>(type: Class<T?>, val handler: Any, private val dispatch: Dispatch<MethodInvocation?>) : BroadcastDispatch<T?>(type) {
        override fun toString(): String {
            return handler.toString()
        }

        override fun equals(obj: Any?): Boolean {
            val other = Cast.uncheckedNonnullCast<SingletonDispatch<T?>>(obj)
            return sameOrEquals(handler, other.handler)
        }

        override fun hashCode(): Int {
            return handler.hashCode()
        }

        override fun add(handler: Any, dispatch: Dispatch<MethodInvocation?>): BroadcastDispatch<T?> {
            if (sameOrEquals(this.handler, handler)) {
                return this
            }
            val result: MutableList<SingletonDispatch<T?>> = ArrayList<SingletonDispatch<T?>>()
            result.add(Cast.uncheckedNonnullCast<SingletonDispatch<T?>>(this))
            result.add(SingletonDispatch<T?>(type, handler, dispatch))
            return CompositeDispatch<T?>(type, result)
        }

        override fun addAll(listeners: MutableCollection<out T?>): BroadcastDispatch<T?> {
            val result: MutableList<SingletonDispatch<T?>> = ArrayList<SingletonDispatch<T?>>()
            result.add(Cast.uncheckedNonnullCast<SingletonDispatch<T?>>(this))
            for (listener in listeners) {
                if (Companion.sameOrEquals(handler, listener!!)) {
                    continue
                }
                val dispatch = BroadcastDispatch.SingletonDispatch<T?>(type, listener, ReflectionDispatch(listener))
                if (!result.contains(dispatch)) {
                    result.add(dispatch)
                }
            }
            if (result.size == 1) {
                return this
            }
            return CompositeDispatch<T?>(type, result)
        }

        override fun remove(listener: Any): BroadcastDispatch<T?> {
            if (sameOrEquals(handler, listener)) {
                return EmptyDispatch<T?>(type)
            }
            return this
        }

        override fun removeAll(listeners: MutableCollection<*>): BroadcastDispatch<T?> {
            for (listener in listeners) {
                if (Companion.sameOrEquals(handler, listener!!)) {
                    return EmptyDispatch<T?>(type)
                }
            }
            return this
        }

        override fun isEmpty(): Boolean {
            return false
        }

        override fun size(): Int {
            return 1
        }

        override fun visitListeners(visitor: Action<T?>) {
            if (this.type.isInstance(handler)) {
                visitor.execute(this.type.cast(handler))
            }
        }

        override fun visitListenersUntyped(visitor: Action<Any>) {
            visitor.execute(handler)
        }

        override fun dispatch(message: MethodInvocation?) {
            dispatch(message!!, dispatch)
        }
    }

    private class CompositeDispatch<T>(type: Class<T?>, private val dispatchers: MutableList<SingletonDispatch<T?>>) : BroadcastDispatch<T?>(type) {
        override fun toString(): String {
            return dispatchers.toString()
        }

        override fun add(handler: Any, dispatch: Dispatch<MethodInvocation?>): BroadcastDispatch<T?> {
            val result: MutableList<SingletonDispatch<T?>> = ArrayList<SingletonDispatch<T?>>()
            for (listener in dispatchers) {
                if (sameOrEquals(listener.handler, handler)) {
                    return this
                }
                result.add(listener)
            }
            result.add(SingletonDispatch<T?>(type, handler, dispatch))
            return CompositeDispatch<T?>(type, result)
        }

        override fun addAll(listeners: MutableCollection<out T?>): BroadcastDispatch<T?> {
            val result: MutableList<SingletonDispatch<T?>> = ArrayList<SingletonDispatch<T?>>(dispatchers)
            for (listener in listeners) {
                val dispatch = BroadcastDispatch.SingletonDispatch<T?>(type, listener!!, ReflectionDispatch(listener))
                if (!result.contains(dispatch)) {
                    result.add(dispatch)
                }
            }
            if (result == dispatchers) {
                return this
            }
            return CompositeDispatch<T?>(type, result)
        }

        override fun remove(listener: Any): BroadcastDispatch<T?> {
            val result: MutableList<SingletonDispatch<T?>> = ArrayList<SingletonDispatch<T?>>()
            var found = false
            for (dispatch in dispatchers) {
                if (sameOrEquals(dispatch.handler, listener)) {
                    found = true
                } else {
                    result.add(dispatch)
                }
            }
            if (!found) {
                return this
            }
            if (result.size == 1) {
                return result.get(0)
            }
            return CompositeDispatch<T?>(type, result)
        }

        override fun removeAll(listeners: MutableCollection<*>): BroadcastDispatch<T?> {
            val listenerList = CollectionUtils.toSet<Any>(listeners)
            val result: MutableList<SingletonDispatch<T?>> = ArrayList<SingletonDispatch<T?>>()
            for (dispatch in this.dispatchers) {
                if (!listenerList.contains(dispatch.handler)) {
                    result.add(dispatch)
                }
            }
            if (result.isEmpty()) {
                return EmptyDispatch<T?>(type)
            }
            if (result.size == 1) {
                return result.get(0)
            }
            if (result == this.dispatchers) {
                return this
            }
            return CompositeDispatch<T?>(type, result)
        }

        override fun visitListeners(visitor: Action<T?>) {
            for (dispatcher in dispatchers) {
                dispatcher.visitListeners(visitor)
            }
        }

        override fun visitListenersUntyped(visitor: Action<Any>) {
            for (dispatcher in dispatchers) {
                dispatcher.visitListenersUntyped(visitor)
            }
        }

        override fun isEmpty(): Boolean {
            return false
        }

        override fun size(): Int {
            return dispatchers.size
        }

        override fun dispatch(message: MethodInvocation?) {
            dispatch(message!!, dispatchers)
        }
    }

    companion object {
        fun <T> empty(type: Class<T?>): BroadcastDispatch<T?> {
            return EmptyDispatch<T?>(type)
        }

        private fun sameOrEquals(x: Any, y: Any): Boolean {
            return x === y || x == y
        }
    }
}
