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
package org.gradle.process.internal.util

import com.google.common.base.Strings
import org.gradle.api.InvalidUserDataException
import java.io.File
import kotlin.math.max

object MergeOptionsUtil {
    fun getHeapSizeMb(heapSize: String?): Int {
        if (heapSize == null) {
            return -1 // unspecified
        }

        val normalized = heapSize.trim { it <= ' ' }.lowercase()
        try {
            if (normalized.endsWith("m")) {
                return normalized.substring(0, normalized.length - 1).toInt()
            }
            if (normalized.endsWith("g")) {
                return normalized.substring(0, normalized.length - 1).toInt() * 1024
            }
        } catch (e: NumberFormatException) {
            throw InvalidUserDataException("Cannot parse heap size: " + heapSize, e)
        }
        throw InvalidUserDataException("Cannot parse heap size: " + heapSize)
    }

    @JvmStatic
    fun mergeHeapSize(heapSize1: String?, heapSize2: String?): String? {
        val mergedHeapSizeMb = max(getHeapSizeMb(heapSize1), getHeapSizeMb(heapSize2))
        return if (mergedHeapSizeMb == -1) null else mergedHeapSizeMb.toString() + "m"
    }

    fun canBeMerged(left: String?, right: String?): Boolean {
        if (left == null || right == null) {
            return true
        } else {
            return normalized(left) == normalized(right)
        }
    }

    fun canBeMerged(left: File?, right: File?): Boolean {
        if (left == null || right == null) {
            return true
        } else {
            return left == right
        }
    }

    fun normalized(strings: Iterable<String?>?): MutableSet<String?> {
        val normalized: MutableSet<String?> = LinkedHashSet<String?>()
        if (strings != null) {
            for (string in strings) {
                normalized.add(normalized(string))
            }
        }
        return normalized
    }

    @JvmStatic
    fun normalized(string: String?): String {
        return Strings.nullToEmpty(string).trim { it <= ' ' }
    }

    fun containsAll(left: MutableMap<String?, Any?>, right: MutableMap<String?, Any?>): Boolean {
        for (rightKey in right.keys) {
            if (!normalized(left.keys).contains(normalized(rightKey))) {
                return false
            } else {
                for (leftKey in left.keys) {
                    if (normalized(leftKey) == normalized(rightKey) && left.get(leftKey) != right.get(rightKey)) {
                        return false
                    }
                }
            }
        }
        return true
    }
}
