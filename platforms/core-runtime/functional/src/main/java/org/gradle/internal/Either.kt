/*
 * Copyright 2021 the original author or authors.
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

import java.util.Optional
import java.util.function.Consumer
import java.util.function.Function

/**
 * Represents values with two possibilities.
 *
 * @param <L> the left type.
 * @param <R> the right type.
</R></L> */
abstract class Either<L, R> {
    /**
     * Take the value if this is a left.
     */
    @JvmField
    abstract val left: Optional<L?>?

    /**
     * Take the value if this is a right.
     */
    @JvmField
    abstract val right: Optional<R?>?

    /**
     * Map the left side.
     *
     * @see .mapRight
     *
     * @see .fold
     */
    abstract fun <U, V> mapLeft(f: Function<in L?, out U>): Either<U?, V?>?

    /**
     * Map the right side.
     *
     * @see .mapLeft
     *
     * @see .fold
     */
    abstract fun <U, V> mapRight(f: Function<in R?, out V>): Either<U?, V?>?

    /**
     * Apply the respective function and return its result.
     */
    abstract fun <U> fold(l: Function<in L?, out U>, r: Function<in R?, out U>): U?

    /**
     * Apply the respective consumer.
     */
    abstract fun apply(l: Consumer<in L?>, r: Consumer<in R?>)

    abstract override fun equals(obj: Any?): Boolean

    abstract override fun hashCode(): Int

    abstract override fun toString(): String

    private class Left<L, R>(private val value: L?) : Either<L?, R?>() {
        override fun getLeft(): Optional<L?> {
            return Optional.of<L?>(value!!)
        }

        override fun getRight(): Optional<R?> {
            return Optional.empty<R?>()
        }

        override fun <U, V> mapLeft(f: Function<in L?, out U>): Either<U?, V?> {
            return Left<U?, V?>(f.apply(value))
        }

        override fun <U, V> mapRight(f: Function<in R?, out V>): Either<U?, V?> {
            return this as Either<U?, V?>
        }

        override fun <U> fold(l: Function<in L?, out U>, r: Function<in R?, out U>): U? {
            return l.apply(value)
        }

        override fun apply(l: Consumer<in L?>, r: Consumer<in R?>) {
            l.accept(value)
        }

        override fun equals(o: Any?): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }
            return value == (o as Left<*, *>).value
        }

        override fun hashCode(): Int {
            return value.hashCode()
        }

        override fun toString(): String {
            return "Left(" + value + ")"
        }
    }

    private class Right<L, R>(private val value: R?) : Either<L?, R?>() {
        override fun getLeft(): Optional<L?> {
            return Optional.empty<L?>()
        }

        override fun getRight(): Optional<R?> {
            return Optional.of<R?>(value!!)
        }

        override fun <U, V> mapLeft(f: Function<in L?, out U>): Either<U?, V?> {
            return this as Either<U?, V?>
        }

        override fun <U, V> mapRight(f: Function<in R?, out V>): Either<U?, V?> {
            return Right<U?, V?>(f.apply(value))
        }

        override fun <U> fold(l: Function<in L?, out U>, r: Function<in R?, out U>): U? {
            return r.apply(value)
        }

        override fun apply(l: Consumer<in L?>, r: Consumer<in R?>) {
            r.accept(value)
        }

        override fun equals(o: Any?): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }
            return value == (o as Right<*, *>).value
        }

        override fun hashCode(): Int {
            return value.hashCode()
        }

        override fun toString(): String {
            return "Right(" + value + ")"
        }
    }

    companion object {
        fun <L, R> left(value: L?): Either<L?, R?> {
            return Left<L?, R?>(value)
        }

        fun <L, R> right(value: R?): Either<L?, R?> {
            return Right<L?, R?>(value)
        }
    }
}
