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

import com.google.common.collect.ImmutableMap

/**
 * Allows for comparison of Version instances.
 * Note that this comparator only considers the 'parts' of a version, and does not consider the part 'separators'.
 * This means that it considers `1.1.1 == 1-1-1 == 1.1-1`, and should not be used in cases where this is important.
 * One example where this comparator is inappropriate is if versions should be retained in a TreeMap/TreeSet.
 */
internal class StaticVersionComparator : Comparator<Version?> {
    /**
     * Compares 2 versions. Algorithm is inspired by PHP version_compare one.
     */
    override fun compare(version1: Version, version2: Version): Int {
        if (version1 == version2) {
            return 0
        }

        val parts1 = version1.getParts()
        val parts2 = version2.getParts()
        val numericParts1 = version1.getNumericParts()
        val numericParts2 = version2.getNumericParts()

        var i = 0
        while (i < parts1.size && i < parts2.size) {
            val part1 = parts1[i]
            val part2 = parts2[i]

            val numericPart1 = numericParts1[i]
            val numericPart2 = numericParts2[i]

            val is1Number = numericPart1 != null
            val is2Number = numericPart2 != null

            if (part1 == part2) {
                i++
                continue
            }
            if (is1Number && !is2Number) {
                return 1
            }
            if (is2Number && !is1Number) {
                return -1
            }
            if (is1Number && is2Number) {
                val result = numericPart1.compareTo(numericPart2)
                if (result == 0) {
                    i++
                    continue
                }
                return result
            }
            // both are strings, we compare them taking into account special meaning
            val sm1: Int? = SPECIAL_MEANINGS.get(part1.lowercase())
            var sm2: Int? = SPECIAL_MEANINGS.get(part2.lowercase())
            if (sm1 != null) {
                sm2 = if (sm2 == null) 0 else sm2
                return sm1 - sm2
            }
            if (sm2 != null) {
                return -sm2
            }
            return part1.compareTo(part2)
            i++
        }
        if (i < parts1.size) {
            return if (numericParts1[i] == null) -1 else 1
        }
        if (i < parts2.size) {
            return if (numericParts2[i] == null) 1 else -1
        }

        return 0
    }

    companion object {
        val SPECIAL_MEANINGS: MutableMap<String?, Int?> = ImmutableMap.builderWithExpectedSize<String?, Int?>(7)
            .put("dev", -1)
            .put("rc", 1)
            .put("snapshot", 2)
            .put("final", 3).put("ga", 4).put("release", 5)
            .put("sp", 6).build()
    }
}
