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
package org.gradle.internal.serialization

import org.gradle.internal.Try
import org.gradle.internal.evaluation.EvaluationContext
import org.gradle.internal.evaluation.EvaluationOwner
import java.io.Serializable
import java.util.Objects
import java.util.concurrent.Callable
import kotlin.concurrent.Volatile

/**
 * Represents a computation that must execute only once and
 * whose result must be cached even (or specially) at serialization time.
 *
 *
 * Instances of this type are mutable and ARE NOT thread-safe,
 * so should not be used from multiple threads.
 *
 * @param <T> the resulting type
</T> */
abstract class Cached<T> {
    abstract fun get(): T?

    private class Deferred<T>(computation: Callable<T?>) : Cached<T?>(), Serializable, EvaluationOwner {
        // TODO(https://github.com/gradle/gradle/issues/31239) fields are volatile as a workaround for call sites still unwisely using Cached from multiple threads.
        @Volatile
        private var computation: Callable<T?>?

        @Volatile
        private var result: Try<T?>? = null

        init {
            this.computation = computation
        }

        override fun get(): T? {
            return result()!!.get()
        }

        fun result(): Try<T?>? {
            val toCompute = computation
            if (result == null) {
                // copy reference into the call stack to avoid exacerbating https://github.com/gradle/gradle/issues/31239
                result = tryComputation(Objects.requireNonNull<Callable<T?>?>(toCompute))
                computation = null
            }
            return result
        }

        fun tryComputation(toCompute: Callable<T?>?): Try<T?> {
            // wrap computation as an "evaluation" so it can be treated specially as other evaluations
            return EvaluationContext.current().evaluate<R, E?>(this, { Try.ofFailable(toCompute) })
        }

        fun writeReplace(): Any {
            return Cached.Fixed<T?>(result()!!)
        }
    }

    private class Fixed<T>(private val result: Try<T?>) : Cached<T?>() {
        override fun get(): T? {
            return result.get()
        }
    }

    companion object {
        /**
         * Creates a cacheable computation. The returned object IS NOT safe to be used from multiple threads.
         * If an unresolved cached computation is used from multiple threads, not only it does not honor
         * "at-most-once" semantics, but it can fail in unpredictable ways.
         *
         * @see [bug report](https://github.com/gradle/gradle/issues/31239)
         */
        fun <T> of(computation: Callable<T?>): Cached<T?> {
            return Deferred<T?>(computation)
        }
    }
}
