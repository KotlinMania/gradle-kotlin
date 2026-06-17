/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.api.publish.internal.mapping

import org.gradle.api.GradleException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.DependencyConstraint
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.capability.CapabilitySelector
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentSelector
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.ResolvedVariantResult
import org.gradle.api.artifacts.result.UnresolvedDependencyResult
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.ProjectComponentIdentifierInternal
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependencyConstraint
import org.gradle.api.internal.artifacts.dependencies.ProjectDependencyInternal
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyPublicationResolver
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.AttributeDesugaring
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchemaFactory.create
import org.gradle.api.publish.internal.validation.VariantWarningCollector
import org.gradle.internal.component.local.model.ProjectComponentSelectorInternal
import org.gradle.util.Path
import java.util.Objects
import java.util.function.Consumer

/**
 * A [VariantDependencyResolver] that analyzes a resolution result to determine the
 * resolved coordinates for a given dependency.
 *
 *
 * The configuration being resolved should declare the same dependencies as the variant
 * being published. Then, each outgoing edge of the analyzed resolution result will correspond
 * to each declared dependency on the published variant. We build a mapping from requested
 * coordinates to resolved coordinates. Then, when resolving individual variant or component
 * coordinates, we can look up in the map to determine what coordinates should be published
 * for a given declared dependency.
 */
