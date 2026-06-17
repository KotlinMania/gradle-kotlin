/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.internal.instrumentation.api.groovybytecode

import org.codehaus.groovy.vmplugin.v8.IndyInterface
import org.gradle.api.GradleException
import org.gradle.util.internal.CollectionUtils
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.Collections

abstract class AbstractCallInterceptor protected constructor(vararg interceptScopes: InterceptScope) : CallInterceptor {
    override val interceptScopes: MutableSet<InterceptScope> = Collections.unmodifiableSet(interceptScopes.toMutableSet())

    override fun decorateMethodHandle(original: MethodHandle, caller: MethodHandles.Lookup, flags: Int): MethodHandle {
        val spreader = original.asSpreader(Array<Any>::class.java, original.type().parameterCount())
        val decorated = MethodHandles.insertArguments(INTERCEPTOR, 0, this, spreader, flags, caller.lookupClass().getName())
        return decorated.asCollector(Array<Any>::class.java, original.type().parameterCount()).asType(original.type())
    }

    @Suppress("unused")
    @Throws(Throwable::class)
    private fun interceptMethodHandle(original: MethodHandle, flags: Int, consumer: String, args: Array<Any>): Any? {
        val isSafeNavigation = (flags and IndyInterface.SAFE_NAVIGATION) != 0
        if (isSafeNavigation && InvocationUtils.unwrap(args[0]) == null) {
            // Skip interception for safe navigation calls on null receiver
            return original.invokeWithArguments(args.asList())
        }
        val isSpread = (flags and IndyInterface.SPREAD_CALL) != 0
        return intercept(MethodHandleInvocation(original, args, isSpread), consumer)
    }

    companion object {
        private val LOOKUP: MethodHandles.Lookup = MethodHandles.lookup()

        private val INTERCEPTOR: MethodHandle

        init {
            try {
                INTERCEPTOR = LOOKUP.findVirtual(
                    AbstractCallInterceptor::class.java,
                    "interceptMethodHandle",
                    MethodType.methodType(Any::class.java, MethodHandle::class.java, Int::class.javaPrimitiveType, String::class.java, Array<Any>::class.java)
                )
            } catch (e: NoSuchMethodException) {
                throw GradleException("Failed to set up an interceptor method", e)
            } catch (e: IllegalAccessException) {
                throw GradleException("Failed to set up an interceptor method", e)
            }
        }
    }
}
