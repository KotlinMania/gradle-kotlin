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

import java.lang.invoke.MethodHandle

/**
 * The implementation of [Invocation] that forwards the call to a MethodHandle. Supports both normal and spread Groovy calls.
 */
internal class MethodHandleInvocation(private val original: MethodHandle, private val originalArgs: Array<Any>, isSpread: Boolean) : Invocation {
    private val unspreadArgs: Array<Any>
    private val unspreadArgsOffset: Int

    init {
        if (isSpread) {
            unspreadArgs = originalArgs[1] as Array<Any>
            unspreadArgsOffset = 0
        } else {
            unspreadArgs = originalArgs
            unspreadArgsOffset = 1
        }
    }

    override val receiver: Any?
        get() = InvocationUtils.unwrap(originalArgs[0])

    override val argsCount: Int
        get() = unspreadArgs.size - unspreadArgsOffset

    override fun getArgument(pos: Int): Any? {
        return InvocationUtils.unwrap(unspreadArgs[pos + unspreadArgsOffset])
    }

    @Throws(Throwable::class)
    override fun callNext(): Any? {
        return original.invokeWithArguments(originalArgs.asList())
    }
}
