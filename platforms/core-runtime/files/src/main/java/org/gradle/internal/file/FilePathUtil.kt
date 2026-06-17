/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.internal.file

import java.io.File
import java.util.Arrays
import java.util.StringTokenizer
import kotlin.collections.ArrayList
import kotlin.collections.contentEquals
import kotlin.math.min

object FilePathUtil {
    // On Windows, / and \ are separators, on Unix only / is a separator.
    private val FILE_PATH_SEPARATORS: String = if (File.separatorChar != '/') ("/" + File.separator) else File.separator

    fun getPathSegments(path: String): Array<String> {
        val tokenizer = StringTokenizer(path, FILE_PATH_SEPARATORS)
        val segments: MutableList<String> = ArrayList<String>()
        while (tokenizer.hasMoreElements()) {
            segments.add(tokenizer.nextToken())
        }
        return segments.toTypedArray()
    }

    /**
     * Does not include the separator char.
     */
    /**
     * Does not include the file separator.
     */
    @JvmOverloads
    fun sizeOfCommonPrefix(path1: String, path2: String, offset: Int, separatorChar: Char = File.separatorChar): Int {
        var pos = 0
        var lastSeparator = 0
        val maxPos = min(path1.length, path2.length - offset)
        while (pos < maxPos) {
            if (path1.get(pos) != path2.get(pos + offset)) {
                break
            }
            if (path1.get(pos) == separatorChar) {
                lastSeparator = pos
            }
            pos++
        }
        if (pos == maxPos) {
            if (path1.length == path2.length - offset) {
                return pos
            }
            if (pos < path1.length && path1.get(pos) == separatorChar) {
                return pos
            }
            if (pos < path2.length - offset && path2.get(pos + offset) == separatorChar) {
                return pos
            }
        }
        return lastSeparator
    }

    /**
     * Removes the trailing segments from the given path if it ends with the specified path to remove.
     */
    fun maybeRemoveTrailingSegments(path: String, pathToRemove: String): String {
        val removalSegments = getPathSegments(pathToRemove)
        val pathSegments = getPathSegments(path)
        val potentialRemovalIndex = pathSegments.size - removalSegments.size
        // Check if the path ends with the removal path.
        if (potentialRemovalIndex > 0) {
            val lastSegments = Arrays.copyOfRange(pathSegments, potentialRemovalIndex, pathSegments.size)

            if (lastSegments.contentEquals(removalSegments)) {
                // If it does, we remove the segments from the path
                // i.e: <rootPath>/<removalPath> becomes just <rootPath>
                val maybeLeadingSeparator = if (FILE_PATH_SEPARATORS.contains(path.substring(0, 1))) "/" else ""
                return maybeLeadingSeparator + pathSegments.copyOfRange(0, potentialRemovalIndex).joinToString("/")
            }
        }
        // Otherwise, we return the original path.
        return path
    }
}
