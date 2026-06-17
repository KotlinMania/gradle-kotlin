/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.publish.internal

import org.gradle.api.InvalidUserDataException

abstract class PublicationFieldValidator<T : PublicationFieldValidator<T?>?>(
    private val type: Class<T?>,
    protected val publicationName: String?,
    protected val name: String?,
    protected val value: String?
) {
    fun notNull(): T? {
        if (value == null) {
            val message = String.format("%s cannot be null.", name)
            throw failure(message)
        }
        return type.cast(this)
    }

    fun notEmpty(): T? {
        notNull()
        if (value!!.length == 0) {
            throw failure(String.format("%s cannot be empty.", name))
        }
        return type.cast(this)
    }

    fun validInFileName(): T? {
        if (value == null || value.length == 0) {
            return type.cast(this)
        }
        doesNotContainSpecialCharacters(false)
        return type.cast(this)
    }

    fun doesNotContainSpecialCharacters(allowSlash: Boolean): T? {
        if (value == null || value.length == 0) {
            return type.cast(this)
        }
        // Iterate over unicode characters
        var offset = 0
        while (offset < value.length) {
            val unicodeChar = value.codePointAt(offset)
            if (Character.isISOControl(unicodeChar)) {
                throw failure(String.format("%s cannot contain ISO control character '\\u%04x'.", name, unicodeChar))
            }
            if ('\\'.code == unicodeChar || ('/'.code == unicodeChar && !allowSlash)) {
                throw failure(String.format("%s cannot contain '%c'.", name, unicodeChar.toChar()))
            }
            offset += Character.charCount(unicodeChar)
        }
        return type.cast(this)
    }

    fun optionalNotEmpty(): T? {
        if (value != null && value.length == 0) {
            throw failure(String.format("%s cannot be an empty string. Use null instead.", name))
        }
        return type.cast(this)
    }

    protected abstract fun failure(message: String?): InvalidUserDataException?
}
