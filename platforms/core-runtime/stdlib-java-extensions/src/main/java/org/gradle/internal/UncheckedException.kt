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
package org.gradle.internal

import java.io.IOException
import java.io.UncheckedIOException
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.Callable

/**
 * Wraps a checked exception. Carries no other context.
 */
class UncheckedException : RuntimeException {
    private constructor(cause: Throwable?) : super(cause)

    private constructor(message: String?, cause: Throwable?) : super(message, cause)

    companion object {
        /**
         * Note: always throws the failure in some form. The return value is to keep the compiler happy.
         */
        @JvmStatic
        @JvmOverloads
        fun throwAsUncheckedException(t: Throwable, preserveMessage: Boolean = false): RuntimeException {
            if (t is InterruptedException) {
                Thread.currentThread().interrupt()
            }
            if (t is RuntimeException) {
                throw t
            }
            if (t is Error) {
                throw t
            }
            if (t is IOException) {
                if (preserveMessage) {
                    throw UncheckedIOException(t.message, t)
                } else {
                    throw UncheckedIOException(t)
                }
            }
            if (preserveMessage) {
                throw UncheckedException(t.message, t)
            } else {
                throw UncheckedException(t)
            }
        }

        fun <T> unchecked(callable: Callable<T?>): T? {
            try {
                return callable.call()
            } catch (e: Exception) {
                throw throwAsUncheckedException(e)
            }
        }

        /**
         * Unwraps passed InvocationTargetException hence making the stack of exceptions cleaner without losing information.
         *
         * Note: always throws the failure in some form. The return value is to keep the compiler happy.
         *
         * @param e to be unwrapped
         * @return an instance of RuntimeException based on the target exception of the parameter.
         */
        @JvmStatic
        fun unwrapAndRethrow(e: InvocationTargetException): Nothing {
            throw throwAsUncheckedException(e.targetException)
        }

        /**
         * Calls the given callable converting any thrown exception to an unchecked exception via [throwAsUncheckedException]
         *
         * @param callable The callable to call
         * @param <T> Callable's return type
         * @return The value returned by [Callable.call]
        </T> */
        fun <T> uncheckedCall(callable: Callable<T?>): T? {
            try {
                return callable.call()
            } catch (e: Exception) {
                throw throwAsUncheckedException(e)
            }
        }
    }
}
