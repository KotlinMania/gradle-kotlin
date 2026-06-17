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
import com.google.common.base.Strings
import com.google.common.collect.ImmutableSet
import org.gradle.api.artifacts.capability.CapabilitySelector
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.LibraryBinaryIdentifier
import org.gradle.api.artifacts.component.LibraryComponentSelector
import org.gradle.api.capabilities.Capability
import org.gradle.api.internal.artifacts.component.ComponentSelectorInternal
import org.gradle.api.internal.attributes.ImmutableAttributes

class DefaultLibraryComponentSelector @JvmOverloads constructor(projectPath: String, libraryName: String, variant: String = null) : LibraryComponentSelector, ComponentSelectorInternal {
    private val projectPath: String
    private val libraryName: String
    private val variant: String

    init {
        assert(!Strings.isNullOrEmpty(projectPath)) { "project path cannot be null or empty" }
        this.projectPath = projectPath
        this.libraryName = Strings.emptyToNull(libraryName)!!
        this.variant = variant
    }

    override fun getDisplayName(): String {
        val txt: String
        if (Strings.isNullOrEmpty(libraryName)) {
            txt = "project '" + projectPath + "'"
        } else if (Strings.isNullOrEmpty(variant)) {
            txt = "project '" + projectPath + "' library '" + libraryName + "'"
        } else {
            txt = "project '" + projectPath + "' library '" + libraryName + "' binary '" + variant + "'"
        }
        return txt
    }

    override fun getProjectPath(): String {
        return projectPath
    }

    override fun getLibraryName(): String {
        return libraryName
    }

    override fun getVariant(): String? {
        return variant
    }

    override fun matchesStrictly(identifier: ComponentIdentifier): Boolean {
        checkNotNull(identifier) { "identifier cannot be null" }

        if (identifier is LibraryBinaryIdentifier) {
            val projectComponentIdentifier = identifier
            return Objects.equal(projectComponentIdentifier.getProjectPath(), projectPath)
                    && Objects.equal(projectComponentIdentifier.getLibraryName(), libraryName)
                    && Objects.equal(projectComponentIdentifier.getVariant(), variant)
        }

        return false
    }

    override fun getAttributes(): ImmutableAttributes {
        return ImmutableAttributes.EMPTY
    }

    override fun getRequestedCapabilities(): MutableList<Capability> {
        return mutableListOf<Capability>()
    }

    override fun getCapabilitySelectors(): ImmutableSet<CapabilitySelector> {
        return ImmutableSet.of<CapabilitySelector>()
    }

    override fun equals(o: Any): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val that = o as DefaultLibraryComponentSelector
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
