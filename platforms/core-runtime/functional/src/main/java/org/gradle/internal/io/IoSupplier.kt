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
package org.gradle.internal.io

import org.gradle.internal.UncheckedException
import java.io.IOException
import java.util.function.Supplier

/**
 * A variant of [Supplier] that is allowed to throw [IOException].
 */
fun interface IoSupplier<T> {
    @Throws(IOException::class)
    fun get(): T?

    companion object {
        /**
         * Wraps an [IOException]-throwing [IoSupplier] into a regular [Supplier].
         *
         * Any `IOException`s are rethrown as [UncheckedIOException].
         */
        fun <T> wrap(supplier: IoSupplier<T?>): Supplier<T?> {
            return Supplier {
                try {
                    return@Supplier supplier.get()
                } catch (e: IOException) {
                    throw UncheckedException.throwAsUncheckedException(e)
                }
            }
        }
    }
}
