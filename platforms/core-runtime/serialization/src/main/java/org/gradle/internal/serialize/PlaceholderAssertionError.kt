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
package org.gradle.internal.serialize

import org.gradle.internal.UncheckedException

/**
 * A `PlaceholderException` is used when an assertion error cannot be serialized or deserialized.
 */
class PlaceholderAssertionError(
    override val exceptionClassName: String?,
    message: String?,
    private val getMessageException: Throwable?,
    private val toString: String?,
    private val toStringRuntimeEx: Throwable?,
    cause: Throwable?
) : AssertionError(message), PlaceholderExceptionSupport {
    init {
        initCause(cause)
    }

    override val message: String?
        get() {
            if (getMessageException != null) {
                throw UncheckedException.throwAsUncheckedException(getMessageException)
            }
            return super.message
        }

    override fun toString(): String {
        if (toStringRuntimeEx != null) {
            throw UncheckedException.throwAsUncheckedException(toStringRuntimeEx)
        }
        return toString!!
    }
}
