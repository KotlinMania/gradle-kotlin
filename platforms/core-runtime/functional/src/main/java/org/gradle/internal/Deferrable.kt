@file:Suppress("UNCHECKED_CAST")

/*
 * Copyright 2022 the original author or authors.
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

import java.util.Objects
import java.util.Optional
import java.util.function.Function
import java.util.function.Supplier
import kotlin.concurrent.Volatile

private fun <T> optionalOfNullable(value: T?): Optional<T?> = Optional.ofNullable(value) as Optional<T?>

/**
 * An invocation to be executed at most once, but one that can be deferred.
 *
 * @param <T> The type which will be computed.
</T> */
interface Deferrable<T> {
    /**
     * The result of the invocation when it is already available.
     */
    val completed: Optional<T?>

    /**
     * Obtain the result of the invocation, either by returning the already computed result or by computing it synchronously.
     *
     * A result is only calculated once.
     */
    fun completeAndGet(): T?

    /**
     * Maps the result of the invocation via a mapper.
     *
     * @param mapper An inexpensive function on the result.
     * @throws NullPointerException if the mapper maps to `null`.
     */
    fun <U> map(mapper: Function<in T?, U?>): Deferrable<U?> {
        return object : Deferrable<U?> {
            override val completed: Optional<U?>
                get() = this@Deferrable.completed
                    .map<U?>(Function { value: T? -> applyAndRequireNonNull(value, mapper) })

            override fun completeAndGet(): U? {
                return applyAndRequireNonNull(this@Deferrable.completeAndGet(), mapper)
            }

            private fun applyAndRequireNonNull(value: T?, mapper: Function<in T?, U?>): U? {
                val result = mapper.apply(value)
                return Objects.requireNonNull<U?>(result, "Mapping a Deferrable to null is not allowed")
            }
        }
    }

    /**
     * Chains two [Deferrable]s.
     *
     * @param mapper A function which creates the next [Deferrable] from the result of the first one.
     * Creating the invocation may be expensive, so this method avoids calling the mapper twice if possible.
     */
    fun <U> flatMap(mapper: Function<in T?, Deferrable<U?>>): Deferrable<U?> {
        return this.completed
            .map<Deferrable<U?>>(mapper)
            .orElseGet(Supplier {
                deferred<U?>(Supplier {
                    mapper
                        .apply(this@Deferrable.completeAndGet())
                        .completeAndGet()
                })
            })
    }

    companion object {
        /**
         * An already completed result, can be successful or failed.
         */
        fun <T> completed(successfulResult: T?): Deferrable<T?> {
            return object : Deferrable<T?> {
                override val completed: Optional<T?>
                    get() = optionalOfNullable(successfulResult)

                override fun completeAndGet(): T? {
                    return successfulResult
                }
            }
        }

        /**
         * An invocation with no pre-computed result, requiring to do the expensive computation on [.completeAndGet].
         */
        fun <T> deferred(result: Supplier<T?>): Deferrable<T?> {
            return object : Deferrable<T?> {
                @Volatile
                private var value: T? = null

                override val completed: Optional<T?>
                    get() = optionalOfNullable(value)

                // the value is MonotonicNonNull, but IDEA doesn't understand it.
                override fun completeAndGet(): T? {
                    if (value == null) {
                        synchronized(this) {
                            if (value == null) {
                                value = result.get()
                            }
                        }
                    }
                    return value
                }
            }
        }
    }
}
