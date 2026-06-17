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
package org.gradle.internal.component.external.model

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.NamedVariantIdentifier
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.Factory
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata.isExternalVariant
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata.isExternalVariant
import org.gradle.internal.component.model.ComponentArtifactMetadata.getName
import org.gradle.internal.component.model.ConfigurationGraphResolveMetadata.isVisible
import org.gradle.internal.component.model.ExcludeMetadata
import org.gradle.internal.component.model.ForcingDependencyMetadata
import org.gradle.internal.component.model.VariantGraphResolveMetadata.getAttributes
import org.gradle.internal.component.model.VariantGraphResolveMetadata.getCapabilities
import org.gradle.internal.component.model.VariantGraphResolveMetadata.getName
import org.gradle.internal.component.model.VariantGraphResolveMetadata.isExternalVariant
import org.gradle.internal.component.model.VariantGraphResolveMetadata.isTransitive
import org.gradle.internal.component.model.VariantIdentifier
import org.gradle.internal.component.model.VariantResolveMetadata.isExternalVariant

/**
 * Effectively immutable implementation of ConfigurationMetadata.
 * Used to represent Ivy and Maven modules in the dependency graph.
 */
class DefaultConfigurationMetadata : AbstractConfigurationMetadata {
    private val componentMetadataRules: VariantMetadataRules

    private var calculatedDependencies: MutableList<out ModuleDependencyMetadata?>? = null
    private var calculatedArtifacts: ImmutableList<out ModuleComponentArtifactMetadata?>? = null

    // Could be precomputed, but we avoid doing so if attributes are never requested
    private var computedAttributes: ImmutableAttributes? = null
    private var computedCapabilities: ImmutableCapabilities? = null

    // Fields used for performance optimizations: we avoid computing the derived dependencies (withConstraints, withoutConstraints, ...)
    // eagerly because it's very likely that those methods would only be called on the selected variant. Therefore it's a waste of time
    // to compute them eagerly when those filtering methods are called. We cannot use a dedicated, lazy wrapper over configuration metadata
    // because we need the attributes to be computes lazily too, because of component metadata rules.
    private val dependencyFilter: DependencyFilter
    private var filteredConfigDependencies: ImmutableList<ModuleDependencyMetadata>? = null

    constructor(
        name: String?,
        id: VariantIdentifier?,
        componentId: ModuleComponentIdentifier?,
        transitive: Boolean,
        visible: Boolean,
        hierarchy: ImmutableSet<String?>?,
        artifacts: ImmutableList<out ModuleComponentArtifactMetadata?>?,
        componentMetadataRules: VariantMetadataRules,
        excludes: ImmutableList<ExcludeMetadata?>?,
        componentLevelAttributes: ImmutableAttributes?,
        externalVariant: Boolean
    ) : super(
        name,
        id,
        componentId,
        transitive,
        visible,
        artifacts,
        hierarchy,
        excludes,
        componentLevelAttributes,
        null as ImmutableList<ModuleDependencyMetadata?>?,
        ImmutableCapabilities.Companion.EMPTY,
        externalVariant
    ) {
        this.componentMetadataRules = componentMetadataRules
        this.dependencyFilter = DependencyFilter.ALL
    }

    private constructor(
        name: String?,
        id: VariantIdentifier?,
        componentId: ModuleComponentIdentifier?,
        transitive: Boolean,
        visible: Boolean,
        hierarchy: ImmutableSet<String?>?,
        artifacts: ImmutableList<out ModuleComponentArtifactMetadata?>?,
        componentMetadataRules: VariantMetadataRules,
        excludes: ImmutableList<ExcludeMetadata?>?,
        attributes: ImmutableAttributes?,
        configDependenciesFactory: Factory<MutableList<ModuleDependencyMetadata?>?>?,
        dependencyFilter: DependencyFilter,
        capabilities: ImmutableCapabilities?,
        externalVariant: Boolean
    ) : super(name, id, componentId, transitive, visible, artifacts, hierarchy, excludes, attributes, configDependenciesFactory, capabilities, externalVariant) {
        this.componentMetadataRules = componentMetadataRules
        this.dependencyFilter = dependencyFilter
    }

