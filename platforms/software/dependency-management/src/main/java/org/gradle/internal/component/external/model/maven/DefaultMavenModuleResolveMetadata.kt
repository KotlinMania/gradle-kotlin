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
package org.gradle.internal.component.external.model.maven

import com.google.common.base.Objects
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.NamedVariantIdentifier
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.model.NamedObjectInstantiator
import org.gradle.internal.component.external.descriptor.Configuration
import org.gradle.internal.component.external.descriptor.MavenScope
import org.gradle.internal.component.external.model.AbstractLazyModuleComponentResolveMetadata
import org.gradle.internal.component.external.model.DefaultConfigurationMetadata
import org.gradle.internal.component.external.model.ExternalModuleVariantGraphResolveMetadata
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata
import org.gradle.internal.component.external.model.ModuleDependencyMetadata
import org.gradle.internal.component.external.model.VariantDerivationStrategy
import org.gradle.internal.component.external.model.VariantMetadataRules
import org.gradle.internal.component.model.ExcludeMetadata
import org.gradle.internal.component.model.IvyArtifactName
import org.gradle.internal.component.model.ModuleConfigurationMetadata
import org.gradle.internal.component.model.ModuleSources
import org.gradle.internal.component.model.VariantIdentifier
import java.util.Optional

/**
 * [Lazy version][AbstractLazyModuleComponentResolveMetadata] of a [MavenModuleResolveMetadata].
 *
 * @see RealisedMavenModuleResolveMetadata
 */
class DefaultMavenModuleResolveMetadata : AbstractLazyModuleComponentResolveMetadata, MavenModuleResolveMetadata {
    val objectInstantiator: NamedObjectInstantiator
    private val attributesFactory: AttributesFactory

    private val dependencies: ImmutableList<MavenDependencyDescriptor>
    private val packaging: String
    private val relocated: Boolean
    private val snapshotTimestamp: String

    private var derivedVariants: ImmutableList<out ModuleConfigurationMetadata>? = null
        get() {
            val strategy = variantDerivationStrategy
            if (field == null && strategy!!.derivesVariants()) {
                filterConstraints = false
                field = strategy.derive(this)
            }
            return field
        }

    private var filterConstraints = true
    private var dependenciesAsArray: Array<MavenDependencyDescriptor>

    internal constructor(metadata: DefaultMutableMavenModuleResolveMetadata) : super(metadata) {
        this.objectInstantiator = metadata.getObjectInstantiator()
        this.attributesFactory = metadata.attributesFactory
        packaging = metadata.getPackaging()
        relocated = metadata.isRelocated()
        snapshotTimestamp = metadata.getSnapshotTimestamp()!!
        dependencies = metadata.getDependencies()
    }

    private constructor(metadata: DefaultMavenModuleResolveMetadata, sources: ModuleSources, derivationStrategy: VariantDerivationStrategy) : super(metadata, sources, derivationStrategy) {
        this.objectInstantiator = metadata.objectInstantiator
        this.attributesFactory = metadata.attributesFactory
        packaging = metadata.packaging
        relocated = metadata.relocated
        snapshotTimestamp = metadata.snapshotTimestamp
        dependencies = metadata.dependencies

        copyCachedState(metadata, metadata.variantDerivationStrategy !== derivationStrategy)
    }

    override fun createConfiguration(
        componentId: ModuleComponentIdentifier,
        name: String,
        transitive: Boolean,
        visible: Boolean,
        parents: ImmutableSet<String>,
        componentMetadataRules: VariantMetadataRules
    ): DefaultConfigurationMetadata {
        val artifacts = this.artifactsForConfiguration
        val id: VariantIdentifier = NamedVariantIdentifier(componentId, name)
        val configuration =
            DefaultConfigurationMetadata(name, id, componentId, transitive, visible, parents, artifacts, componentMetadataRules, ImmutableList.of<ExcludeMetadata?>(), getAttributes(), false)
        configuration.setConfigDependenciesFactory(org.gradle.internal.Factory { filterDependencies(configuration) })
        return configuration
    }

    override fun maybeDeriveVariants(): Optional<MutableList<out ExternalModuleVariantGraphResolveMetadata>> {
        return Optional.ofNullable<MutableList<out ExternalModuleVariantGraphResolveMetadata>>(this.derivedVariants)
    }

    fun deriveVariants(): Optional<MutableList<out ModuleConfigurationMetadata>> {
        return Optional.ofNullable<MutableList<out ModuleConfigurationMetadata>>(this.derivedVariants)
    }

    override fun populateConfigurationFromDescriptor(name: String, configurationDefinitions: MutableMap<String, Configuration>): ModuleConfigurationMetadata {
        val md = super.populateConfigurationFromDescriptor(name, configurationDefinitions) as DefaultConfigurationMetadata?
        if (filterConstraints && md != null) {
            // if the first call to getConfiguration is done before getDerivedVariants() is called
            // then it means we're using the legacy matching, without attributes, and that the metadata
            // we construct should _not_ include the constraints. We keep the constraints in the descriptors
            // because if we actually use attribute matching, we can select the platform variant which
            // does use constraints.
            return md.mutate().withoutConstraints().build()
        }
        return md!!
    }

    private val artifactsForConfiguration: ImmutableList<out ModuleComponentArtifactMetadata>
        get() = RealisedMavenModuleResolveMetadata.Companion.getArtifactsForConfiguration(this)