class ResolutionBackedPublicationDependencyResolver(
    private val projectDependencyResolver: ProjectDependencyPublicationResolver,
    private val moduleIdentifierFactory: ImmutableModuleIdentifierFactory,
    rootComponent: ResolvedComponentResult,
    rootVariant: ResolvedVariantResult,
    private val attributeDesugaring: AttributeDesugaring
) : VariantDependencyResolver, ComponentDependencyResolver {
    private val mappings: ResolvedMappings

    init {
        this.mappings = calculateMappings(
            rootComponent, rootVariant,
            projectDependencyResolver,
            moduleIdentifierFactory
        )
    }

    override fun resolveVariantCoordinates(dependency: ExternalDependency, warnings: VariantWarningCollector): ResolvedCoordinates? {
        val module = moduleIdentifierFactory.module(dependency.getGroup(), dependency.getName())
        val key = ResolutionBackedPublicationDependencyResolver.ModuleDependencyKey(module!!, ModuleDependencyDetails.Companion.from(dependency, attributeDesugaring))

        val resolved = mappings.resolvedModuleVariants.get(key)
        if (resolved != null) {
            return ResolvedCoordinates.Companion.create(resolved)
        }

        if (mappings.incompatibleModules.contains(key)) {
            // TODO: We should enhance this warning to list the conflicting dependencies.
            warnings.addIncompatible(
                String.format(
                    "Cannot determine variant coordinates for dependency '%s' since " +
                            "multiple dependencies ambiguously map to different resolved coordinates.",
                    module
                )
            )
        } else {
            // This is likely user error, as the resolution result should have the same dependencies as the published variant.
            warnings.addIncompatible(
                String.format(
                    "Cannot determine variant coordinates for dependency '%s' since " +
                            "the resolved graph does not contain the requested module.",
                    module
                )
            )
        }

        // Fallback to component coordinate mapping only.
        return resolveModuleComponentCoordinates(module)
    }

    override fun resolveVariantCoordinates(dependency: ProjectDependency, warnings: VariantWarningCollector): ResolvedCoordinates {
        val identityPath = (dependency as ProjectDependencyInternal).getTargetProjectIdentity().getBuildTreePath()
        val key = ProjectDependencyKey(identityPath, ModuleDependencyDetails.Companion.from(dependency, attributeDesugaring))

        val resolved = mappings.resolvedProjectVariants.get(key)
        if (resolved != null) {
            return ResolvedCoordinates.Companion.create(resolved)
        }

        if (mappings.incompatibleProjects.contains(key)) {
            // TODO: We should enhance this warning to list the conflicting dependencies.
            warnings.addIncompatible(
                String.format(
                    "Cannot determine variant coordinates for Project dependency '%s' since " +
                            "multiple dependencies ambiguously map to different resolved coordinates.",
                    identityPath
                )
            )
        } else {
            // This is likely user error, as the resolution result should have the same dependencies as the published variant.
            warnings.addIncompatible(
                String.format(
                    "Cannot determine variant coordinates for Project dependency '%s' since " +
                            "the resolved graph does not contain the requested project.",
                    identityPath
                )
            )
        }

        // Fallback to component coordinate mapping only.
        return resolveComponentCoordinates(dependency)
    }

    override fun resolveComponentCoordinates(dependency: ExternalDependency): ResolvedCoordinates? {
        val module = moduleIdentifierFactory.module(dependency.getGroup(), dependency.getName())
        return resolveModuleComponentCoordinates(module!!)
    }

    override fun resolveComponentCoordinates(dependency: ProjectDependency): ResolvedCoordinates {
        val identityPath = (dependency as ProjectDependencyInternal).getTargetProjectIdentity().getBuildTreePath()
        val resolved = mappings.resolvedProjectComponents.get(identityPath)
        if (resolved != null) {
            return ResolvedCoordinates.Companion.create(resolved)
        }

        // This is likely user error, as the resolution result should have the same
        // dependencies as the published variant. Fallback to resolving the project
        // coordinates directly.
        return ResolvedCoordinates.Companion.create(
            projectDependencyResolver.resolveComponent<ModuleVersionIdentifier?>(ModuleVersionIdentifier::class.java, identityPath)
        )
    }

    override fun resolveComponentCoordinates(dependency: DependencyConstraint): ResolvedCoordinates? {
        assert(dependency !is DefaultProjectDependencyConstraint)
        val module = moduleIdentifierFactory.module(dependency.getGroup(), dependency.getName())
        return resolveModuleComponentCoordinates(module!!)
    }

    override fun resolveComponentCoordinates(dependency: DefaultProjectDependencyConstraint): ResolvedCoordinates {
        return resolveComponentCoordinates(dependency.projectDependency)
    }


    private fun resolveModuleComponentCoordinates(module: ModuleIdentifier): ResolvedCoordinates? {
        val resolved = mappings.resolvedModuleComponents.get(module)
        if (resolved != null) {
            return ResolvedCoordinates.Companion.create(resolved)
        }
        return null
    }

    private class ResolvedMappings(
        val resolvedModuleComponents: MutableMap<ModuleIdentifier, ModuleVersionIdentifier>,
        val resolvedProjectComponents: MutableMap<Path, ModuleVersionIdentifier>,
        val resolvedModuleVariants: MutableMap<ModuleDependencyKey, ModuleVersionIdentifier>,
        val resolvedProjectVariants: MutableMap<ProjectDependencyKey, ModuleVersionIdentifier>,
        // Incompatible modules and projects are those that have multiple dependencies with the same
        // attributes and capabilities, but have somehow resolved to different coordinates. This can
        // often happen when the dependency is declared with a targetConfiguration.
        val incompatibleModules: MutableSet<ModuleDependencyKey>,
        val incompatibleProjects: MutableSet<ProjectDependencyKey>
    )

    private class CoordinatePair(
        val componentCoordinates: ModuleVersionIdentifier,
        val variantCoordinates: ModuleVersionIdentifier
    )

    private class ModuleDependencyKey(private val module: ModuleIdentifier, private val details: ModuleDependencyDetails) {
        override fun equals(o: Any): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }
            val that = o as ModuleDependencyKey
            return module == that.module && details == that.details
        }

        override fun hashCode(): Int {
            return Objects.hash(module, details)
        }
    }

    private class ProjectDependencyKey(private val identityPath: Path, private val details: ModuleDependencyDetails) {
        override fun equals(o: Any): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }
            val that = o as ProjectDependencyKey
            return identityPath == that.identityPath && details == that.details
        }

        override fun hashCode(): Int {
            return Objects.hash(identityPath, details)
        }
    }

    private class ModuleDependencyDetails(
        val requestAttributes: AttributeContainer,
        val capabilitySelectors: MutableSet<CapabilitySelector>
    ) {
        override fun equals(o: Any): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }
            val that = o as ModuleDependencyDetails
            return requestAttributes == that.requestAttributes && capabilitySelectors == that.capabilitySelectors
        }

        override fun hashCode(): Int {
            return Objects.hash(requestAttributes, capabilitySelectors)
        }

        companion object {
            fun from(dependency: ModuleDependency, attributeDesugaring: AttributeDesugaring): ModuleDependencyDetails {
                val attributes = (dependency.getAttributes() as AttributeContainerInternal).asImmutable()
                return ModuleDependencyDetails(
                    attributeDesugaring.desugar(attributes),
                    dependency.getCapabilitySelectors()
                )
            }

            // Do not desugar here since resolution results already expose desugared attributes.
            fun from(componentSelector: ComponentSelector): ModuleDependencyDetails {
                return ModuleDependencyDetails(
                    componentSelector.getAttributes(),
                    componentSelector.getCapabilitySelectors()
                )
            }
        }
    }

    companion object {
        private fun calculateMappings(
            rootComponent: ResolvedComponentResult,
            rootVariant: ResolvedVariantResult,
            projectDependencyResolver: ProjectDependencyPublicationResolver,
            moduleIdentifierFactory: ImmutableModuleIdentifierFactory
        ): ResolvedMappings {
            val incompatibleModuleDeps: MutableSet<ModuleDependencyKey> = HashSet<ModuleDependencyKey>()
            val incompatibleProjectDeps: MutableSet<ProjectDependencyKey> = HashSet<ProjectDependencyKey>()

            val resolvedModuleComponents: MutableMap<ModuleIdentifier, ModuleVersionIdentifier> = HashMap<ModuleIdentifier, ModuleVersionIdentifier>()
            val resolvedProjectComponents: MutableMap<Path, ModuleVersionIdentifier> = HashMap<Path, ModuleVersionIdentifier>()

            val resolvedModuleVariants: MutableMap<ModuleDependencyKey, ModuleVersionIdentifier> = HashMap<ModuleDependencyKey, ModuleVersionIdentifier>()
            val resolvedProjectVariants: MutableMap<ProjectDependencyKey, ModuleVersionIdentifier> = HashMap<ProjectDependencyKey, ModuleVersionIdentifier>()

            visitFirstLevelEdges(rootComponent, rootVariant, Consumer { edge: ResolvedDependencyResult ->
                val requested = edge.getRequested()
                val coordinates: CoordinatePair = getResolvedCoordinates(edge.getResolvedVariant(), projectDependencyResolver, moduleIdentifierFactory)
                if (requested is ModuleComponentSelector) {
                    val requestedModule = requested

                    val existingComponent = resolvedModuleComponents.put(requestedModule.getModuleIdentifier(), coordinates.componentCoordinates)
                    if (existingComponent != null && existingComponent != coordinates.componentCoordinates) {
                        throw GradleException("Expected all requested coordinates to resolve to the same component coordinates.")
                    }

                    val key = ModuleDependencyKey(requestedModule.getModuleIdentifier(), ModuleDependencyDetails.Companion.from(requested))
                    if (incompatibleModuleDeps.contains(key)) {
                        return@visitFirstLevelEdges
                    }

                    val existingVariant = resolvedModuleVariants.put(key, coordinates.variantCoordinates)
                    if (existingVariant != null && existingVariant != coordinates.variantCoordinates) {
                        resolvedModuleVariants.remove(key)
                        incompatibleModuleDeps.add(key)
                    }
                } else if (requested is ProjectComponentSelector) {
                    val requestedProject = requested as ProjectComponentSelectorInternal

                    val existingComponent = resolvedProjectComponents.put(requestedProject.identityPath, coordinates.componentCoordinates)
                    if (existingComponent != null && existingComponent != coordinates.componentCoordinates) {
                        throw GradleException("Expected all requested projects to resolve to the same component coordinates.")
                    }

                    val key = ProjectDependencyKey(requestedProject.identityPath, ModuleDependencyDetails.Companion.from(requested))
                    if (incompatibleProjectDeps.contains(key)) {
                        return@visitFirstLevelEdges
                    }

                    val existingVariant = resolvedProjectVariants.put(key, coordinates.variantCoordinates)
                    if (existingVariant != null && existingVariant != coordinates.variantCoordinates) {
                        resolvedProjectVariants.remove(key)
                        incompatibleProjectDeps.add(key)
                    }
                }
            })

            return ResolvedMappings(
                resolvedModuleComponents,
                resolvedProjectComponents,
                resolvedModuleVariants,
                resolvedProjectVariants,
                incompatibleModuleDeps,
                incompatibleProjectDeps
            )
        }

        private fun visitFirstLevelEdges(rootComponent: ResolvedComponentResult, rootVariant: ResolvedVariantResult, visitor: Consumer<ResolvedDependencyResult>) {
            val rootEdges = rootComponent.getDependenciesForVariant(rootVariant)
            for (dependencyResult in rootEdges) {
                if (dependencyResult !is ResolvedDependencyResult) {
                    val unresolved = dependencyResult as UnresolvedDependencyResult
                    throw GradleException("Could not map coordinates for " + unresolved.getAttempted().getDisplayName() + ".", unresolved.getFailure())
                }

                if (dependencyResult.isConstraint()) {
                    // Constraints also appear in the graph if they contributed to it.
                    // Ignore them for now, though perhaps we can use them in the future to
                    // publish version ranges.
                    continue
                }

                visitor.accept(dependencyResult)
            }
        }

        private fun getResolvedCoordinates(
            variant: ResolvedVariantResult,
            projectDependencyResolver: ProjectDependencyPublicationResolver,
            moduleIdentifierFactory: ImmutableModuleIdentifierFactory
        ): CoordinatePair {
            val componentId = variant.getOwner()

            // TODO #3170: We should analyze artifacts to determine if we need to publish additional
            // artifact information like type or classifier.
            if (componentId is ProjectComponentIdentifier) {
                return getProjectCoordinates(variant, componentId as ProjectComponentIdentifierInternal, projectDependencyResolver)
            } else if (componentId is ModuleComponentIdentifier) {
                return getModuleCoordinates(variant, componentId, moduleIdentifierFactory)
            } else {
                throw UnsupportedOperationException("Unexpected component identifier type: " + componentId)
            }
        }

        private fun getModuleCoordinates(
            variant: ResolvedVariantResult,
            componentId: ModuleComponentIdentifier,
            moduleIdentifierFactory: ImmutableModuleIdentifierFactory
        ): CoordinatePair {
            val componentCoordinates = moduleIdentifierFactory.moduleWithVersion(componentId.getModuleIdentifier(), componentId.getVersion())
            val variantCoordinates: ModuleVersionIdentifier? = getExternalCoordinates(variant, moduleIdentifierFactory)

            return ResolutionBackedPublicationDependencyResolver.CoordinatePair(
                componentCoordinates!!,
                (if (variantCoordinates != null) variantCoordinates else componentCoordinates)
            )
        }

        private fun getExternalCoordinates(
            variant: ResolvedVariantResult,
            moduleIdentifierFactory: ImmutableModuleIdentifierFactory
        ): ModuleVersionIdentifier? {
            val externalVariant = variant.getExternalVariant().orElse(null)
            if (externalVariant != null) {
                val owningComponent = externalVariant.getOwner()
                if (owningComponent is ModuleComponentIdentifier) {
                    val moduleComponentId = owningComponent
                    return moduleIdentifierFactory.moduleWithVersion(moduleComponentId.getModuleIdentifier(), moduleComponentId.getVersion())
                }
                throw GradleException("Expected owning component of module component to be a module component: " + owningComponent)
            }
            return null
        }

        private fun getProjectCoordinates(
            variant: ResolvedVariantResult,
            componentId: ProjectComponentIdentifierInternal,
            projectDependencyResolver: ProjectDependencyPublicationResolver
        ): CoordinatePair {
            val identityPath = componentId.getIdentityPath()

            // TODO: Using the display name here is not great, however it is the same as the variant name.
            // Instead, the resolution result should expose the project coordinates via getExternalVariant.
            val variantName = variant.getDisplayName()
            val variantCoordinates = projectDependencyResolver.resolveVariant<ModuleVersionIdentifier?>(ModuleVersionIdentifier::class.java, identityPath, variantName)

            if (variantCoordinates == null) {
                throw InvalidUserDataException(
                    String.format(
                        "Could not resolve coordinates for variant '%s' of project '%s'.",
                        variantName, identityPath
                    )
                )
            }

            val componentCoordinates = projectDependencyResolver.resolveComponent<ModuleVersionIdentifier?>(ModuleVersionIdentifier::class.java, identityPath)
            return ResolutionBackedPublicationDependencyResolver.CoordinatePair(componentCoordinates!!, variantCoordinates)
        }
    }
}
