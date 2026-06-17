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
package org.gradle.internal.component.external.model

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.capability.CapabilitySelector
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.dsl.dependencies.PlatformSupport
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.component.external.descriptor.Configuration
import org.gradle.internal.component.model.ComponentArtifactMetadata.getName
import org.gradle.internal.component.model.ExcludeMetadata
import org.gradle.internal.component.model.VariantGraphResolveMetadata.getName
import java.util.function.Function
import java.util.stream.Collectors

/**
 * Utility class to help transform a lazy [ModuleComponentResolveMetadata] into a realised one.
 */
object LazyToRealisedModuleComponentResolveMetadataHelper {
    /**
     * Method to transform lazy variants into realised ones
     *
     * @param mutableMetadata the source metadata
     * @param variantMetadataRules the lazy rules
     * @param variants the variants to transform
     * @return a list of realised variants
     */
    fun realiseVariants(
        mutableMetadata: ModuleComponentResolveMetadata,
        variantMetadataRules: VariantMetadataRules,
        variants: ImmutableList<out ComponentVariant>
    ): ImmutableList<AbstractRealisedModuleComponentResolveMetadata.ImmutableRealisedVariantImpl?>? {
        if (variants.isEmpty()) {
            return ImmutableList.of<AbstractRealisedModuleComponentResolveMetadata.ImmutableRealisedVariantImpl?>()
        }
        val realisedVariants: MutableList<AbstractRealisedModuleComponentResolveMetadata.ImmutableRealisedVariantImpl?> =
            ArrayList<AbstractRealisedModuleComponentResolveMetadata.ImmutableRealisedVariantImpl?>(variants.size)
        for (variant in variants) {
            realisedVariants.add(LazyToRealisedModuleComponentResolveMetadataHelper.applyRules(variant, variantMetadataRules, mutableMetadata.getId()!!))
        }
        return addVariantsFromRules(mutableMetadata, realisedVariants, variantMetadataRules)
    }

    private fun addVariantsFromRules(
        componentMetadata: ModuleComponentResolveMetadata,
        declaredVariants: MutableList<AbstractRealisedModuleComponentResolveMetadata.ImmutableRealisedVariantImpl?>,
        variantMetadataRules: VariantMetadataRules
    ): ImmutableList<AbstractRealisedModuleComponentResolveMetadata.ImmutableRealisedVariantImpl?> {
        val additionalVariants = variantMetadataRules.additionalVariants
        if (additionalVariants.isEmpty()) {
            return ImmutableList.copyOf<AbstractRealisedModuleComponentResolveMetadata.ImmutableRealisedVariantImpl?>(declaredVariants)
        }

        val builder = ImmutableList.Builder<AbstractRealisedModuleComponentResolveMetadata.ImmutableRealisedVariantImpl?>()
        builder.addAll(declaredVariants)
        val variantsByName: MutableMap<String?, ComponentVariant?> = declaredVariants.stream().collect(Collectors.toMap(ComponentVariant::getName, Function.identity<Any?>()))
        for (additionalVariant in additionalVariants) {
            val baseName = additionalVariant.getBase()
            val attributes: ImmutableAttributes?
            val capabilities: ImmutableCapabilities?
            val dependencies: ImmutableList<out ComponentVariant.Dependency>?
            val dependencyConstraints: ImmutableList<out ComponentVariant.DependencyConstraint>?
            val files: ImmutableList<out ComponentVariant.File?>?

            val baseVariant = variantsByName.get(baseName)
            val isExternalVariant: Boolean
            if (baseVariant == null) {
                attributes = componentMetadata.getAttributes()
                capabilities = ImmutableCapabilities.Companion.EMPTY
                dependencies = ImmutableList.of<ComponentVariant.Dependency?>()
                dependencyConstraints = ImmutableList.of<ComponentVariant.DependencyConstraint?>()
                files = ImmutableList.of<ComponentVariant.File?>()
                isExternalVariant = false
            } else {
                attributes = baseVariant.attributes
                capabilities = baseVariant.capabilities
                dependencies = baseVariant.getDependencies()
                dependencyConstraints = baseVariant.getDependencyConstraints()
                files = baseVariant.getFiles()
                isExternalVariant = baseVariant.isExternalVariant()
            }

            if (baseName == null || baseVariant != null) {
                val variant = LazyToRealisedModuleComponentResolveMetadataHelper.applyRules(
                    AbstractMutableModuleComponentResolveMetadata.ImmutableVariantImpl(
                        componentMetadata.getId(), additionalVariant.getName(), attributes, dependencies, dependencyConstraints, files, capabilities, isExternalVariant
                    ),
                    variantMetadataRules, componentMetadata.getId()!!
                )
                builder.add(variant)
            } else if (!additionalVariant.isLenient()) {
                throw InvalidUserDataException("Variant '" + baseName + "' not defined in module " + componentMetadata.getId()!!.getDisplayName())
            }
        }
        return builder.build()
    }