    private fun filterDependencies(config: DefaultConfigurationMetadata): ImmutableList<ModuleDependencyMetadata> {
        if (dependencies.isEmpty()) {
            return ImmutableList.of<ModuleDependencyMetadata>()
        }
        val size = dependencies.size
        // If we're reaching this point, we're very likely going to iterate on the dependencies
        // several times. It appears that iterating using `dependencies` is expensive because of
        // the creation of an iterator and checking bounds. Iterating an array is faster.
        if (dependenciesAsArray == null) {
            dependenciesAsArray = dependencies.toTypedArray<MavenDependencyDescriptor>()
        }
        var filteredDependencies: ImmutableList.Builder<ModuleDependencyMetadata>? = null
        val isOptionalConfiguration = "optional" == config.getName()
        val hierarchy: ImmutableSet<String>? = config.getHierarchy()
        for (dependency in dependenciesAsArray) {
            if (isOptionalConfiguration && includeInOptionalConfiguration(dependency)) {
                val element: ModuleDependencyMetadata = OptionalConfigurationMavenDependencyMetadata(dependency)
                if (size == 1) {
                    return ImmutableList.of<ModuleDependencyMetadata>(element)
                }
                if (filteredDependencies == null) {
                    filteredDependencies = ImmutableList.builder<ModuleDependencyMetadata>()
                }
                filteredDependencies.add(element)
            } else if (include(dependency, hierarchy!!)) {
                val element: ModuleDependencyMetadata = MavenDependencyMetadata(dependency)
                if (size == 1) {
                    return ImmutableList.of<ModuleDependencyMetadata>(element)
                }
                if (filteredDependencies == null) {
                    filteredDependencies = ImmutableList.builder<ModuleDependencyMetadata>()
                }
                filteredDependencies.add(element)
            }
        }
        return if (filteredDependencies == null) ImmutableList.of<ModuleDependencyMetadata>() else filteredDependencies.build()
    }

    private fun includeInOptionalConfiguration(dependency: MavenDependencyDescriptor): Boolean {
        val dependencyScope = dependency.getScope()
        // Include all 'optional' dependencies in "optional" configuration
        return dependency.isOptional
                && dependencyScope != MavenScope.Test && dependencyScope != MavenScope.System
    }

    private fun include(dependency: MavenDependencyDescriptor, hierarchy: MutableCollection<String>): Boolean {
        if (dependency.isOptional) {
            return false
        }
        return hierarchy.contains(dependency.getScope().getLowerName())
    }

    override fun asMutable(): MutableMavenModuleResolveMetadata {
        return DefaultMutableMavenModuleResolveMetadata(this, objectInstantiator)
    }

    override fun withSources(sources: ModuleSources): DefaultMavenModuleResolveMetadata {
        return DefaultMavenModuleResolveMetadata(this, sources, variantDerivationStrategy!!)
    }

    override fun withDerivationStrategy(derivationStrategy: VariantDerivationStrategy): ModuleComponentResolveMetadata {
        if (variantDerivationStrategy === derivationStrategy) {
            return this
        }
        return DefaultMavenModuleResolveMetadata(this, getSources(), derivationStrategy)
    }

    override fun getPackaging(): String {
        return packaging
    }

    override fun isRelocated(): Boolean {
        return relocated
    }

    override fun isPomPackaging(): Boolean {
        return POM_PACKAGING == packaging
    }

    override fun isKnownJarPackaging(): Boolean {
        return JAR_PACKAGINGS.contains(packaging)
    }

    override fun getSnapshotTimestamp(): String? {
        return snapshotTimestamp
    }

    override fun getDependencies(): ImmutableList<MavenDependencyDescriptor> {
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

        val that = o as DefaultMavenModuleResolveMetadata
        return relocated == that.relocated && Objects.equal(dependencies, that.dependencies)
                && Objects.equal(packaging, that.packaging)
                && Objects.equal(snapshotTimestamp, that.snapshotTimestamp)
    }

    override fun hashCode(): Int {
        return Objects.hashCode(
            super.hashCode(),
            dependencies,
            packaging,
            relocated,
            snapshotTimestamp
        )
    }

    /**
     * Adapts a MavenDependencyDescriptor to `DependencyMetadata` for the magic "optional" configuration.
     *
     * This configuration has special semantics:
     * - Dependencies in the "optional" configuration are _never_ themselves optional (ie not 'pending')
     * - Dependencies in the "optional" configuration can have dependency artifacts, even if the dependency is flagged as 'optional'.
     * (For a standard configuration, any dependency flagged as 'optional' will have no dependency artifacts).
     */
    private class OptionalConfigurationMavenDependencyMetadata(delegate: MavenDependencyDescriptor) : MavenDependencyMetadata(delegate) {
        val artifacts: ImmutableList<IvyArtifactName>
            /**
             * Dependencies marked as optional/pending in the "optional" configuration _can_ have dependency artifacts.
             */
            get() {
                val dependencyArtifact = dependencyDescriptor.getDependencyArtifact()
                return if (dependencyArtifact == null) ImmutableList.of<IvyArtifactName>() else ImmutableList.of<IvyArtifactName>(
                    dependencyArtifact
                )
            }

        val isConstraint: Boolean
            /**
             * Dependencies in the "optional" configuration are never 'pending'.
             */
            get() = false
    }

    companion object {
        const val POM_PACKAGING: String = "pom"
        val JAR_PACKAGINGS: MutableSet<String> = ImmutableSet.of<String>("jar", "ejb", "bundle", "maven-plugin", "eclipse-plugin")
    }
}
