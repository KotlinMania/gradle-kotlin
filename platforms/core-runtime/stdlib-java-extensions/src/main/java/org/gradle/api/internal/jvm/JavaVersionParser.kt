/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.api.internal.jvm

/**
 * Parses the major version, as an integer, from the string returned by the `java.version` system property.
 */
object JavaVersionParser {
    @JvmStatic
    fun parseCurrentMajorVersion(): Int {
        return parseMajorVersion(System.getProperty("java.version"))
    }

    @JvmStatic
    fun parseMajorVersion(fullVersion: String): Int {
        val firstNonVersionCharIndex = findFirstNonVersionCharIndex(fullVersion)

        val versionStrings = fullVersion.substring(0, firstNonVersionCharIndex).split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val versions = convertToNumber(fullVersion, versionStrings)

        if (isLegacyVersion(versions)) {
            assertTrue(fullVersion, versions.get(1)!! > 0)
            return versions.get(1)!!
        } else {
            return versions.get(0)!!
        }
    }

    private fun assertTrue(value: String?, condition: Boolean) {
        require(condition) { "Could not determine Java version from '" + value + "'." }
    }

    private fun isLegacyVersion(versions: MutableList<Int?>): Boolean {
        return 1 == versions.get(0) && versions.size > 1
    }

    private fun convertToNumber(value: String?, versionStrs: Array<String>): MutableList<Int?> {
        val result: MutableList<Int?> = ArrayList<Int?>()
        for (s in versionStrs) {
            assertTrue(value, !isNumberStartingWithZero(s))
            try {
                result.add(s.toInt())
            } catch (e: NumberFormatException) {
                assertTrue(value, false)
            }
        }
        assertTrue(value, !result.isEmpty() && result.get(0)!! > 0)
        return result
    }

    private fun isNumberStartingWithZero(number: String): Boolean {
        return number.length > 1 && number.startsWith("0")
    }

    private fun findFirstNonVersionCharIndex(s: String): Int {
        assertTrue(s, !s.isEmpty())

        for (i in 0..<s.length) {
            if (!isDigitOrPeriod(s.get(i))) {
                assertTrue(s, i != 0)
                return i
            }
        }

        return s.length
    }

    private fun isDigitOrPeriod(c: Char): Boolean {
        return (c >= '0' && c <= '9') || c == '.'
    }
}
