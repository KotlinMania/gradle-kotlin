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
package org.gradle.internal.component.external.model

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.artifacts.capability.CapabilitySelector
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.capabilities.Capability
import org.gradle.api.internal.artifacts.ImmutableVersionConstraint
import org.gradle.api.internal.artifacts.capability.DefaultSpecificCapabilitySelector
import org.gradle.api.internal.artifacts.capability.FeatureCapabilitySelector
import org.gradle.api.internal.artifacts.capability.SpecificCapabilitySelector
import org.gradle.api.internal.artifacts.component.ComponentSelectorInternal
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.capabilities.ImmutableCapability

class DefaultModuleComponentSelector private constructor(
    private val moduleIdentifier: ModuleIdentifier,
    private val versionConstraint: ImmutableVersionConstraint,
    private val attributes: ImmutableAttributes,
    private val capabilitySelectors: ImmutableSet<CapabilitySelector?>
) : ModuleComponentSelector, ComponentSelectorInternal {
    private val hashCode: Int

    init {
        // Do NOT change the order of members used in hash code here, it's been empirically
        // tested to reduce the number of collisions on a large dependency graph (performance test)
        this.hashCode = computeHashcode(moduleIdentifier, versionConstraint, attributes, capabilitySelectors)
    }

    private fun computeHashcode(module: ModuleIdentifier, version: ImmutableVersionConstraint, attributes: ImmutableAttributes, capabilitySelectors: ImmutableSet<CapabilitySelector?>): Int {
        var hashCode = version.hashCode()
        hashCode = 31 * hashCode + module.hashCode()
        hashCode = 31 * hashCode + attributes.hashCode()
        hashCode = 31 * hashCode + capabilitySelectors.hashCode()
        return hashCode
    }

    override fun getDisplayName(): String {
        val group = moduleIdentifier.getGroup()
        val module = moduleIdentifier.getName()
        val builder = StringBuilder(group.length + module.length + versionConstraint.getRequiredVersion().length + 2)
        builder.append(group)
        builder.append(":")
        builder.append(module)
        val versionString = versionConstraint.getDisplayName()
        if (versionString.length > 0) {
            builder.append(":")
            builder.append(versionString)
        }
        return builder.toString()
    }

    override fun getGroup(): String {
        return moduleIdentifier.getGroup()
    }

    override fun getModule(): String {
        return moduleIdentifier.getName()
    }

    override fun getVersion(): String {
        return versionConstraint.getRequiredVersion()
    }

    override fun getVersionConstraint(): VersionConstraint {
        return versionConstraint
    }

    override fun getModuleIdentifier(): ModuleIdentifier {
        return moduleIdentifier
    }

    @Suppress("deprecation")
    override fun getRequestedCapabilities(): MutableList<Capability?> {
        return capabilitySelectors.stream()
            .map<ImmutableCapability?> { c: CapabilitySelector? ->
                if (c is SpecificCapabilitySelector) {
                    return@map (c as DefaultSpecificCapabilitySelector).getBackingCapability()
                } else if (c is FeatureCapabilitySelector) {
                    return@map DefaultImmutableCapability(
                        getGroup(),
                        getModule() + "-" + c.getFeatureName(),
                        getVersion()
                    )
                } else {
                    throw UnsupportedOperationException("Unsupported capability selector type: " + c!!.javaClass.getName())
                }
            }
            .collect(ImmutableList.toImmutableList<Capability?>())
    }

    override fun getCapabilitySelectors(): ImmutableSet<CapabilitySelector?> {
        return capabilitySelectors
    }

    override fun getAttributes(): ImmutableAttributes {
        return attributes
    }

    override fun matchesStrictly(identifier: ComponentIdentifier): Boolean {
        if (identifier is ModuleComponentIdentifier) {
            val moduleComponentIdentifier = identifier
            return moduleIdentifier.getName() == moduleComponentIdentifier.getModule()
                    && moduleIdentifier.getGroup() == moduleComponentIdentifier.getGroup()
                    && getVersion() == moduleComponentIdentifier.getVersion()
        }

        return false
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val that = o as DefaultModuleComponentSelector

        if (hashCode != that.hashCode) {
            return false
        }

        if (moduleIdentifier != that.moduleIdentifier) {
            return false
        }
        if (versionConstraint != that.versionConstraint) {
            return false
        }
        if (attributes != that.attributes) {
            return false
        }
        return capabilitySelectors == that.capabilitySelectors
    }

    override fun hashCode(): Int {
        return hashCode
    }

    override fun toString(): String {
        return getDisplayName()
    }

    companion object {
        fun newSelector(id: ModuleIdentifier, version: VersionConstraint, attributes: AttributeContainer, capabilitySelectors: MutableSet<CapabilitySelector?>): ModuleComponentSelector {
            return DefaultModuleComponentSelector(
                id,
                DefaultImmutableVersionConstraint.of(version),
                (attributes as AttributeContainerInternal).asImmutable(),
                ImmutableSet.copyOf<CapabilitySelector?>(capabilitySelectors)
            )
        }

        @JvmStatic
        fun newSelector(id: ModuleIdentifier, version: VersionConstraint): ModuleComponentSelector {
            return DefaultModuleComponentSelector(id, DefaultImmutableVersionConstraint.of(version), ImmutableAttributes.EMPTY, ImmutableSet.of<CapabilitySelector?>())
        }

        @JvmStatic
        fun newSelector(id: ModuleIdentifier, version: String?): ModuleComponentSelector {
            return DefaultModuleComponentSelector(id, DefaultImmutableVersionConstraint.of(version), ImmutableAttributes.EMPTY, ImmutableSet.of<CapabilitySelector?>())
        }

        @JvmStatic
        fun withAttributes(selector: ModuleComponentSelector?, attributes: ImmutableAttributes): ModuleComponentSelector {
            val cs = selector as DefaultModuleComponentSelector
            return DefaultModuleComponentSelector(
                cs.moduleIdentifier,
                cs.versionConstraint,
                attributes,
                cs.capabilitySelectors
            )
        }

        fun withCapabilities(selector: ModuleComponentSelector?, capabilitySelectors: ImmutableSet<CapabilitySelector?>): ComponentSelector {
            val cs = selector as DefaultModuleComponentSelector
            return DefaultModuleComponentSelector(
                cs.moduleIdentifier,
                cs.versionConstraint,
                cs.attributes,
                ImmutableSet.copyOf<CapabilitySelector?>(capabilitySelectors)
            )
        }
    }
}