    val attributes: ImmutableAttributes?
        get() {
            if (computedAttributes == null) {
                computedAttributes = componentMetadataRules.applyVariantAttributeRules(this, super.getAttributes())
            }
            return computedAttributes
        }

    override fun getConfigDependencies(): ImmutableList<ModuleDependencyMetadata>? {
        if (filteredConfigDependencies != null) {
            return filteredConfigDependencies
        }
        var filtered = super.getConfigDependencies()
        when (dependencyFilter) {
            DependencyFilter.CONSTRAINTS_ONLY, DependencyFilter.FORCED_CONSTRAINTS_ONLY -> filtered = withConstraints(true, filtered)
            DependencyFilter.DEPENDENCIES_ONLY, DependencyFilter.FORCED_DEPENDENCIES_ONLY -> filtered = withConstraints(false, filtered)
            else -> {}
        }
        when (dependencyFilter) {
            DependencyFilter.FORCED_ALL, DependencyFilter.FORCED_CONSTRAINTS_ONLY, DependencyFilter.FORCED_DEPENDENCIES_ONLY -> filtered = force(filtered)
            else -> {}
        }
        filteredConfigDependencies = filtered
        return filteredConfigDependencies
    }

    private val originalArtifacts: ImmutableList<out ModuleComponentArtifactMetadata?>?
        get() = super.getArtifacts()

    val artifacts: ImmutableList<out ModuleComponentArtifactMetadata?>
        get() {
            if (calculatedArtifacts == null) {
                calculatedArtifacts = componentMetadataRules.applyVariantFilesMetadataRulesToArtifacts(this, this.originalArtifacts, getComponentId())
            }
            return calculatedArtifacts
        }

    val dependencies: MutableList<out ModuleDependencyMetadata?>?
        get() {
            if (calculatedDependencies == null) {
                calculatedDependencies = componentMetadataRules.applyDependencyMetadataRules<ModuleDependencyMetadata?>(this, getConfigDependencies())
            }
            return calculatedDependencies
        }

    val capabilities: ImmutableCapabilities?
        get() {
            if (computedCapabilities == null) {
                computedCapabilities = componentMetadataRules.applyCapabilitiesRules(this, super.getCapabilities())
            }
            return computedCapabilities
        }

    private val rawCapabilities: ImmutableCapabilities?
        get() =// We need the raw capabilities when deriving a variant since we pass down the component metadata rules as well
            super.getCapabilities()

    private fun lazyConfigDependencies(): Factory<MutableList<ModuleDependencyMetadata?>?> {
        return object : Factory<MutableList<ModuleDependencyMetadata?>?> {
            override fun create(): MutableList<ModuleDependencyMetadata> {
                return super@DefaultConfigurationMetadata.getConfigDependencies()
            }
        }
    }

    private fun force(configDependencies: ImmutableList<ModuleDependencyMetadata>): ImmutableList<ModuleDependencyMetadata> {
        val dependencies = ImmutableList.Builder<ModuleDependencyMetadata?>()
        for (configDependency in configDependencies) {
            if (configDependency is ForcingDependencyMetadata) {
                dependencies.add((configDependency as ForcingDependencyMetadata).forced() as ModuleDependencyMetadata?)
            } else {
                dependencies.add(ForcedDependencyMetadataWrapper(configDependency))
            }
        }
        return dependencies.build()
    }

    private fun withConstraints(constraint: Boolean, configDependencies: ImmutableList<ModuleDependencyMetadata>): ImmutableList<ModuleDependencyMetadata> {
        if (configDependencies.isEmpty()) {
            return ImmutableList.of<ModuleDependencyMetadata?>()
        }
        var count = 0
        var filtered: ImmutableList.Builder<ModuleDependencyMetadata?>? = null
        for (configDependency in configDependencies) {
            if (configDependency.isConstraint === constraint) {
                if (filtered == null) {
                    filtered = ImmutableList.Builder<ModuleDependencyMetadata?>()
                }
                filtered.add(configDependency)
                count++
            }
        }
        if (count == configDependencies.size) {
            // Avoid creating a copy if the resulting configuration is identical
            return configDependencies
        }
        return if (filtered == null) ImmutableList.of<ModuleDependencyMetadata?>() else filtered.build()
    }

