/*
 * Copyright 2007 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.FileCollectionDependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.attributes.Category
import org.gradle.api.capabilities.Capability
import org.gradle.api.internal.artifacts.NamedVariantIdentifier
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal
import org.gradle.api.internal.artifacts.configurations.Configurations
import org.gradle.api.internal.artifacts.dependencies.SelfResolvingDependencyInternal
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.tasks.NodeExecutionContext
import org.gradle.internal.Describables
import org.gradle.internal.DisplayName
import org.gradle.internal.component.external.model.ImmutableCapabilities.Companion.of
import org.gradle.internal.component.local.model.DefaultLocalVariantGraphResolveMetadata
import org.gradle.internal.component.local.model.DefaultLocalVariantGraphResolveState
import org.gradle.internal.component.local.model.LocalComponentArtifactMetadata
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata
import org.gradle.internal.component.local.model.LocalVariantGraphResolveMetadata
import org.gradle.internal.component.local.model.LocalVariantGraphResolveState
import org.gradle.internal.component.local.model.LocalVariantMetadata
import org.gradle.internal.component.local.model.PublishArtifactLocalArtifactMetadata
import org.gradle.internal.component.model.ComponentConfigurationIdentifier
import org.gradle.internal.component.model.ComponentIdGenerator
import org.gradle.internal.component.model.ExcludeMetadata
import org.gradle.internal.component.model.LocalOriginDependencyMetadata
import org.gradle.internal.component.model.VariantIdentifier
import org.gradle.internal.component.model.VariantResolveMetadata
import org.gradle.internal.model.CalculatedValue
import org.gradle.internal.model.CalculatedValueContainerFactory
import org.gradle.internal.model.ModelContainer
import org.gradle.internal.model.ValueCalculator
import java.util.function.Consumer
import java.util.function.Function

/**
 * Encapsulates all logic required to build a [LocalVariantGraphResolveMetadata] from a
 * [ConfigurationInternal]. Utilizes caching to prevent unnecessary duplicate conversions
 * between DSL and internal metadata types.
 */
