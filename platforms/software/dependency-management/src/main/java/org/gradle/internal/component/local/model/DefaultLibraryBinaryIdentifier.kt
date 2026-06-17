/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.internal.component.local.model

import com.google.common.base.Objects
import org.gradle.api.artifacts.component.LibraryBinaryIdentifier

class DefaultLibraryBinaryIdentifier(projectPath: String, libraryName: String, variant: String) : LibraryBinaryIdentifier {
    private val projectPath: String
    private val libraryName: String
    private val displayName: String
    private val variant: String

    override fun getDisplayName(): String {
        return displayName
    }

    init {
        checkNotNull(projectPath) { "project path cannot be null" }
        checkNotNull(libraryName) { "library name cannot be null" }
        checkNotNull(variant) { "variant cannot be null" }
        this.projectPath = projectPath
        this.libraryName = libraryName
        this.variant = variant
        this.displayName = "project '" + projectPath + "' library '" + libraryName + "' variant '" + variant + "'"
    }

    override fun getProjectPath(): String {
        return projectPath
    }

    override fun getLibraryName(): String {
        return libraryName
    }

    override fun getVariant(): String {
        return variant
    }

    override fun equals(o: Any): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val that = o as DefaultLibraryBinaryIdentifier
        return Objects.equal(projectPath, that.projectPath)
                && Objects.equal(libraryName, that.libraryName)
                && Objects.equal(variant, that.variant)
    }

    override fun hashCode(): Int {
        return Objects.hashCode(projectPath, libraryName, variant)
    }

    override fun toString(): String {
        return getDisplayName()
    }
}
