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

object PatternStepFactory {
    private val ANY_WILDCARD_PATTERN_STEP = AnyWildcardPatternStep()

    @JvmStatic
    fun getStep(source: String, caseSensitive: Boolean): PatternStep {
        if (source.length == 0) {
            return FixedPatternStep(source, caseSensitive)
        }

        // Here, we try to avoid using the reg exp backed pattern step, as it is expensive in terms of performance and heap usage.
        // There are several special cases we handle here:
        // 1. '*'
        // 2. '*' <literal>
        // 3. <literal> '*'
        // 4. <literal> '*' <literal>
        // 5. <literal>
        // Everything else uses a reg exp.

        // Not empty: may match any case above
        var ch = source.get(0)
        var endPrefixWildcard = 0
        if (ch == '*') {
            endPrefixWildcard = 1
            while (endPrefixWildcard < source.length && source.get(endPrefixWildcard) == '*') {
                endPrefixWildcard++
            }
        }

        if (endPrefixWildcard == source.length) {
            // Only * characters: matches #1 above
            return ANY_WILDCARD_PATTERN_STEP
        }

        // Zero or more * characters followed by at least one !*
        var endLiteral = endPrefixWildcard
        while (endLiteral < source.length) {
            ch = source.get(endLiteral)
            if (ch == '?') {
                // No matches - fall back to regexp
                return RegExpPatternStep(source, caseSensitive)
            }
            if (ch == '*') {
                break
            }
            endLiteral++
        }
        if (endLiteral == source.length) {
            if (endPrefixWildcard == 0) {
                // No wildcards: matches #5 above
                return FixedPatternStep(source, caseSensitive)
            }
            // One or more '*' followed by one or more non-wildcard: matches #2 above
            return HasSuffixPatternStep(source.substring(endPrefixWildcard), caseSensitive)
        }

        // Zero or more * characters followed by literal followed by at least one *
        if (endPrefixWildcard > 0) {
            // No matches - fall back to regexp
            return RegExpPatternStep(source, caseSensitive)
        }

        // literal followed by at least one *
        var endSuffixWildcard = endLiteral
        while (endSuffixWildcard < source.length) {
            ch = source.get(endSuffixWildcard)
            if (ch != '*') {
                break
            }
            endSuffixWildcard++
        }

        if (endSuffixWildcard == source.length) {
            // Literal followed by *: matches #3 above
            return HasPrefixPatternStep(source.substring(0, endLiteral), caseSensitive)
        }

        for (i in endSuffixWildcard..<source.length) {
            ch = source.get(i)
            if (ch == '?' || ch == '*') {
                // No matches - fall back to regexp
                return RegExpPatternStep(source, caseSensitive)
            }
        }

        // literal followed by * followed by literal: matches #4 above
        return HasPrefixAndSuffixPatternStep(source.substring(0, endLiteral), source.substring(endSuffixWildcard), caseSensitive)
    }
}
