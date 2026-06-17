/*
 * Copyright 2013 the original author or authors.
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

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import org.gradle.api.artifacts.capability.CapabilitySelector
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentSelector
import org.gradle.api.capabilities.Capability
import org.gradle.api.internal.artifacts.DefaultProjectComponentIdentifier
import org.gradle.api.internal.artifacts.ProjectComponentIdentifierInternal
import org.gradle.api.internal.artifacts.capability.DefaultSpecificCapabilitySelector
import org.gradle.api.internal.artifacts.capability.SpecificCapabilitySelector
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.capabilities.ImmutableCapability
import org.gradle.api.internal.project.ProjectIdentity
import org.gradle.util.Path

class DefaultProjectComponentSelector(
    projectIdentity: ProjectIdentity,
    attributes: ImmutableAttributes,
    capabilitySelectors: ImmutableSet<CapabilitySelector>
) : ProjectComponentSelectorInternal {
    private val projectIdentity: ProjectIdentity
    private val attributes: ImmutableAttributes
    private val capabilitySelectors: ImmutableSet<CapabilitySelector>

    private val hashCode: Int

    init {
        this.projectIdentity = projectIdentity
        this.attributes = attributes
        this.capabilitySelectors = capabilitySelectors
        this.hashCode = computeHashCode(attributes, capabilitySelectors, projectIdentity)
    }

    override fun getProjectIdentity(): ProjectIdentity {
        return projectIdentity
    }

    override fun getDisplayName(): String {
        return projectIdentity.getDisplayName()
    }

    override fun getBuildPath(): String {
        return projectIdentity.getBuildPath().asString()
    }

    override fun getIdentityPath(): Path {
        return projectIdentity.getBuildTreePath()
    }

    override fun getProjectPath(): String {
        return projectIdentity.getProjectPath().asString()
    }

    override fun matchesStrictly(identifier: ComponentIdentifier): Boolean {
        checkNotNull(identifier) { "identifier cannot be null" }

        if (identifier is ProjectComponentIdentifier) {
            val projectComponentIdentifier = identifier as ProjectComponentIdentifierInternal
            return projectComponentIdentifier.getProjectIdentity() == projectIdentity
        }

        return false
    }

    override fun getAttributes(): ImmutableAttributes {
        return attributes
    }

    @Suppress("deprecation")
    override fun getRequestedCapabilities(): MutableList<Capability> {
        return capabilitySelectors.stream()
            .filter { c: CapabilitySelector? -> c is SpecificCapabilitySelector }
            .map<ImmutableCapability> { c: CapabilitySelector? -> (c as DefaultSpecificCapabilitySelector).getBackingCapability() }
            .collect(ImmutableList.toImmutableList<Capability>())
    }

    override fun getCapabilitySelectors(): ImmutableSet<CapabilitySelector> {
        return capabilitySelectors
    }

    override fun equals(o: Any): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val that = o as DefaultProjectComponentSelector
        return projectIdentity == that.projectIdentity &&
                attributes == that.attributes &&
                capabilitySelectors == that.capabilitySelectors
    }

    override fun hashCode(): Int {
        return hashCode
    }

    override fun toString(): String {
        return getDisplayName()
    }

    // TODO: It seems fishy to be able to go directly from a selector to an identifier.
    // There should be some registry involved here.
    fun toIdentifier(): ProjectComponentIdentifier {
        return DefaultProjectComponentIdentifier(projectIdentity)
    }

    companion object {
        private fun computeHashCode(
            attributes: ImmutableAttributes,
            capabilitySelectors: ImmutableSet<CapabilitySelector>,
            projectIdentity: ProjectIdentity
        ): Int {
            var result = projectIdentity.hashCode()
            result = 31 * result + attributes.hashCode()
            result = 31 * result + capabilitySelectors.hashCode()
            return result
        }

        fun withAttributes(selector: ProjectComponentSelector, attributes: ImmutableAttributes): ProjectComponentSelector {
            val current = selector as ProjectComponentSelectorInternal
            return DefaultProjectComponentSelector(
                current.getProjectIdentity(),
                attributes,
                current.getCapabilitySelectors()
            )
        }

        fun withCapabilities(selector: ProjectComponentSelector, capabilitySelectors: ImmutableSet<CapabilitySelector>): ProjectComponentSelector {
            val current = selector as ProjectComponentSelectorInternal
            return DefaultProjectComponentSelector(
                current.getProjectIdentity(),
                current.getAttributes(),
                capabilitySelectors
            )
        }

        fun withAttributesAndCapabilities(selector: ProjectComponentSelector, attributes: ImmutableAttributes, capabilitySelectors: ImmutableSet<CapabilitySelector>): ProjectComponentSelector {
            val current = selector as ProjectComponentSelectorInternal
            return DefaultProjectComponentSelector(
                current.getProjectIdentity(),
                attributes,
                capabilitySelectors
            )
        }
    }
}