    fun mutate(): Builder {
        return DefaultConfigurationMetadata.Builder()
    }

    private enum class DependencyFilter {
        ALL,
        CONSTRAINTS_ONLY,
        DEPENDENCIES_ONLY,
        FORCED_ALL,
        FORCED_CONSTRAINTS_ONLY,
        FORCED_DEPENDENCIES_ONLY;

        fun forcing(): DependencyFilter {
            when (this) {
                DependencyFilter.ALL -> return DependencyFilter.FORCED_ALL
                DependencyFilter.CONSTRAINTS_ONLY -> return DependencyFilter.FORCED_CONSTRAINTS_ONLY
                DependencyFilter.DEPENDENCIES_ONLY -> return DependencyFilter.FORCED_DEPENDENCIES_ONLY
                else -> {}
            }
            return this
        }

        fun dependenciesOnly(): DependencyFilter {
            when (this) {
                DependencyFilter.ALL -> return DependencyFilter.DEPENDENCIES_ONLY
                DependencyFilter.FORCED_ALL -> return DependencyFilter.FORCED_DEPENDENCIES_ONLY
                DependencyFilter.DEPENDENCIES_ONLY -> return this
                else -> {}
            }
            throw IllegalStateException("Cannot set dependencies only when constraints only has already been called")
        }

        fun constraintsOnly(): DependencyFilter {
            when (this) {
                DependencyFilter.ALL -> return DependencyFilter.CONSTRAINTS_ONLY
                DependencyFilter.FORCED_ALL -> return DependencyFilter.FORCED_CONSTRAINTS_ONLY
                DependencyFilter.CONSTRAINTS_ONLY -> return this
                else -> {}
            }
            throw IllegalStateException("Cannot set constraints only when dependencies only has already been called")
        }
    }

    inner class Builder {
        private var name: String = this@DefaultConfigurationMetadata.getName()
        private var dependencyFilter = DependencyFilter.ALL
        private var capabilities: ImmutableCapabilities? = null
        private var attributes: ImmutableAttributes? = null
        private var artifacts: ImmutableList<out ModuleComponentArtifactMetadata?>? = null

        fun withName(name: String): Builder {
            this.name = name
            return this
        }

        fun withArtifacts(artifacts: ImmutableList<out ModuleComponentArtifactMetadata?>?): Builder {
            this.artifacts = artifacts
            return this
        }

        fun withAttributes(attributes: ImmutableAttributes?): Builder {
            this.attributes = attributes
            return this
        }

        fun withoutConstraints(): Builder {
            dependencyFilter = dependencyFilter.dependenciesOnly()
            return this
        }

        fun withForcedDependencies(): Builder {
            dependencyFilter = dependencyFilter.forcing()
            return this
        }

        fun withConstraintsOnly(): Builder {
            dependencyFilter = dependencyFilter.constraintsOnly()
            artifacts = ImmutableList.of<ModuleComponentArtifactMetadata?>()
            return this
        }

        fun withCapabilities(capabilities: ImmutableCapabilities?): Builder {
            this.capabilities = capabilities
            return this
        }

        fun build(): DefaultConfigurationMetadata {
            val id: VariantIdentifier = NamedVariantIdentifier(getId()!!.componentId, name)
            return DefaultConfigurationMetadata(
                name,
                id,
                getComponentId(),
                isTransitive(),
                isVisible(),
                getHierarchy(),
                if (artifacts == null) this@DefaultConfigurationMetadata.originalArtifacts else artifacts,
                componentMetadataRules,
                getExcludes(),
                if (attributes == null) super@DefaultConfigurationMetadata.getAttributes() else attributes,
                lazyConfigDependencies(),
                dependencyFilter,
                if (capabilities == null) this@DefaultConfigurationMetadata.rawCapabilities else capabilities,
                isExternalVariant()
            )
        }
    }
}
