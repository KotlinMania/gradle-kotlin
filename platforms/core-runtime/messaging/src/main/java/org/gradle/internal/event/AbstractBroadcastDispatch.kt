/*
 * Copyright 2015 the original author or authors.
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

import org.gradle.internal.UncheckedException
import org.gradle.internal.dispatch.Dispatch
import org.gradle.internal.dispatch.MethodInvocation
import org.gradle.internal.operations.BuildOperationInvocationException

abstract class AbstractBroadcastDispatch<T>(protected val type: Class<T?>) : Dispatch<MethodInvocation?> {
    private val errorMessage: String
        get() {
            val typeDescription = type.getSimpleName().replace("(\\p{Upper})".toRegex(), " $1").trim { it <= ' ' }.lowercase()
            return "Failed to notify " + typeDescription + "."
        }

    protected fun dispatch(invocation: MethodInvocation, handler: Dispatch<MethodInvocation?>) {
        try {
            handler.dispatch(invocation)
        } catch (e: UncheckedException) {
            throw ListenerNotificationException(invocation, this.errorMessage, mutableListOf<Throwable>(e.cause!!))
        } catch (e: BuildOperationInvocationException) {
            throw ListenerNotificationException(invocation, this.errorMessage, mutableListOf<Throwable>(e.cause!!))
        } catch (t: RuntimeException) {
            throw t
        } catch (t: Throwable) {
            throw ListenerNotificationException(invocation, this.errorMessage, mutableListOf<Throwable>(t))
        }
    }

    /**
     * Dispatch an invocation to the given dispatchers.
     *
     *
     * This method will try to dispatch the invocation in an efficient way based on the number of dispatchers.
     *
     */
    protected fun dispatch(invocation: MethodInvocation, dispatchers: MutableList<out Dispatch<MethodInvocation>>) {
        when (dispatchers.size) {
            0 -> {}
            1 -> dispatch(invocation, dispatchers.get(0))
            else -> dispatch(invocation, dispatchers.iterator())
        }
    }

    /**
     * Dispatch an invocation to multiple handlers.
     */
    private fun dispatch(invocation: MethodInvocation, handlers: MutableIterator<out Dispatch<MethodInvocation>>) {
        // Defer creation of failures list, assume dispatch will succeed
        var failures: MutableList<Throwable>? = null
        while (handlers.hasNext()) {
            val handler: Dispatch<MethodInvocation?> = handlers.next()
            try {
                handler.dispatch(invocation)
            } catch (e: ListenerNotificationException) {
                if (failures == null) {
                    failures = ArrayList<Throwable>()
                }
                if (invocation.equals(e.getEvent())) {
                    failures.addAll(e.getCauses())
                } else {
                    failures.add(e)
                }
            } catch (e: UncheckedException) {
                if (failures == null) {
                    failures = ArrayList<Throwable>()
                }
                failures.add(e.cause!!)
            } catch (t: Throwable) {
                if (failures == null) {
                    failures = ArrayList<Throwable>()
                }
                failures.add(t)
            }
        }
        if (failures == null) {
            return
        }
        if (failures.size == 1 && failures.get(0) is RuntimeException) {
            throw failures.get(0) as RuntimeException
        }
        throw ListenerNotificationException(invocation, this.errorMessage, failures)
    }
}
