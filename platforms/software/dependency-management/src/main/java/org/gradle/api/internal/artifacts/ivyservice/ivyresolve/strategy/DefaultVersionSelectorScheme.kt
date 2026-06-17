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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy

class DefaultVersionSelectorScheme(private val versionComparator: VersionComparator, private val versionParser: VersionParser?) : VersionSelectorScheme {
    override fun parseSelector(selectorString: String): VersionSelector {
        if (VersionRangeSelector.Companion.ALL_RANGE.matcher(selectorString).matches()) {
            return maybeCreateRangeSelector(selectorString)
        }

        if (isSubVersion(selectorString)) {
            return SubVersionSelector(selectorString)
        }

        if (isLatestVersion(selectorString)) {
            return LatestVersionSelector(selectorString)
        }

        return ExactVersionSelector(selectorString)
    }

    private fun maybeCreateRangeSelector(selectorString: String?): VersionSelector {
        val rangeSelector = VersionRangeSelector(selectorString, versionComparator.asVersionComparator(), versionParser)
        if (isSingleVersionRange(rangeSelector)) {
            // it's a single version range, like [1.0] or [1.0, 1.0]
            return ExactVersionSelector(rangeSelector.getUpperBound())
        }
        return rangeSelector
    }

    override fun renderSelector(selector: VersionSelector): String? {
        return selector.getSelector()
    }

    override fun complementForRejection(selector: VersionSelector?): VersionSelector {
        return InverseVersionSelector(selector)
    }

    companion object {
        private fun isSingleVersionRange(rangeSelector: VersionRangeSelector): Boolean {
            val lowerBound = rangeSelector.getLowerBound()
            return lowerBound != null &&
                    lowerBound == rangeSelector.getUpperBound() &&
                    rangeSelector.isLowerInclusive() && rangeSelector.isUpperInclusive()
        }

        @JvmStatic
        fun isSubVersion(selectorString: String): Boolean {
            return selectorString.endsWith("+")
        }

        @JvmStatic
        fun isLatestVersion(selectorString: String): Boolean {
            return selectorString.startsWith("latest.")
        }
    }
}
