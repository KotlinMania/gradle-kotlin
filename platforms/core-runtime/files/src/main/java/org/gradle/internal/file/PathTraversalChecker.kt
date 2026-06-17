/*
 * Copyright 2022 the original author or authors.
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

object PathTraversalChecker {
    private val IS_WINDOWS = System.getProperty("os.name").lowercase().contains("windows")

    /**
     * Checks the entry name for path traversal vulnerable sequences.
     *
     * This code is used for path traversal, ZipSlip and TarSlip detection.
     *
     * **IMPLEMENTATION NOTE**
     * We do it this way instead of the way recommended in [](https://snyk.io/research/zip-slip-vulnerability)
     * for performance reasons, calling [File.getCanonicalPath] is too expensive.
     *
     * @throws IllegalArgumentException if the entry contains vulnerable sequences
     */
    @JvmStatic
    fun safePathName(name: String): String {
        require(!isUnsafePathName(name)) { String.format("'%s' is not a safe archive entry or path name.", name) }
        return name
    }

    fun isUnsafePathName(name: String): Boolean {
        if (name.isEmpty()) {
            return true
        }
        if (IS_WINDOWS && name.contains(":")) {
            return true
        }
        if (name.startsWith("/") || name.startsWith("\\")) {
            return true
        }

        return containsDirectoryNavigation(name)
    }

    /**
     * We want to treat both '/' and '\' as path separators on all OSes.
     *
     * @param name the original path name
     * @return the path name with all separators replaced with the OS file separator
     */
    private fun osIndependentPath(name: String): String {
        if (File.separatorChar == '\\') {
            return name.replace('/', File.separatorChar)
        } else if (File.separatorChar == '/') {
            return name.replace('\\', File.separatorChar)
        } else {
            // Throw an error here, as we would want to add this separator to our list
            // rather than passing it through unmodified
            throw IllegalStateException("Unknown file separator: " + File.separatorChar)
        }
    }

    private fun containsDirectoryNavigation(name: String): Boolean {
        val names = buildNamesList(name)
        for (part in names) {
            if (part == "..") {
                return true
            }
            if (IS_WINDOWS) {
                // Directories with dots at the end will have them removed by win32 compatibility
                // We don't know what paths might be directories, so just ban any occurrence of dots at the end
                if (part != "." && part.endsWith(".")) {
                    return true
                }
            }
        }
        return false
    }

    private fun buildNamesList(name: String): MutableList<String> {
        // We run this through File then toPath, as `name` is primarily used with new File(...) calls elsewhere
        // This ensures a consistent parsing/understanding of the path
        val path = File(osIndependentPath(name)).toPath()
        val names: MutableList<String> = ArrayList<String>(path.getNameCount())
        for (part in path) {
            names.add(part.toString())
        }
        return names
    }
}
