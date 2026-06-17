/*
 * Copyright 2022 the original author or authors.
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
package org.gradle.internal.enterprise.exceptions

import org.gradle.internal.scan.UsedByScanPlugin
import org.gradle.internal.serialize.PlaceholderAssertionError
import org.gradle.internal.serialize.PlaceholderException
import org.gradle.internal.serialize.PlaceholderExceptionSupport

@UsedByScanPlugin
object PlaceholderExceptions {
    @UsedByScanPlugin
    fun getExceptionClassName(t: Throwable): String {
        if (t is PlaceholderExceptionSupport) {
            return t.exceptionClassName!!
        } else {
            return t.javaClass.getName()
        }
    }

    @UsedByScanPlugin
    fun createException(
        originalClassName: String,
        message: String?,
        getMessageException: Throwable?,
        toString: String?,
        toStringException: Throwable?,
        cause: Throwable?
    ): Throwable {
        return PlaceholderException(originalClassName, message, getMessageException, toString, toStringException, cause)
    }

    @UsedByScanPlugin
    fun createAssertionError(
        originalClassName: String,
        message: String?,
        getMessageException: Throwable?,
        toString: String?,
        toStringException: Throwable?,
        cause: Throwable?
    ): Throwable {
        return PlaceholderAssertionError(originalClassName, message, getMessageException, toString, toStringException, cause)
    }
}
