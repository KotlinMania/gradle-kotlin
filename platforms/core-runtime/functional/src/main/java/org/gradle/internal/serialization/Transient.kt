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

import java.io.Serializable

/**
 * A value that gets discarded during serialization.
 */
abstract class Transient<T> : Serializable {
    /**
     * A mutable variable that gets discarded during serialization.
     */
    abstract class Var<T> : Transient<T?>() {
        abstract fun set(value: T?)
    }

    abstract fun get(): T?

    open val isPresent: Boolean
        get() = true

    private class ImmutableTransient<T>(private val value: T?) : Transient<T?>() {
        override fun get(): T? {
            return value
        }

        fun writeReplace(): Any {
            return DISCARDED
        }
    }

    private class MutableTransient<T>(private var value: T?) : Var<T?>() {
        override fun get(): T? {
            return value
        }

        override fun set(value: T?) {
            this.value = value
        }

        fun writeReplace(): Any {
            return DISCARDED
        }
    }

    private class Discarded<T> : Var<T?>() {
        override fun set(value: T?) {
            throw IllegalStateException("The value of this property cannot be set after it has been discarded during serialization.")
        }

        override fun get(): T? {
            throw IllegalStateException("The value of this property has been discarded during serialization.")
        }

        override fun isPresent(): Boolean {
            return false
        }

        fun readResolve(): Any {
            return DISCARDED
        }
    }

    companion object {
        fun <T> of(value: T?): Transient<T?> {
            return ImmutableTransient<T?>(value)
        }

        fun <T> varOf(): Var<T?> {
            return varOf<T?>(null)
        }

        @JvmStatic
        fun <T> varOf(value: T?): Var<T?> {
            return MutableTransient<T?>(value)
        }

        private val DISCARDED: Transient<Any?> = Discarded<Any?>()
    }
}
