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

/**
 * A simple implementation of the Invocation that accepts a lambda for [.callNext] implementation.
 *
 * @param <R> the type of the receiver
</R> */
class InvocationImpl<R>(private val invocationReceiver: R?, private val args: Array<Any>, private val callOriginal: ThrowingSupplier) : Invocation {
    fun interface ThrowingSupplier {
        @Throws(Throwable::class)
        fun get(): Any?
    }

    override val receiver: Any?
        get() = invocationReceiver

    override val argsCount: Int
        get() = args.size

    override fun getArgument(pos: Int): Any? {
        return InvocationUtils.unwrap(args[pos])
    }

    @Throws(Throwable::class)
    override fun callNext(): Any? {
        return callOriginal.get()
    }
}
