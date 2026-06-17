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
package org.gradle.api.internal.artifacts.dependencies

import com.google.common.base.Strings
import com.google.common.collect.ImmutableList
import org.gradle.api.Action
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.MutableVersionConstraint
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.artifacts.capability.CapabilitySelector
import org.gradle.api.capabilities.Capability
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ModuleVersionSelectorStrictSpec
import org.gradle.api.internal.artifacts.capability.DefaultSpecificCapabilitySelector
import org.gradle.api.internal.artifacts.capability.FeatureCapabilitySelector
import org.gradle.api.internal.artifacts.capability.SpecificCapabilitySelector
import org.gradle.api.internal.capabilities.ImmutableCapability
import org.gradle.internal.component.external.model.DefaultImmutableCapability

abstract class AbstractExternalModuleDependency : AbstractModuleDependency, ExternalModuleDependency {
    private val moduleIdentifier: ModuleIdentifier
    private var changing = false
    private val versionConstraint: MutableVersionConstraint

    constructor(module: ModuleIdentifier, version: String, configuration: String?) {
        this.moduleIdentifier = module
        this.versionConstraint = DefaultMutableVersionConstraint(version)
        if (configuration != null) {
            setTargetConfiguration(configuration)
        }
    }

    constructor(module: ModuleIdentifier, version: MutableVersionConstraint, configuration: String?) {
        this.moduleIdentifier = module
        this.versionConstraint = version
        if (configuration != null) {
            setTargetConfiguration(configuration)
        }
    }

    protected open fun copyTo(target: AbstractExternalModuleDependency) {
        super.copyTo(target)
        target.setChanging(isChanging())
    }

    override fun matchesStrictly(identifier: ModuleVersionIdentifier): Boolean {
        return ModuleVersionSelectorStrictSpec(this).isSatisfiedBy(identifier)
    }

    override fun getGroup(): String? {
        return moduleIdentifier.getGroup()
    }

    override fun getName(): String {
        return moduleIdentifier.getName()
    }

    override fun getVersion(): String? {
        val requiredVersion = versionConstraint.getRequiredVersion()
        val version = if (requiredVersion.isEmpty()) versionConstraint.getPreferredVersion() else requiredVersion
        return Strings.emptyToNull(version)
    }

    override fun isForce(): Boolean {
        return false // Enforced Platforms no longer mark force, so there is no way for a dependency to be forced (configurations and resolution strategies are used instead)
    }

    override fun isChanging(): Boolean {
        return changing
    }

    override fun setChanging(changing: Boolean): ExternalModuleDependency {
        validateMutation(this.changing, changing)
        this.changing = changing
        return this
    }

    override fun getVersionConstraint(): VersionConstraint {
        return versionConstraint
    }

    override fun version(configureAction: Action<in MutableVersionConstraint>) {
        validateMutation()
        configureAction.execute(versionConstraint)
    }

    override fun getModule(): ModuleIdentifier {
        return moduleIdentifier
    }

    @Suppress("deprecation")
    override fun getRequestedCapabilities(): MutableList<Capability> {
        return getCapabilitySelectors().stream()
            .map<ImmutableCapability> { c: CapabilitySelector? ->
                if (c is SpecificCapabilitySelector) {
                    return@map (c as DefaultSpecificCapabilitySelector).getBackingCapability()
                } else if (c is FeatureCapabilitySelector) {
                    return@map DefaultImmutableCapability(
                        getGroup()!!,
                        getName() + "-" + c.getFeatureName(),
                        getVersion()
                    )
                } else {
                    throw UnsupportedOperationException("Unsupported capability selector type: " + c!!.javaClass.getName())
                }
            }
            .collect(ImmutableList.toImmutableList<Capability>())
    }

    override fun toString(): String {
        return getGroup() + ":" + getName() + ":" + getVersionConstraint().getDisplayName()
    }

    override fun equals(o: Any): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val that = o as AbstractExternalModuleDependency

        return moduleIdentifier == that.moduleIdentifier &&
                versionConstraint == that.versionConstraint && changing == that.changing &&
                isCommonContentEquals(that)
    }

    override fun hashCode(): Int {
        var result = if (getGroup() != null) getGroup().hashCode() else 0
        result = 31 * result + getName().hashCode()
        result = 31 * result + (if (getVersion() != null) getVersion().hashCode() else 0)
        return result
    }

    companion object {
        fun assertModuleId(group: String?, name: String?): ModuleIdentifier {
            if (name == null) {
                throw InvalidUserDataException("Name must not be null!")
            }
            return DefaultModuleIdentifier.newId(group, name)
        }
    }
}
