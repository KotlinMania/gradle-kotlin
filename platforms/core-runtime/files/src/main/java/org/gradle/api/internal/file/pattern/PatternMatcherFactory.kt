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

import com.google.common.annotations.VisibleForTesting
import com.google.common.base.CharMatcher
import com.google.common.base.Splitter
import java.util.ArrayList

object PatternMatcherFactory {
    private val END_OF_PATH_MATCHER = EndOfPathMatcher()
    private val PATH_SPLITTER = Splitter.on(CharMatcher.anyOf("\\/")).omitEmptyStrings()

    @JvmStatic
    fun getPatternsMatcher(partialMatchDirs: Boolean, caseSensitive: Boolean, patterns: Iterable<String>): PatternMatcher {
        var matcher: PatternMatcher = PatternMatcher.Companion.MATCH_ALL
        for (pattern in patterns) {
            val patternMatcher = getPatternMatcher(partialMatchDirs, caseSensitive, pattern)
            matcher = if (matcher === PatternMatcher.Companion.MATCH_ALL)
                patternMatcher
            else
                matcher.or(patternMatcher)
        }
        return matcher
    }

    @JvmStatic
    fun getPatternMatcher(partialMatchDirs: Boolean, caseSensitive: Boolean, pattern: String): PatternMatcher {
        val pathMatcher = compile(caseSensitive, pattern)
        return DefaultPatternMatcher(partialMatchDirs, pathMatcher)
    }

    @JvmStatic
    fun compile(caseSensitive: Boolean, pattern: String): PathMatcher {
        var pattern = pattern
        if (pattern.length == 0) {
            return END_OF_PATH_MATCHER
        }

        // trailing / or \ assumes **
        if (pattern.endsWith("/") || pattern.endsWith("\\")) {
            pattern = pattern + "**"
        }
        val parts = ArrayList<String>(PATH_SPLITTER.splitToList(pattern))
        return compile(parts, 0, caseSensitive)
    }

    private fun compile(parts: List<String>, startIndex: Int, caseSensitive: Boolean): PathMatcher {
        if (startIndex >= parts.size) {
            return END_OF_PATH_MATCHER
        }
        var pos = startIndex
        while (pos < parts.size && parts.get(pos) == "**") {
            pos++
        }
        if (pos > startIndex) {
            if (pos == parts.size) {
                return AnythingMatcher()
            }
            return GreedyPathMatcher(compile(parts, pos, caseSensitive))
        }
        return FixedStepPathMatcher(PatternStepFactory.getStep(parts.get(pos), caseSensitive), compile(parts, pos + 1, caseSensitive))
    }

    @VisibleForTesting
    internal class DefaultPatternMatcher(private val partialMatchDirs: Boolean, @JvmField @get:VisibleForTesting val pathMatcher: PathMatcher) : PatternMatcher() {
        override fun test(segments: Array<String?>?, file: Boolean): Boolean {
            if (file || !partialMatchDirs) {
                return pathMatcher.matches(segments, 0)
            } else {
                return pathMatcher.isPrefix(segments, 0)
            }
        }
    }
}
