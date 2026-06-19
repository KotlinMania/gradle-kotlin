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
import java.util.function.Function

/**
 * A variant of [Function] that is allowed to throw [IOException].
 */
fun interface IoFunction<T : Any?, R : Any?> {
    @Throws(IOException::class)
    fun apply(t: T?): R?

    companion object {
        /**
         * Wraps an [IOException]-throwing [IoFunction] into a regular [Function].
         *
         * Any `IOException`s are rethrown as [UncheckedIOException].
         */
        @JvmStatic
        fun <T : Any?, R : Any?> wrap(function: IoFunction<T?, R?>): java.util.function.Function<T?, R?> {
            return Function { t: T? ->
                try {
                    return@Function function.apply(t)
                } catch (e: IOException) {
                    throw UncheckedException.throwAsUncheckedException(e)
                }
            }
        }
    }
}
