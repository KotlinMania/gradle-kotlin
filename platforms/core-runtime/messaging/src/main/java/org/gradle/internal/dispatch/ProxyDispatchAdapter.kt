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

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * Adapts from interface T to a [Dispatch]
 */
class ProxyDispatchAdapter<T> {
    val type: Class<T>
    @JvmField
    val source: T

    constructor(dispatch: Dispatch<in MethodInvocation?>, type: Class<T>) {
        this.type = type
        source = type.cast(
            Proxy.newProxyInstance(
                type.getClassLoader(),
                arrayOf<Class<*>>(type),
                DispatchingInvocationHandler(type, dispatch)
            )
        )
    }

    constructor(dispatch: Dispatch<in MethodInvocation?>, type: Class<T>, extraType: Class<*>) {
        this.type = type
        source = type.cast(
            Proxy.newProxyInstance(
                selectClassLoader<T>(type, extraType),
                arrayOf<Class<*>>(type, extraType),
                DispatchingInvocationHandler(type, dispatch)
            )
        )
    }

    private class DispatchingInvocationHandler(private val type: Class<*>, private val dispatch: Dispatch<in MethodInvocation?>) : InvocationHandler {
        override fun invoke(target: Any?, method: Method, parameters: Array<Any?>?): Any? {
            when (method.getName()) {
                "equals" -> {
                    val parameter = parameters?.getOrNull(0)
                    if (parameter == null || !Proxy.isProxyClass(parameter.javaClass)) {
                        return false
                    }
                    val handler: Any? = Proxy.getInvocationHandler(parameter)
                    if (handler !is DispatchingInvocationHandler) {
                        return false
                    }

                    val otherHandler = handler
                    return otherHandler.type == type && otherHandler.dispatch === dispatch
                }

                "hashCode" -> return dispatch.hashCode()
                "toString" -> return type.getSimpleName() + " broadcast"
                else -> {
                    dispatch.dispatch(MethodInvocation(method, parameters))
                    return null
                }
            }
        }
    }

    companion object {
        private fun <T> selectClassLoader(type: Class<T>, extraType: Class<*>): ClassLoader? {
            val typeClassLoader = type.getClassLoader()
            val candidate = extraType.getClassLoader()
            return if (candidate !== typeClassLoader && candidate != null && isCanLoadType<T>(candidate, type))
                candidate
            else
                typeClassLoader
        }

        private fun <T> isCanLoadType(candidate: ClassLoader, type: Class<T>): Boolean {
            try {
                if (candidate.loadClass(type.getName()) != null) {
                    return true
                }
            } catch (e: ClassNotFoundException) {
                // Ignore
            }
            return false
        }
    }
}
