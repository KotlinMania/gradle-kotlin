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

import java.util.Objects
import java.util.Optional
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate
import java.util.function.Supplier
import java.util.stream.Stream

/**
 * Backport of Java 11 methods to Java 8's [Optional].
 */
class ExtendedOptional<T> private constructor(private val delegate: Optional<T?>) {
    fun get(): T? {
        return delegate.get()
    }

    val isPresent: Boolean
        get() = delegate.isPresent()

    val isEmpty: Boolean
        get() = !delegate.isPresent()

    fun ifPresent(action: Consumer<in T?>) {
        delegate.ifPresent(action)
    }

    @Suppress("unused")
    fun ifPresentOrElse(action: Consumer<in T?>, emptyAction: Runnable) {
        val value = delegate.orElse(null)
        if (value != null) {
            action.accept(value)
        } else {
            emptyAction.run()
        }
    }

    fun filter(predicate: Predicate<in T?>): Optional<T?> {
        return delegate.filter(predicate)
    }

    fun <U> map(mapper: Function<in T?, out U>): Optional<U?> {
        return delegate.map<U?>(mapper)
    }

    fun <U> flatMap(mapper: Function<in T?, out Optional<out U>>): Optional<U?> {
        return delegate.flatMap<U?>(mapper as Function<T?, Optional<U?>?>)
    }

    fun or(supplier: Supplier<out Optional<out T>>): Optional<T?> {
        Objects.requireNonNull(supplier)
        if (this.isPresent) {
            return delegate
        } else {
            val r = supplier.get() as Optional<T?>
            return Objects.requireNonNull<Optional<T?>>(r)
        }
    }

    fun stream(): Stream<T?> {
        val value = delegate.orElse(null)
        if (value != null) {
            return Stream.of<T?>(value)
        } else {
            return Stream.empty<T?>()
        }
    }

    fun orElse(other: T?): T? {
        return delegate.orElse(other)
    }

    fun orElseGet(supplier: Supplier<out T>): T? {
        return delegate.orElseGet(supplier)
    }

    @Suppress("unused")
    fun orElseThrow(): T? {
        val value = delegate.orElse(null)
        if (value == null) {
            throw NoSuchElementException("No value present")
        }
        return value
    }

    @Suppress("unused")
    @Throws(X::class)
    fun <X : Throwable?> orElseThrow(exceptionSupplier: Supplier<out X>): T? {
        return delegate.orElseThrow(exceptionSupplier)
    }

    companion object {
        fun <T> extend(delegate: Optional<T?>): ExtendedOptional<T?> {
            return ExtendedOptional<T?>(delegate)
        }
    }
}
