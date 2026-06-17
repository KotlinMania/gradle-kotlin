/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.api.reporting.components.internal

import org.gradle.api.Named

object RendererUtils {
    /**
     * Converts a value into a string. Never returns `null`.
     *
     *
     * Rules used to determine the string value:
     *
     *
     *  * the `null` value is converted to the string `"null"`,
     *  * if the value has a type that overrides [Object.toString] then it is converted to `value.toString()`,
     *  * if the value's type implements [Named] then it is converted to `value.getName()`,
     *  * otherwise the return value of [Object.toString] is used.
     *
     *
     *
     * The method returns the converted value, unless it is `null`, in which case the string `"null"` is returned instead.
     */
    fun displayValueOf(value: Any?): String {
        var result: String? = null
        if (value != null) {
            var hasCustomToString: Boolean
            try {
                val toString = value.javaClass.getMethod("toString")
                hasCustomToString = toString.getDeclaringClass() != Any::class.java
            } catch (ignore: NoSuchMethodException) {
                hasCustomToString = false
            }

            if (!hasCustomToString && Named::class.java.isAssignableFrom(value.javaClass)) {
                result = (value as Named).getName()
            } else {
                result = value.toString()
            }
        }
        if (result == null) {
            result = "null"
        }
        return result
    }
}