    private fun applyRules(
        variant: ComponentVariant,
        variantMetadataRules: VariantMetadataRules,
        id: ModuleComponentIdentifier
    ): AbstractRealisedModuleComponentResolveMetadata.ImmutableRealisedVariantImpl {
        val attributes = variantMetadataRules.applyVariantAttributeRules(variant, variant.attributes)
        val capabilities = variantMetadataRules.applyCapabilitiesRules(variant, variant.capabilities)
        val files: ImmutableList<out ComponentVariant.File?> = variantMetadataRules.applyVariantFilesMetadataRulesToFiles(variant, variant.getFiles(), id)
        val force = PlatformSupport.hasForcedDependencies(variant)
        val dependencies: MutableList<out ModuleDependencyMetadata?> =
            variantMetadataRules.applyDependencyMetadataRules<GradleDependencyMetadata?>(variant, convertDependencies(variant.getDependencies(), variant.getDependencyConstraints(), force))
        return AbstractRealisedModuleComponentResolveMetadata.ImmutableRealisedVariantImpl(
            id, variant.name, attributes,
            variant.getDependencies(), variant.getDependencyConstraints(), files,
            capabilities, dependencies, variant.isExternalVariant()
        )
    }

    private fun convertDependencies(
        dependencies: MutableList<out ComponentVariant.Dependency>,
        dependencyConstraints: MutableList<out ComponentVariant.DependencyConstraint>,
        force: Boolean
    ): MutableList<GradleDependencyMetadata?> {
        val result: MutableList<GradleDependencyMetadata?> = ArrayList<GradleDependencyMetadata?>(dependencies.size)
        for (dependency in dependencies) {
            val selector: ModuleComponentSelector = DefaultModuleComponentSelector.Companion.newSelector(
                DefaultModuleIdentifier.newId(dependency.getGroup(), dependency.getModule()),
                dependency.getVersionConstraint(),
                dependency.getAttributes(),
                dependency.getCapabilitySelectors()
            )
            val excludes: ImmutableList<ExcludeMetadata?>? = dependency.getExcludes()
            result.add(GradleDependencyMetadata(selector, excludes, false, dependency.isEndorsingStrictVersions(), dependency.getReason(), force, dependency.getDependencyArtifact()))
        }
        for (dependencyConstraint in dependencyConstraints) {
            result.add(
                GradleDependencyMetadata(
                    DefaultModuleComponentSelector.Companion.newSelector(
                        DefaultModuleIdentifier.newId(dependencyConstraint.getGroup(), dependencyConstraint.getModule()),
                        dependencyConstraint.getVersionConstraint(),
                        dependencyConstraint.getAttributes(),
                        ImmutableSet.of<CapabilitySelector?>()
                    ),
                    ImmutableList.of<ExcludeMetadata?>(),
                    true,
                    false,
                    dependencyConstraint.getReason(),
                    force,
                    null
                )
            )
        }
        return result
    }

    fun constructHierarchy(descriptorConfiguration: Configuration, configurationDefinitions: ImmutableMap<String?, Configuration?>): ImmutableSet<String?> {
        if (descriptorConfiguration.extendsFrom.isEmpty()) {
            return ImmutableSet.of<String?>(descriptorConfiguration.name)
        }
        val accumulator = ImmutableSet.Builder<String?>()
        populateHierarchy(descriptorConfiguration, configurationDefinitions, accumulator)
        return accumulator.build()
    }

    private fun populateHierarchy(metadata: Configuration, configurationDefinitions: ImmutableMap<String?, Configuration?>, accumulator: ImmutableSet.Builder<String?>) {
        accumulator.add(metadata.name)
        for (parentName in metadata.extendsFrom) {
            val parent = configurationDefinitions.get(parentName)
            LazyToRealisedModuleComponentResolveMetadataHelper.populateHierarchy(parent!!, configurationDefinitions, accumulator)
        }
    }
}
