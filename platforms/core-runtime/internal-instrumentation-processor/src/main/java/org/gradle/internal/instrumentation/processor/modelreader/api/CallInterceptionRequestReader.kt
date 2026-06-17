/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.internal.instrumentation.processor.modelreader.api

import com.google.common.base.Preconditions
import org.gradle.internal.instrumentation.model.CallInterceptionRequest
import java.util.Optional
import java.util.function.Function

interface CallInterceptionRequestReader<T> {
    /**
     * @param input the input context to read
     * @param context the context that is shared between request reads, can be used for caching
     */
    fun readRequest(input: T?, context: ReadRequestContext?): MutableCollection<Result?>?

    class ReadRequestContext {
        private val store: MutableMap<String?, Any?> = HashMap<String?, Any?>()

        fun <T> computeIfAbsent(key: String?, function: Function<String?, T?>): T? {
            return store.computeIfAbsent(key) { `__`: String? -> Preconditions.checkNotNull<T?>(function.apply(key)) } as T
        }

        fun <T> get(key: String?): Optional<T?> {
            return Optional.ofNullable<T?>(store.get(key) as T?)
        }
    }

    interface Result {
        class Success(@JvmField val request: CallInterceptionRequest?) : Result

        class InvalidRequest(@JvmField val reason: String?) : Result
    }
}
