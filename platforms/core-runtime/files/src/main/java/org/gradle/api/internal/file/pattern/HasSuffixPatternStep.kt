/*
 * Copyright 2014 the original author or authors.
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

/**
 * A pattern step for a pattern segment a the common case with a '*' prefix on the pattern. e.g. '*.java'
 */
class HasSuffixPatternStep internal constructor(private val suffix: String, private val caseSensitive: Boolean, private val prefixLength: Int) : PatternStep {
    private val suffixLength: Int

    constructor(suffix: String, caseSensitive: Boolean) : this(suffix, caseSensitive, 0)

    // Used by HasPrefixAndSuffixPatternStep to ensure the suffix isn't matching any part of the prefix.
    init {
        suffixLength = suffix.length
    }

    override fun toString(): String {
        return "{suffix: " + suffix + "}"
    }

    override fun matches(candidate: String): Boolean {
        return isLongEnough(candidate) && candidate.regionMatches(candidate.length - suffixLength, suffix, 0, suffixLength, ignoreCase = !caseSensitive)
    }

    // Confirms there is enough space in candidate to fit both suffix and prefix.
    private fun isLongEnough(candidate: String): Boolean {
        return prefixLength + suffixLength <= candidate.length
    }
}
