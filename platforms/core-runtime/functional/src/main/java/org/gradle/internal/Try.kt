/*
 * Copyright 2018 the original author or authors.
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
import java.util.Objects
import java.util.Optional
import java.util.concurrent.Callable
import java.util.function.Consumer
import java.util.function.Function

/**
 * An object to represent the result of an operation that can potentially fail.
 * The object either holds the result of a successful execution, or an exception encountered during a failed one.
 *
 *
 * It is possible for Try to hold null values.
 */
abstract class Try<T : Any?> private constructor() {
    /**
     * Returns whether this `Try` represents a successful execution.
     */
    abstract val isSuccessful: Boolean

    /**
     * Return the result if the represented operation was successful.
     * Throws the original failure otherwise (wrapped in an `UncheckedException` if necessary).
     */
    abstract fun get(): T?

    /**
     * Return the result if the represented operation was successful or return the result of the given function.
     * In the latter case the failure is passed to the function.
     */
    abstract fun getOrMapFailure(f: Function<Throwable, T?>): T?

    /**
     * Returns the failure for a failed result, or [Optional.empty] otherwise.
     */
    abstract val failure: Optional<Throwable>

    /**
     * If the represented operation was successful, returns the result of applying the given
     * `Try`-bearing mapping function to the value, otherwise returns
     * the `Try` representing the original failure.
     *
     * Exceptions thrown by the given function are propagated.
     */
    abstract fun <U : Any?> flatMap(f: Function<in T?, Try<U?>>): Try<U?>?

    /**
     * If the represented operation was successful, returns the result of applying the given
     * mapping function to the value, otherwise returns
     * the `Try` representing the original failure.
     *
     * This is similar to [.tryMap] but propagates any exception the given function throws.
     */
    abstract fun <U : Any?> map(f: Function<in T?, U?>): Try<U?>?

    /**
     * If the represented operation was successful, returns the result of applying the given
     * mapping function to the value, otherwise returns
     * the `Try` representing the original failure.
     *
     * This is similar to [.map] but converts any exception the given function
     * throws into a failed `Try`.
     */
    abstract fun <U : Any?> tryMap(f: Function<in T?, U?>): Try<U?>?

    /**
     * If the represented operation was successful, returns the original result,
     * otherwise returns the given mapping function applied to the failure.
     */
    abstract fun mapFailure(f: Function<in Throwable, out Throwable>): Try<T?>?

    /**
     * Calls the given consumer with the result iff the represented operation was successful.
     */
    abstract fun ifSuccessful(consumer: Consumer<T?>)

    /**
     * Calls {successConsumer} with the result if the represented operation was successful,
     * otherwise calls {failureConsumer} with the failure.
     */
    abstract fun ifSuccessfulOrElse(successConsumer: Consumer<in T?>, failureConsumer: Consumer<in Throwable>)

    private class Success<T : Any?>(private val value: T?) : Try<T?>() {
        override val isSuccessful: Boolean
            get() = true

        override val failure: Optional<Throwable>
            get() = Optional.empty<Throwable>()

        override fun get(): T? {
            return value
        }

        override fun getOrMapFailure(f: Function<Throwable, T?>): T? {
            return value
        }

        override fun <U : Any?> flatMap(f: Function<in T?, Try<U?>>): Try<U?> {
            return f.apply(value)
        }

        override fun <U : Any?> map(f: Function<in T?, U?>): Try<U?> {
            return successful<U?>(f.apply(value))
        }

        override fun <U : Any?> tryMap(f: Function<in T?, U?>): Try<U?> {
            return ofFailable<U?>(Callable { f.apply(value) })
        }

        override fun mapFailure(f: Function<in Throwable, out Throwable>): Try<T?> {
            return this
        }

        override fun ifSuccessful(consumer: Consumer<T?>) {
            consumer.accept(value)
        }

        override fun ifSuccessfulOrElse(successConsumer: Consumer<in T?>, failureConsumer: Consumer<in Throwable>) {
            successConsumer.accept(value)
        }

        override fun equals(o: Any?): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }

            val success = o as Success<*>

            return value == success.value
        }

        override fun hashCode(): Int {
            return Objects.hashCode(value)
        }

        override fun toString(): String {
            return "Successful(" + value + ")"
        }
    }

    private class Failure<T>(failure: Throwable) : Try<T?>() {
        private val failureValue: Throwable

        init {
            this.failureValue = Objects.requireNonNull<Throwable>(failure, "null failure is not allowed")
        }

        override val isSuccessful: Boolean
            get() = false

        override val failure: Optional<Throwable>
            get() = Optional.of<Throwable>(failureValue)

        override fun get(): T? {
            // TODO Merge back with org.gradle.internal.UncheckedException.throwAsUncheckedException()
            //      once it's extracted from :base-services
            if (failureValue is InterruptedException) {
                Thread.currentThread().interrupt()
            }
            if (failureValue is RuntimeException) {
                throw failureValue as RuntimeException
            }
            if (failureValue is Error) {
                throw failureValue as Error
            }
            if (failureValue is IOException) {
                throw UncheckedException.throwAsUncheckedException(failureValue)
            }
            throw RuntimeException(failureValue)
        }

        override fun getOrMapFailure(f: Function<Throwable, T?>): T? {
            return f.apply(failureValue)
        }

        override fun <U : Any?> flatMap(f: Function<in T?, Try<U?>>): Try<U?> {
            return this as Try<U?>
        }

        override fun <U : Any?> map(f: Function<in T?, U?>): Try<U?> {
            return this as Try<U?>
        }

        override fun <U : Any?> tryMap(f: Function<in T?, U?>): Try<U?> {
            return this as Try<U?>
        }

        override fun mapFailure(f: Function<in Throwable, out Throwable>): Try<T?> {
            return failure<T?>(f.apply(failureValue))
        }

        override fun ifSuccessful(consumer: Consumer<T?>) {
        }

        override fun ifSuccessfulOrElse(successConsumer: Consumer<in T?>, failureConsumer: Consumer<in Throwable>) {
            failureConsumer.accept(failureValue)
        }

        override fun equals(o: Any?): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }

            val failure1 = o as Failure<*>

            return failureValue == failure1.failureValue
        }

        override fun hashCode(): Int {
            return failureValue.hashCode()
        }

        override fun toString(): String {
            return "Failed(" + failureValue + ")"
        }
    }

    companion object {
        /**
         * Construct a `Try` by executing the given operation.
         * The returned object will either hold the result or the exception thrown during the operation.
         * If the callable returns null, then the returned Try instance will hold null as its value.
         */
        @JvmStatic
        fun <U : Any?> ofFailable(failable: Callable<U?>): Try<U?> {
            try {
                return successful<U?>(failable.call())
            } catch (e: Exception) {
                return failure<U?>(e)
            }
        }

        /**
         * Construct a `Try` representing a successful execution.
         * The returned object will hold the given result.
         * If the result is null, then the returned Try instance will hold null as its value.
         */
        @JvmStatic
        fun <U : Any?> successful(result: U?): Try<U?> {
            return Success<U?>(result)
        }

        /**
         * Construct a `Try` representing a failed execution.
         * The returned object will hold the given failure.
         */
        @JvmStatic
        fun <U : Any?> failure(failure: Throwable?): Try<U?> {
            return Failure<U?>(Objects.requireNonNull<Throwable>(failure, "null failure is not allowed"))
        }
    }
}
