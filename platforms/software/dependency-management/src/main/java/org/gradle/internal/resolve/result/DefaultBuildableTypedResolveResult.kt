/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.internal.resolve.result

open class DefaultBuildableTypedResolveResult<T, E : Throwable?> : DefaultResourceAwareResolveResult(), BuildableTypedResolveResult<T?, E?> {
    private var result: T? = null
    private var failure: E? = null

    override fun failed(failure: E?) {
        this.result = null
        this.failure = failure
    }

    override fun resolved(result: T?) {
        this.result = result
        this.failure = null
    }

    @Throws(E::class)
    override fun getResult(): T? {
        assertHasResult()
        if (failure != null) {
            throw failure
        }
        return result
    }

    override fun getFailure(): E? {
        assertHasResult()
        return failure
    }

    val isSuccessful: Boolean
        get() = result != null

    override fun hasResult(): Boolean {
        return failure != null || result != null
    }

    protected fun assertHasResult() {
        check(hasResult()) { "No result has been specified." }
    }
}
