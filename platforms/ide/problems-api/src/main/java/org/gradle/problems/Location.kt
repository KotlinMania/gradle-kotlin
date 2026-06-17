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
package org.gradle.problems

import org.gradle.internal.DisplayName

/**
 * A source file location.
 */
class Location(
    /**
     * Returns a long display name for the source file containing this location. The long description should use absolute paths and assume no particular context.
     */
    @JvmField val sourceLongDisplayName: DisplayName,
    /**
     * Returns a short display name for the source file containing this location. The short description may use relative paths.
     */
    val sourceShortDisplayName: DisplayName, val filePath: String, @JvmField val lineNumber: Int
) {
    val formatted: String
        get() = sourceLongDisplayName.getCapitalizedDisplayName() + ": line " + lineNumber

    override fun toString(): String {
        return this.formatted
    }
}
