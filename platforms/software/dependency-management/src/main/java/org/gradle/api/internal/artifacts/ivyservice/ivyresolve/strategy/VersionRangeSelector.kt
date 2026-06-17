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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy

import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Matches version ranges:
 *
 *  * [1.0,2.0] matches all versions greater or equal to 1.0 and lower or equal to 2.0
 *  * [1.0,2.0[ matches all versions greater or equal to 1.0 and lower than 2.0
 *  * ]1.0,2.0] matches all versions greater than 1.0 and lower or equal to 2.0
 *  * ]1.0,2.0[ matches all versions greater than 1.0 and lower than 2.0
 *  * [1.0,) matches all versions greater or equal to 1.0
 *  * ]1.0,) matches all versions greater than 1.0
 *  * (,2.0] matches all versions lower or equal to 2.0
 *  * (,2.0[matches all versions lower than 2.0
 *
 * This class uses a latest strategy to compare revisions. If
 * none is set, it uses the default one of the ivy instance set through setIvy(). If neither a
 * latest strategy nor a ivy instance is set, an IllegalStateException will be thrown when calling
 * accept(). Note that it can't work with latest time strategy, cause no time is known for the
 * limits of the range. Therefore only purely revision based LatestStrategy can be used.
 */
class VersionRangeSelector(selector: String, private val comparator: Comparator<Version?>, versionParser: VersionParser) : AbstractVersionVersionSelector(versionParser, selector) {
    val upperBound: String?
    val upperBoundVersion: Version?
    val isUpperInclusive: Boolean
    val lowerBound: String?
    val isLowerInclusive: Boolean
    val lowerBoundVersion: Version?

    init {
        var matcher: Matcher?
        matcher = FINITE_RANGE.matcher(selector)
        if (matcher.matches()) {
            lowerBound = matcher.group(1)
            this.isLowerInclusive = selector.startsWith(OPEN_INC)
            upperBound = matcher.group(2)
            this.isUpperInclusive = selector.endsWith(CLOSE_INC)
        } else {
            matcher = LOWER_INFINITE_RANGE.matcher(selector)
            if (matcher.matches()) {
                lowerBound = null
                this.isLowerInclusive = true
                upperBound = matcher.group(1)
                this.isUpperInclusive = selector.endsWith(CLOSE_INC)
            } else {
                matcher = UPPER_INFINITE_RANGE.matcher(selector)
                if (matcher.matches()) {
                    lowerBound = matcher.group(1)
                    this.isLowerInclusive = selector.startsWith(OPEN_INC)
                    upperBound = null
                    this.isUpperInclusive = true
                } else {
                    matcher = SINGLE_VALUE_RANGE.matcher(selector)
                    if (matcher.matches()) {
                        lowerBound = matcher.group(1)
                        this.isLowerInclusive = true
                        upperBound = lowerBound
                        this.isUpperInclusive = true
                    } else {
                        throw IllegalArgumentException("Not a version range selector: " + selector)
                    }
                }
            }
        }
        lowerBoundVersion = if (lowerBound == null) null else versionParser.transform(lowerBound)
        upperBoundVersion = if (upperBound == null) null else versionParser.transform(upperBound)
    }

    override fun isDynamic(): Boolean {
        return true
    }

    override fun requiresMetadata(): Boolean {
        return false
    }

    override fun matchesUniqueVersion(): Boolean {
        return false
    }

    override fun accept(candidate: Version): Boolean {
        if (lowerBound != null && !isHigher(candidate, lowerBoundVersion, this.isLowerInclusive)) {
            return false
        }
        return upperBound == null || isLower(candidate, upperBoundVersion!!, this.isUpperInclusive)
    }

    /**
     * Tells if version1 is lower than version2.
     */
    private fun isLower(version1: Version, version2: Version, inclusive: Boolean): Boolean {
        val result = comparator.compare(version1, version2)
        if (inclusive) {
            return result <= 0
        } else {
            // For non inclusive upper bound, we also check that the prefix does not match
            if (result <= -1) {
                return !version1.toString().startsWith(version2.toString())
            } else {
                return false
            }
        }
    }

    /**
     * Tells if version1 is higher than version2.
     */
    private fun isHigher(version1: Version?, version2: Version?, inclusive: Boolean): Boolean {
        val result = comparator.compare(version1, version2)
        return result >= (if (inclusive) 0 else 1)
    }

    override fun toString(): String {
        return getSelector()
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val that = o as VersionRangeSelector

        if (this.isUpperInclusive != that.isUpperInclusive) {
            return false
        }
        if (this.isLowerInclusive != that.isLowerInclusive) {
            return false
        }
        if (upperBound != that.upperBound) {
            return false
        }
        if (upperBoundVersion != that.upperBoundVersion) {
            return false
        }
        if (lowerBound != that.lowerBound) {
            return false
        }
        if (lowerBoundVersion != that.lowerBoundVersion) {
            return false
        }
        return comparator == that.comparator
    }

    override fun hashCode(): Int {
        var result = if (upperBound != null) upperBound.hashCode() else 0
        result = 31 * result + (if (upperBoundVersion != null) upperBoundVersion.hashCode() else 0)
        result = 31 * result + (if (this.isUpperInclusive) 1 else 0)
        result = 31 * result + (if (lowerBound != null) lowerBound.hashCode() else 0)
        result = 31 * result + (if (this.isLowerInclusive) 1 else 0)
        result = 31 * result + (if (lowerBoundVersion != null) lowerBoundVersion.hashCode() else 0)
        result = 31 * result + comparator.hashCode()
        return result
    }

    companion object {
        private const val OPEN_INC = "["

        private const val OPEN_EXC = "]"
        private const val OPEN_EXC_MAVEN = "("

        private const val CLOSE_INC = "]"

        private const val CLOSE_EXC = "["
        private const val CLOSE_EXC_MAVEN = ")"

        private const val LOWER_INFINITE = "("

        private const val UPPER_INFINITE = ")"

        private const val SEPARATOR = ","

        // following patterns are built upon constants above and should not be modified
        private val OPEN_INC_PATTERN = "\\" + OPEN_INC

        private val OPEN_EXC_PATTERN = "\\" + OPEN_EXC + "\\" + OPEN_EXC_MAVEN

        private val CLOSE_INC_PATTERN = "\\" + CLOSE_INC

        private val CLOSE_EXC_PATTERN = "\\" + CLOSE_EXC + "\\" + CLOSE_EXC_MAVEN

        private val LI_PATTERN = "\\" + LOWER_INFINITE

        private val UI_PATTERN = "\\" + UPPER_INFINITE

        private val SEP_PATTERN = "\\s*\\" + SEPARATOR + "\\s*"

        private val OPEN_PATTERN = "[" + OPEN_INC_PATTERN + OPEN_EXC_PATTERN + "]"

        private val CLOSE_PATTERN = "[" + CLOSE_INC_PATTERN + CLOSE_EXC_PATTERN + "]"

        private val ANY_NON_SPECIAL_PATTERN = ("[^\\s" + SEPARATOR + OPEN_INC_PATTERN
                + OPEN_EXC_PATTERN + CLOSE_INC_PATTERN + CLOSE_EXC_PATTERN + LI_PATTERN + UI_PATTERN
                + "]")

        private val FINITE_PATTERN: String = (OPEN_PATTERN + "\\s*(" + ANY_NON_SPECIAL_PATTERN
                + "+)" + SEP_PATTERN + "(" + ANY_NON_SPECIAL_PATTERN + "+)\\s*" + CLOSE_PATTERN)

        private val LOWER_INFINITE_PATTERN: String = (LI_PATTERN + SEP_PATTERN + "("
                + ANY_NON_SPECIAL_PATTERN + "+)\\s*" + CLOSE_PATTERN)

        private val UPPER_INFINITE_PATTERN: String = (OPEN_PATTERN + "\\s*("
                + ANY_NON_SPECIAL_PATTERN + "+)" + SEP_PATTERN + UI_PATTERN)

        private val SINGLE_VALUE_PATTERN: String = OPEN_INC_PATTERN + "\\s*(" + ANY_NON_SPECIAL_PATTERN + "+)" + CLOSE_INC_PATTERN

        private val FINITE_RANGE: Pattern = Pattern.compile(FINITE_PATTERN)

        private val LOWER_INFINITE_RANGE: Pattern = Pattern.compile(LOWER_INFINITE_PATTERN)

        private val UPPER_INFINITE_RANGE: Pattern = Pattern.compile(UPPER_INFINITE_PATTERN)

        private val SINGLE_VALUE_RANGE: Pattern = Pattern.compile(SINGLE_VALUE_PATTERN)

        val ALL_RANGE: Pattern = Pattern.compile(
            (FINITE_PATTERN + "|"
                    + LOWER_INFINITE_PATTERN + "|" + UPPER_INFINITE_PATTERN + "|" + SINGLE_VALUE_RANGE)
        )
    }
}
