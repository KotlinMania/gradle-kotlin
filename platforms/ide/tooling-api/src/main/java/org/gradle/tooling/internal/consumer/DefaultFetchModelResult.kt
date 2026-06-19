/*
 * Copyright 2025 the original author or authors.
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
package org.gradle.tooling.internal.consumer

import com.google.common.collect.ImmutableList
import org.gradle.internal.lazy.Lazy
import org.gradle.internal.lazy.Lazy.Companion.locking
import org.gradle.tooling.Failure
import org.gradle.tooling.FetchModelResult
import org.gradle.tooling.internal.consumer.parameters.BuildProgressListenerAdapter
import org.gradle.tooling.internal.protocol.InternalFailure
import org.jspecify.annotations.NullMarked
import java.util.function.Supplier

@NullMarked
class DefaultFetchModelResult<M> private constructor(override val model: M?, failures: Supplier<MutableCollection<out Failure>>) : FetchModelResult<M?> {
    private val failuresSupplier: Lazy<MutableCollection<out Failure>?>

    init {
        this.failuresSupplier = locking().of<MutableCollection<out Failure>?>(failures as Supplier<MutableCollection<out Failure>?>)
    }



    override val failures: MutableCollection<out Failure>
        get() = failuresSupplier.get()!!

    companion object {
        fun <M> of(model: M?, failures: MutableCollection<out InternalFailure>): DefaultFetchModelResult<M?> {
            return DefaultFetchModelResult<M?>(model, Supplier { BuildProgressListenerAdapter.toFailures(failures) })
        }

        fun <M> success(model: M?): DefaultFetchModelResult<M?> {
            return DefaultFetchModelResult<M?>(model, Supplier { ImmutableList.of() })
        }

        fun <M> failure(exception: Exception): DefaultFetchModelResult<M?> {
            return DefaultFetchModelResult<M?>(null, Supplier { ImmutableList.of<DefaultFailure>(DefaultFailure.Companion.fromThrowable(exception)) })
        }
    }
}