class DefaultLocalVariantGraphResolveStateBuilder(
    private val idGenerator: ComponentIdGenerator,
    private val dependencyMetadataFactory: DependencyMetadataFactory,
    private val excludeRuleConverter: ExcludeRuleConverter
) : LocalVariantGraphResolveStateBuilder {
    override fun createRootVariantState(
        configuration: ConfigurationInternal,
        componentId: ComponentIdentifier,
        dependencyCache: LocalVariantGraphResolveStateBuilder.DependencyCache,
        model: ModelContainer<*>,
        calculatedValueContainerFactory: CalculatedValueContainerFactory
    ): LocalVariantGraphResolveState {
        finalize(configuration, "resolved")

        val attributes = configuration.getAttributes().asImmutable()
        val dependencies = getConfigurationDependencyState(
            configuration.asDescribable(),
            configuration.getHierarchy(),
            attributes,
            dependencyCache,
            model,
            calculatedValueContainerFactory
        )

        // TODO: The root node should have no capabilities, as it has no artifacts.
        // However, changing this prevents conflicts between code being compiled and its
        // dependencies from being detected during compilation -- though this also
        // can lead to some false positives.
        val id: VariantIdentifier = NamedVariantIdentifier(componentId, configuration.getName())
        val capabilities = of(Configurations.collectCapabilities(configuration, HashSet<Capability?>(), HashSet<Configuration?>()))
        val metadata: LocalVariantGraphResolveMetadata = DefaultLocalVariantGraphResolveMetadata(
            id,
            configuration.getName(),
            configuration.isTransitive(),
            attributes,
            capabilities!!,
            false
        )

        return DefaultLocalVariantGraphResolveState(
            idGenerator.nextVariantId(),
            metadata,
            dependencies,
            mutableSetOf<LocalVariantMetadata>()
        )
    }

    override fun createConsumableVariantState(
        configuration: ConfigurationInternal,
        componentId: ComponentIdentifier,
        dependencyCache: LocalVariantGraphResolveStateBuilder.DependencyCache,
        model: ModelContainer<*>,
        calculatedValueContainerFactory: CalculatedValueContainerFactory
    ): LocalVariantGraphResolveState {
        finalize(configuration, "consumed as a variant")

        val configurationName = configuration.getName()
        val configurationIdentifier = ComponentConfigurationIdentifier(componentId, configurationName)

        val attributes = configuration.getAttributes().asImmutable()
        val capabilities = of(Configurations.collectCapabilities(configuration, HashSet<Capability?>(), HashSet<Configuration?>()))

        // Collect all artifact sets.
        val artifactSets = ImmutableSet.builder<LocalVariantMetadata>()
        configuration.collectVariants(object : ConfigurationInternal.VariantVisitor {
            override fun visitOwnVariant(displayName: DisplayName, attributes: ImmutableAttributes, artifacts: MutableCollection<out PublishArtifact>) {
                val variantArtifacts: CalculatedValue<ImmutableList<LocalComponentArtifactMetadata>> = getVariantArtifacts(displayName, componentId, artifacts, model, calculatedValueContainerFactory)
                artifactSets.add(LocalVariantMetadata(configurationName, configurationIdentifier, displayName, attributes, capabilities!!, variantArtifacts))
            }

            override fun visitChildVariant(name: String, displayName: DisplayName, attributes: ImmutableAttributes, artifacts: MutableCollection<out PublishArtifact>) {
                val variantArtifacts: CalculatedValue<ImmutableList<LocalComponentArtifactMetadata>> = getVariantArtifacts(displayName, componentId, artifacts, model, calculatedValueContainerFactory)
                artifactSets.add(
                    LocalVariantMetadata(
                        configurationName + "-" + name,
                        NonImplicitArtifactVariantIdentifier(configurationIdentifier, name),
                        displayName,
                        attributes,
                        capabilities!!,
                        variantArtifacts
                    )
                )
            }
        })

        val dependencies = getConfigurationDependencyState(
            configuration.asDescribable(),
            configuration.getHierarchy(),
            attributes,
            dependencyCache,
            model,
            calculatedValueContainerFactory
        )

        val id: VariantIdentifier = NamedVariantIdentifier(componentId, configuration.getName())
        val metadata: LocalVariantGraphResolveMetadata = DefaultLocalVariantGraphResolveMetadata(
            id,
            configurationName,
            configuration.isTransitive(),
            attributes,
            capabilities!!,
            configuration.isDeprecatedForConsumption()
        )

        return DefaultLocalVariantGraphResolveState(
            idGenerator.nextVariantId(),
            metadata,
            dependencies,
            artifactSets.build()
        )
    }

    /**
     * Lazily collect all dependencies and excludes of all configurations in the provided `hierarchy`.
     */
    private fun getConfigurationDependencyState(
        description: DisplayName,
        hierarchy: MutableSet<Configuration>,
        attributes: ImmutableAttributes,
        dependencyCache: LocalVariantGraphResolveStateBuilder.DependencyCache,
        model: ModelContainer<*>,
        calculatedValueContainerFactory: CalculatedValueContainerFactory
    ): CalculatedValue<DefaultLocalVariantGraphResolveState.VariantDependencyMetadata> {
        return calculatedValueContainerFactory.create<DefaultLocalVariantGraphResolveState.VariantDependencyMetadata, ValueCalculator<out DefaultLocalVariantGraphResolveState.VariantDependencyMetadata>>(
            Describables.of("Dependency state for", description), ValueCalculator { context: NodeExecutionContext? ->
                model.fromMutableState<DefaultLocalVariantGraphResolveState.VariantDependencyMetadata> { p: Any? ->
                    val dependencies = ImmutableList.builder<LocalOriginDependencyMetadata>()
                    val files = ImmutableSet.builder<LocalFileDependencyMetadata>()
                    val excludes = ImmutableList.builder<ExcludeMetadata>()

                    // For historical reasons, and to maintain behavior, dependencies
                    // are ordered based on the name of the extended configurations.
                    val sortedHierarchy = ArrayList<Configuration>(hierarchy)
                    sortedHierarchy.sort(Comparator.comparing<Configuration, String>(Function { obj: Configuration -> obj.getName() }))
                    sortedHierarchy.forEach(Consumer { config: Configuration? ->
                        val defined = getDefinedState(config as ConfigurationInternal, dependencyCache)
                        dependencies.addAll(defined.dependencies)
                        files.addAll(defined.files)
                        excludes.addAll(defined.excludes)
                    })

                    val state = LocalVariantGraphResolveStateBuilder.DependencyState(dependencies.build(), files.build(), excludes.build())
                    DefaultLocalVariantGraphResolveState.VariantDependencyMetadata(
                        maybeForceDependencies(state.dependencies, attributes), state.files, state.excludes
                    )
                }
            })
    }

    /**
     * Get the defined dependencies and excludes for `configuration`, while also caching the result.
     */
    private fun getDefinedState(configuration: ConfigurationInternal, cache: LocalVariantGraphResolveStateBuilder.DependencyCache): LocalVariantGraphResolveStateBuilder.DependencyState {
        return cache.computeIfAbsent(configuration, Function { configuration: ConfigurationInternal? -> this.doGetDefinedState(configuration!!) })
    }

    /**
     * Calculate the defined dependencies and excludes for `configuration`, while converting the
     * DSL representation to the internal representation.
     */
    private fun doGetDefinedState(configuration: ConfigurationInternal): LocalVariantGraphResolveStateBuilder.DependencyState {
        val dependencyBuilder = ImmutableList.builder<LocalOriginDependencyMetadata>()
        val fileBuilder = ImmutableSet.builder<LocalFileDependencyMetadata>()
        val excludeBuilder = ImmutableList.builder<ExcludeMetadata>()

        // Configurations that are not declarable should not have dependencies or constraints present,
        // but we need to allow dependencies to be checked to avoid emitting many warnings when the
        // Kotlin plugin is applied.  This is because applying the Kotlin plugin adds dependencies
        // to the testRuntimeClasspath configuration, which is not declarable.
        // To demonstrate this, add a check for configuration.isCanBeDeclared() && configuration.assertHasNoDeclarations() if not
        // and run tests such as KotlinDslPluginTest, or the building-kotlin-applications samples and you'll configurations which
        // aren't declarable but have declared dependencies present.
        for (dependency in configuration.getDependencies()) {
            if (dependency is ModuleDependency) {
                val moduleDependency = dependency
                dependencyBuilder.add(dependencyMetadataFactory.createDependencyMetadata(moduleDependency))
            } else if (dependency is FileCollectionDependency) {
                val fileDependency = dependency
                fileBuilder.add(DefaultLocalFileDependencyMetadata(fileDependency))
            } else {
                throw IllegalArgumentException("Cannot convert dependency " + dependency + " to local component dependency metadata.")
            }
        }

        // Configurations that are not declarable should not have dependencies or constraints present,
        // no smoke-tested plugins add constraints, so we should be able to safely throw an exception here
        // if we find any - but we'll avoid doing so for now to avoid breaking any existing builds and to
        // remain consistent with the behavior for dependencies.
        for (dependencyConstraint in configuration.getDependencyConstraints()) {
            dependencyBuilder.add(dependencyMetadataFactory.createDependencyConstraintMetadata(dependencyConstraint))
        }

        for (excludeRule in configuration.getExcludeRules()) {
            excludeBuilder.add(excludeRuleConverter.convertExcludeRule(excludeRule))
        }

        configuration.markDependenciesObserved()

        return LocalVariantGraphResolveStateBuilder.DependencyState(dependencyBuilder.build(), fileBuilder.build(), excludeBuilder.build())
    }

    /**
     * Identifier for non-implicit artifact variants of a local graph variant.
     */
    private class NonImplicitArtifactVariantIdentifier(private val parent: VariantResolveMetadata.Identifier, private val name: String) : VariantResolveMetadata.Identifier {
        private val hashCode: Int

        init {
            this.hashCode = computeHashCode(
                name,
                parent
            )
        }

        override fun hashCode(): Int {
            return hashCode
        }

        override fun equals(obj: Any): Boolean {
            if (obj === this) {
                return true
            }
            if (obj == null || javaClass != obj.javaClass) {
                return false
            }
            val other = obj as NonImplicitArtifactVariantIdentifier
            return parent == other.parent && name == other.name
        }

        companion object {
            private fun computeHashCode(name: String, parent: VariantResolveMetadata.Identifier): Int {
                return 31 * parent.hashCode() + name.hashCode()
            }
        }
    }

    /**
     * Default implementation of [LocalFileDependencyMetadata].
     */
    class DefaultLocalFileDependencyMetadata(val source: FileCollectionDependency) : LocalFileDependencyMetadata {
        val componentId: ComponentIdentifier?
            get() = (this.source as SelfResolvingDependencyInternal).getTargetComponentId()

        val files: FileCollectionInternal
            get() = source.getFiles() as FileCollectionInternal
    }

    companion object {
        /**
         * Perform any final mutating actions for this configuration and its parents.
         * Then, lock this configuration and its parents from mutation.
         * After we observe a configuration (by building its metadata), its state should not change.
         */
        private fun finalize(configuration: ConfigurationInternal, reason: String) {
            // Perform any final mutating actions for this configuration and its parents.
            // Then, lock this configuration and its parents from mutation.
            // After we observe a configuration (by building its metadata), its state should not change.
            configuration.runDependencyActions()
            configuration.markAsObserved(reason)
        }

        private fun getVariantArtifacts(
            displayName: DisplayName,
            componentId: ComponentIdentifier,
            sourceArtifacts: MutableCollection<out PublishArtifact>,
            model: ModelContainer<*>,
            calculatedValueContainerFactory: CalculatedValueContainerFactory
        ): CalculatedValue<ImmutableList<LocalComponentArtifactMetadata>> {
            return calculatedValueContainerFactory.create<ImmutableList<LocalComponentArtifactMetadata>, ValueCalculator<out ImmutableList<LocalComponentArtifactMetadata>>>(
                Describables.of(
                    displayName,
                    "artifacts"
                ), ValueCalculator { context: NodeExecutionContext? ->
                    if (sourceArtifacts.isEmpty()) {
                        return@create ImmutableList.of<LocalComponentArtifactMetadata>()
                    } else {
                        return@create model.fromMutableState<ImmutableList<LocalComponentArtifactMetadata>> { m: Any? ->
                            val result = ImmutableList.builderWithExpectedSize<LocalComponentArtifactMetadata>(sourceArtifacts.size)
                            for (sourceArtifact in sourceArtifacts) {
                                result.add(PublishArtifactLocalArtifactMetadata(componentId, sourceArtifact))
                            }
                            result.build()
                        }
                    }
                })
        }

        private fun maybeForceDependencies(
            dependencies: ImmutableList<LocalOriginDependencyMetadata>,
            attributes: ImmutableAttributes
        ): ImmutableList<LocalOriginDependencyMetadata> {
            val entry = attributes.findEntry<Category>(Category.CATEGORY_ATTRIBUTE)
            if (entry == null || entry.getIsolatedValue().getName() != Category.ENFORCED_PLATFORM) {
                return dependencies
            }

            // Need to wrap all dependencies to force them.
            val forcedDependencies = ImmutableList.builder<LocalOriginDependencyMetadata>()
            for (rawDependency in dependencies) {
                forcedDependencies.add(rawDependency.forced()!!)
            }
            return forcedDependencies.build()
        }
    }
}
