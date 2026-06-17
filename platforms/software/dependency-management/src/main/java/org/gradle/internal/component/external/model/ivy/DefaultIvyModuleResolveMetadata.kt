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
package org.gradle.internal.component.external.model.ivy

import com.google.common.base.Objects
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.NamedVariantIdentifier
import org.gradle.api.internal.artifacts.ivyservice.NamespaceId
import org.gradle.internal.component.external.descriptor.Artifact
import org.gradle.internal.component.external.descriptor.Configuration
import org.gradle.internal.component.external.model.AbstractLazyModuleComponentResolveMetadata
import org.gradle.internal.component.external.model.DefaultConfigurationMetadata
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector.Companion.newSelector
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata
import org.gradle.internal.component.external.model.VariantDerivationStrategy
import org.gradle.internal.component.external.model.VariantMetadataRules
import org.gradle.internal.component.model.Exclude
import org.gradle.internal.component.model.ModuleSources
import org.gradle.internal.component.model.VariantIdentifier
import org.gradle.util.internal.CollectionUtils.collect
import java.util.IdentityHashMap
import java.util.function.Function

/**
 * [Lazy version][AbstractLazyModuleComponentResolveMetadata] of a [IvyModuleResolveMetadata].
 *
 * @see RealisedIvyModuleResolveMetadata
 */
class DefaultIvyModuleResolveMetadata : AbstractLazyModuleComponentResolveMetadata, IvyModuleResolveMetadata {
    private val configurationDefinitions: ImmutableMap<String, Configuration>
    private val dependencies: ImmutableList<IvyDependencyDescriptor>
    private val artifactDefinitions: ImmutableList<Artifact>
    private val excludes: ImmutableList<Exclude>
    private val extraAttributes: ImmutableMap<NamespaceId, String>
    private val branch: String

    // Since a single `Artifact` is shared between configurations, share the metadata type as well.
    private var artifacts: IdentityHashMap<Artifact, ModuleComponentArtifactMetadata>? = null

    internal constructor(metadata: DefaultMutableIvyModuleResolveMetadata) : super(metadata) {
        this.configurationDefinitions = metadata.configurationDefinitions
        this.branch = metadata.getBranch()!!
        this.artifactDefinitions = metadata.getArtifactDefinitions()
        this.dependencies = metadata.getDependencies()
        this.excludes = metadata.getExcludes()
        this.extraAttributes = metadata.getExtraAttributes()
    }

    private constructor(metadata: DefaultIvyModuleResolveMetadata, sources: ModuleSources, variantDerivationStrategy: VariantDerivationStrategy) : super(metadata, sources, variantDerivationStrategy) {
        this.configurationDefinitions = metadata.configurationDefinitions
        this.branch = metadata.branch
        this.artifactDefinitions = metadata.artifactDefinitions
        this.dependencies = metadata.dependencies
        this.excludes = metadata.excludes
        this.extraAttributes = metadata.extraAttributes

        copyCachedState(metadata, metadata.variantDerivationStrategy !== variantDerivationStrategy)
    }

    private constructor(metadata: DefaultIvyModuleResolveMetadata, dependencies: MutableList<IvyDependencyDescriptor>) : super(metadata, metadata.getSources(), metadata.variantDerivationStrategy) {
        this.configurationDefinitions = metadata.configurationDefinitions
        this.branch = metadata.branch
        this.artifactDefinitions = metadata.artifactDefinitions
        this.dependencies = ImmutableList.copyOf<IvyDependencyDescriptor>(dependencies)
        this.excludes = metadata.excludes
        this.extraAttributes = metadata.extraAttributes

        // Cached state is not copied, since dependency inputs are different.
    }

    override fun createConfiguration(
        componentId: ModuleComponentIdentifier,
        name: String,
        transitive: Boolean,
        visible: Boolean,
        hierarchy: ImmutableSet<String>,
        componentMetadataRules: VariantMetadataRules
    ): DefaultConfigurationMetadata {
        if (artifacts == null) {
            artifacts = IdentityHashMap<Artifact, ModuleComponentArtifactMetadata>()
        }
        val configurationHelper = IvyConfigurationHelper(artifactDefinitions, artifacts!!, excludes, dependencies, componentId)
        val artifacts = configurationHelper.filterArtifacts(name, hierarchy)
        val excludesForConfiguration = configurationHelper.filterExcludes(hierarchy)

        val id: VariantIdentifier = NamedVariantIdentifier(componentId, name)
        val configuration = DefaultConfigurationMetadata(
            name,
            id,
            componentId,
            transitive,
            visible,
            hierarchy,
            ImmutableList.copyOf<ModuleComponentArtifactMetadata>(artifacts),
            componentMetadataRules,
            excludesForConfiguration,
            getAttributes().asImmutable(),
            false
        )
        configuration.setDependencies(configurationHelper.filterDependencies(configuration))
        return configuration
    }

    override fun asMutable(): MutableIvyModuleResolveMetadata {
        return DefaultMutableIvyModuleResolveMetadata(this)
    }

    override fun withSources(sources: ModuleSources): DefaultIvyModuleResolveMetadata {
        return DefaultIvyModuleResolveMetadata(this, sources, variantDerivationStrategy!!)
    }


    override fun withDerivationStrategy(derivationStrategy: VariantDerivationStrategy): ModuleComponentResolveMetadata {
        if (variantDerivationStrategy === derivationStrategy) {
            return this
        }
        return DefaultIvyModuleResolveMetadata(this, getSources(), derivationStrategy)
    }

    override fun getConfigurationDefinitions(): ImmutableMap<String, Configuration> {
        return configurationDefinitions
    }

    override fun getArtifactDefinitions(): ImmutableList<Artifact> {
        return artifactDefinitions
    }

    override fun getExcludes(): ImmutableList<Exclude> {
        return excludes
    }

    override fun getBranch(): String {
        return branch
    }

    override fun getExtraAttributes(): ImmutableMap<NamespaceId, String> {
        return extraAttributes
    }

    override fun withDynamicConstraintVersions(): IvyModuleResolveMetadata {
        val transformed: MutableList<IvyDependencyDescriptor> = collect<IvyDependencyDescriptor?, IvyDependencyDescriptor?>(getDependencies(), Function { dependency: IvyDependencyDescriptor? ->
            val selector = dependency!!.selector
            val dynamicConstraintVersion = dependency.getDynamicConstraintVersion()
            val newSelector = newSelector(selector.getModuleIdentifier(), dynamicConstraintVersion)
            dependency.withRequested(newSelector)
        })
        return this.withDependencies(transformed)
    }

    private fun withDependencies(transformed: MutableList<IvyDependencyDescriptor>): IvyModuleResolveMetadata {
        return DefaultIvyModuleResolveMetadata(this, transformed)
    }

    override fun getDependencies(): ImmutableList<IvyDependencyDescriptor> {
        return dependencies
    }

    override fun equals(o: Any): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        if (!super.equals(o)) {
            return false
        }

        val that = o as DefaultIvyModuleResolveMetadata
        return Objects.equal(dependencies, that.dependencies)
                && Objects.equal(artifactDefinitions, that.artifactDefinitions)
                && Objects.equal(excludes, that.excludes)
                && Objects.equal(extraAttributes, that.extraAttributes)
                && Objects.equal(branch, that.branch)
                && Objects.equal(artifacts, that.artifacts)
    }

    override fun hashCode(): Int {
        return Objects.hashCode(
            super.hashCode(),
            dependencies,
            artifactDefinitions,
            excludes,
            extraAttributes,
            branch,
            artifacts
        )
    }
}
