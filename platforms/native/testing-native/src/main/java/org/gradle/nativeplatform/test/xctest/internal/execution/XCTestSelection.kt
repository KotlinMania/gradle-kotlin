/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.nativeplatform.test.xctest.internal.execution

import com.google.common.collect.ImmutableList
import org.apache.commons.lang3.StringUtils

/**
 * Describes the set of filtered XCTests.
 *
 * NOTE: Eventually we want to support regular Java-like test filtering, like filtering for a set of test cases
 * or test suites that match a particular pattern.  Unfortunately, XCTest is very limited with how much up-front
 * test discovery we can do and the kind of filtering we can specify from the command-line.  This class reflects
 * those limitations.
 */
class XCTestSelection(includedTests: MutableCollection<String>, includedTestsCommandLine: MutableCollection<String>) {
    private val includedTests: MutableSet<String?> = LinkedHashSet<String?>()

    init {
        val testSuiteCache: MutableSet<String?> = HashSet<String?>()

        prepareIncludedTestList(includedTests, testSuiteCache)
        prepareIncludedTestList(includedTestsCommandLine, testSuiteCache)

        removeLogicalDuplication(testSuiteCache)

        includeAllTestIfEmpty()
    }

    private fun removeLogicalDuplication(testSuiteCache: MutableSet<String?>) {
        val it = includedTests.iterator()
        while (it.hasNext()) {
            val includedTest = it.next()
            if (isIncludedTestCase(includedTest)) {
                if (testSuiteCache.contains(getTestSuiteName(includedTest))) {
                    it.remove()
                }
            }
        }
    }

    private fun includeAllTestIfEmpty() {
        if (includedTests.isEmpty()) {
            includedTests.add(INCLUDE_ALL_TESTS)
        }
    }

    private fun prepareIncludedTestList(testFilters: MutableCollection<String>, testSuiteCache: MutableSet<String?>) {
        for (testFilter in testFilters) {
            includedTests.add(prepareIncludedTest(disallowForwardSlash(testFilter), testSuiteCache))
        }
    }

    private fun disallowForwardSlash(testFilter: String): String {
        require(!testFilter.contains("/")) { String.format("'%s' is an invalid pattern. Patterns cannot contain forward slash.", testFilter) }
        return testFilter
    }

    private fun prepareIncludedTest(testFilter: String?, testSuiteCache: MutableSet<String?>): String? {
        val tokens = StringUtils.splitPreserveAllTokens(testFilter, '.')
        require(tokens.size <= 3) { String.format("'%s' is an invalid pattern. Patterns should have one or two dots.", testFilter) }
        if (tokens.size == 3) {
            if (WILDCARD == tokens[2]) {
                val filter = tokens[0] + "." + tokens[1]
                testSuiteCache.add(filter)
                return filter
            } else if (tokens[2]!!.isEmpty()) {
                return testFilter
            }
            return tokens[0] + "." + tokens[1] + "/" + tokens[2]
        } else if (tokens.size == 2 && WILDCARD != tokens[1]) {
            testSuiteCache.add(testFilter)
        }

        return testFilter
    }

    fun getIncludedTests(): MutableCollection<String?> {
        return ImmutableList.copyOf<String?>(includedTests)
    }

    companion object {
        const val INCLUDE_ALL_TESTS: String = "All"
        private const val WILDCARD = "*"
        private fun isIncludedTestCase(includedTest: String): Boolean {
            return includedTest.contains("/")
        }

        private fun getTestSuiteName(includedTestCase: String?): String? {
            return StringUtils.split(includedTestCase, '/')[0]
        }
    }
}
