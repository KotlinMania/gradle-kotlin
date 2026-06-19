/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.internal.lazy

import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier

/**
 * A wrapper around a value computed lazily. Multiple implementations
 * are possible and creating a lazy provider can be done by calling
 * one of the factory methods:
 *
 *  * [.unsafe] would create a lazy wrapper which performs no synchronization at all when calling the supplier: it may be called several times concurrently by different threads. Not thread safe!
 *  * [.locking] would create a lazy wrapper which performs locking when calling the supplier: the supplier will only be called once. Reading is done without locking once initialized.
 *
 *
 * @param <T> the type of the lazy value
</T> */
interface Lazy<T : Any?> : Supplier<T?> {
    /**
     * Executes an operation on the lazily computed value
     *
     * @param consumer the consumer
     */
    fun use(consumer: Consumer<in T?>) {
        consumer.accept(get())
    }

    /**
     * Applies a function to the lazily computed value and returns its value
     *
     * @param function the value to apply to the lazily computed value
     * @param <V> the return type
     * @return the result of the function, applied on the lazily computed value
    </V> */
    fun <V : Any?> apply(function: Function<in T?, V?>): V? {
        return function.apply(get())
    }

    /**
     * Creates another lazy wrapper which will eventually apply the supplied
     * function to the lazily computed value
     *
     * @param mapper the mapping function
     * @param <V> the type of the result of the function
     * @return a new lazy wrapper
    </V> */
    fun <V : Any?> map(mapper: Function<in T?, V?>): Lazy<V?> {
        return unsafe().of<V?>(Supplier { mapper.apply(get()) })
    }

    interface Factory {
        fun <T : Any?> of(supplier: Supplier<T?>): Lazy<T?>
    }

    companion object {
        /**
         * Constructs a lazy value that always returns the given value.
         *
         * @param <V> the type of the lazy value
        </V> */
        @JvmStatic
        fun <V> fixed(value: V?): Lazy<V?> {
            return FixedLazy<V?>(value)
        }

        @JvmStatic
        fun unsafe(): Factory {
            return object : Factory {
                override fun <T : Any?> of(supplier: Supplier<T?>): Lazy<T?> {
                    return UnsafeLazy(supplier)
                }
            }
        }

        /**
         * An atomic [Lazy] allows concurrent access from multiple threads without locking.
         * <br></br>
         * The given `Supplier` might be executed more than once when multiple threads access a
         * non-initialized [Lazy] at the same time but one value will eventually be cached and
         * published to all threads.
         * <br></br>
         * **WARNING:** Given the above, this flavor of `Lazy` initialization should not be used
         * with a `Supplier` that can have undesirable side effects if executed more than once.
         */
        @JvmStatic
        fun atomic(): Factory {
            return object : Factory {
                override fun <T : Any?> of(supplier: Supplier<T?>): Lazy<T?> {
                    return AtomicLazy(supplier)
                }
            }
        }

        @JvmStatic
        fun locking(): Factory {
            return object : Factory {
                override fun <T : Any?> of(supplier: Supplier<T?>): Lazy<T?> {
                    return LockingLazy(supplier)
                }
            }
        }
    }
}
