/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.file.pattern

import java.util.regex.Pattern

class RegExpPatternStep(pattern: String, caseSensitive: Boolean) : PatternStep {
    private val pattern: Pattern

    init {
        this.pattern = Pattern.compile(getRegExPattern(pattern), if (caseSensitive) 0 else Pattern.CASE_INSENSITIVE)
    }

    override fun toString(): String {
        return "{regexp: " + pattern + "}"
    }

    override fun matches(testString: String?): Boolean {
        if (testString == null) {
            return false
        }
        val matcher = pattern.matcher(testString)
        return matcher.matches()
    }

    companion object {
        private const val ESCAPE_CHARS = "\\[]^-&.{}()$+|<=!"

        @JvmStatic
        fun getRegExPattern(pattern: String): String {
            val result = StringBuilder()
            for (i in 0..<pattern.length) {
                val next = pattern.get(i)
                if (next == '*') {
                    result.append(".*")
                } else if (next == '?') {
                    result.append(".")
                } else if (ESCAPE_CHARS.indexOf(next) >= 0) {
                    result.append('\\')
                    result.append(next)
                } else {
                    result.append(next)
                }
            }
            return result.toString()
        }
    }
}
