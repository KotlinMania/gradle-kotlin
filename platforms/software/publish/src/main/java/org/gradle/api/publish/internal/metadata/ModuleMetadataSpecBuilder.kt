/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.publish.internal.metadata

import com.google.common.base.Strings
import com.google.common.collect.Sets
import org.apache.commons.lang3.StringUtils
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.Named
import org.gradle.api.Transformer
import org.gradle.api.artifacts.DependencyArtifact
import org.gradle.api.artifacts.DependencyConstraint
import org.gradle.api.artifacts.ExcludeRule
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.artifacts.capability.CapabilitySelector
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.capabilities.Capability
import org.gradle.api.component.ComponentWithCoordinates
import org.gradle.api.component.ComponentWithVariants
import org.gradle.api.component.SoftwareComponent
import org.gradle.api.component.SoftwareComponentVariant
import org.gradle.api.internal.artifacts.DefaultExcludeRule
import org.gradle.api.internal.artifacts.ImmutableVersionConstraint
import org.gradle.api.internal.artifacts.PublishArtifactInternal
import org.gradle.api.internal.artifacts.capability.FeatureCapabilitySelector
import org.gradle.api.internal.artifacts.capability.SpecificCapabilitySelector
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint.Companion.of
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependencyConstraint
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchemaFactory.create
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.api.internal.provider.MergeProvider
import org.gradle.api.internal.provider.Providers
import org.gradle.api.provider.Provider
import org.gradle.api.publish.internal.PublicationInternal
import org.gradle.api.publish.internal.mapping.ComponentDependencyResolver
import org.gradle.api.publish.internal.mapping.DependencyCoordinateResolverFactory
import org.gradle.api.publish.internal.mapping.ResolvedCoordinates
import java.util.TreeMap

/**
 * Builds a [ModuleMetadataSpec] from a [PublicationInternal] and its [SoftwareComponent].
 *
 *
 * This builder extracts the variants, dependencies, artifacts, etc from the component to build
 * an independent representation of a GMM file that can be published without additional processing.
 */
class ModuleMetadataSpecBuilder(
    private val publication: PublicationInternal<*>,
    private val publications: MutableCollection<out PublicationInternal<*>>,
    private val checker: InvalidPublicationChecker,
    private val dependencyCoordinateResolverFactory: DependencyCoordinateResolverFactory
) {
    private val publicationCoordinates: ModuleVersionIdentifier
    private val component: Provider<SoftwareComponentInternal>
    private val componentCoordinates: MutableMap<SoftwareComponent?, ComponentData?> = HashMap<SoftwareComponent?, ComponentData?>()

    init {
        this.component = publication.component
        this.publicationCoordinates = publication.getCoordinates()
        // Collect a map from component to coordinates. This might be better to move to the component or some publications model
        collectCoordinates(componentCoordinates)
    }

    fun build(): Provider<ModuleMetadataSpec?> {
        return variants().map<ModuleMetadataSpec?>(Transformer { variants: MutableList<ModuleMetadataSpec.Variant?>? -> ModuleMetadataSpec(identity(), variants, publication.isPublishBuildId()) })
    }

    private fun identity(): ModuleMetadataSpec.Identity {
        // Collect a map from component to its owning component. This might be better to move to the component or some publications model
        val owners: MutableMap<SoftwareComponent?, SoftwareComponent?> = HashMap<SoftwareComponent?, SoftwareComponent?>()
        collectOwners(publications, owners)

        val owner = owners.get(component.get())
        val ownerData = if (owner == null) null else componentCoordinates.get(owner)
        val componentData = ComponentData(publication.getCoordinates(), publication.getAttributes())

        return if (ownerData != null)
            identityFor(ownerData, relativeUrlTo(componentData.coordinates, ownerData.coordinates))
        else
            identityFor(componentData, null)
    }

    private fun identityFor(componentData: ComponentData, relativeUrl: String?): ModuleMetadataSpec.Identity {
        return ModuleMetadataSpec.Identity(
            componentData.coordinates,
            attributesFor(componentData.attributes),
            relativeUrl
        )
    }

    private fun variants(): Provider<MutableList<ModuleMetadataSpec.Variant?>?> {
        val variants: MutableList<Provider<ModuleMetadataSpec.Variant?>?> = ArrayList<Provider<ModuleMetadataSpec.Variant?>?>()
        val softwareComponent = component.get()
        val versionMappingStrategy = publication.getVersionMappingStrategy()
        checker.checkComponent(softwareComponent)
        for (variant in softwareComponent.getUsages()) {
            checkVariant(variant)
            variants.add(
                dependencyCoordinateResolverFactory.createCoordinateResolvers(variant, versionMappingStrategy)
                    .map<ModuleMetadataSpec.Variant?>(Transformer { resolvers: DependencyCoordinateResolverFactory.DependencyResolvers? ->
                        ModuleMetadataSpec.LocalVariant(
                            variant.getName(),
                            attributesFor(variant.getAttributes()),
                            capabilitiesFor(variant.getCapabilities()),
                            dependenciesOf(variant, resolvers!!.getComponentResolver()),
                            dependencyConstraintsFor(variant, resolvers.getComponentResolver()),
                            artifactsOf(variant)
                        )
                    })
            )
        }
        if (softwareComponent is ComponentWithVariants) {
            for (childComponent in (softwareComponent as ComponentWithVariants).getVariants()) {
                checker.checkComponent(childComponent)
                val childCoordinates: ModuleVersionIdentifier? = checkNotNull(coordinatesOf(childComponent))
                if (childComponent is SoftwareComponentInternal) {
                    for (variant in childComponent.getUsages()) {
                        checkVariant(variant)
                        variants.add(
                            Providers.of<ModuleMetadataSpec.Variant?>(
                                ModuleMetadataSpec.RemoteVariant(
                                    variant.getName(),
                                    attributesFor(variant.getAttributes()),
                                    availableAt(publicationCoordinates, childCoordinates!!),
                                    capabilitiesFor(variant.getCapabilities())
                                )
                            )
                        )
                    }
                }
            }
        }
        return MergeProvider<ModuleMetadataSpec.Variant?>(variants)
    }

    private fun artifactsOf(variant: SoftwareComponentVariant): MutableList<ModuleMetadataSpec.Artifact?> {
        if (variant.getArtifacts().isEmpty()) {
            return mutableListOf<ModuleMetadataSpec.Artifact?>()
        }
        val artifacts = ArrayList<ModuleMetadataSpec.Artifact?>()
        for (artifact in variant.getArtifacts()) {
            val metadataArtifact = artifactFor(artifact)
            if (metadataArtifact != null) {
                artifacts.add(metadataArtifact)
            }
        }
        return artifacts
    }

    private fun artifactFor(artifact: PublishArtifact): ModuleMetadataSpec.Artifact? {
        if (shouldNotBePublished(artifact)) {
            return null
        }
        val publishedFile = publication.getPublishedFile(artifact)
        return ModuleMetadataSpec.Artifact(
            publishedFile.getName(),
            publishedFile.getUri(),
            artifact.getFile()
        )
    }

    private fun shouldNotBePublished(artifact: PublishArtifact?): Boolean {
        return !PublishArtifactInternal.shouldBePublished(artifact)
    }

    private fun availableAt(coordinates: ModuleVersionIdentifier, targetCoordinates: ModuleVersionIdentifier): ModuleMetadataSpec.AvailableAt {
        if (coordinates.getModule() == targetCoordinates.getModule()) {
            throw InvalidUserCodeException("Cannot have a remote variant with coordinates '" + targetCoordinates.getModule() + "' that are the same as the module itself.")
        }
        return ModuleMetadataSpec.AvailableAt(
            relativeUrlTo(coordinates, targetCoordinates),
            targetCoordinates
        )
    }

    private fun dependencyFor(
        dependency: ModuleDependency,
        additionalExcludes: MutableSet<ExcludeRule?>,
        dependencyResolver: ComponentDependencyResolver,
        dependencyArtifact: DependencyArtifact?,
        variant: String?
    ): ModuleMetadataSpec.Dependency {
        val coordinates = dependencyCoordinatesFor(dependency, dependencyResolver)
        return ModuleMetadataSpec.Dependency(
            coordinates,
            excludedRulesFor(dependency, additionalExcludes),
            dependencyAttributesFor(variant, coordinates.group, coordinates.name, dependency.getAttributes()),
            capabilitySelectorsFor(dependency.getCapabilitySelectors(), coordinates),
            dependency.isEndorsingStrictVersions(),
            if (StringUtils.isNotEmpty(dependency.getReason())) dependency.getReason() else null,
            if (dependencyArtifact != null) artifactSelectorFor(dependencyArtifact) else null
        )
    }

    private fun dependencyConstraintFor(
        dependencyConstraint: DependencyConstraint,
        dependencyResolver: ComponentDependencyResolver,
        variant: String?
    ): ModuleMetadataSpec.DependencyConstraint {
        val coordinates = dependencyConstraintCoordinatesFor(dependencyConstraint, dependencyResolver)
        return ModuleMetadataSpec.DependencyConstraint(
            coordinates,
            dependencyAttributesFor(variant, coordinates.group, coordinates.name, dependencyConstraint.getAttributes()),
            if (StringUtils.isNotEmpty(dependencyConstraint.getReason())) dependencyConstraint.getReason() else null
        )
    }

    private fun dependencyCoordinatesFor(
        dependency: ModuleDependency,
        resolver: ComponentDependencyResolver
    ): ModuleMetadataSpec.DependencyCoordinates {
        if (dependency is ProjectDependency) {
            val identifier = resolver.resolveComponentCoordinates(dependency)
            return projectDependencyCoordinatesFor(identifier)
        } else if (dependency is ExternalDependency) {
            var identifier = resolver.resolveComponentCoordinates(dependency)
            if (identifier == null) {
                identifier = ResolvedCoordinates.Companion.create(dependency.getGroup(), dependency.getName(), null)
            }
            return moduleDependencyCoordinatesFor(identifier!!, dependency.getVersionConstraint())
        } else {
            throw UnsupportedOperationException("Unsupported dependency type: " + dependency.javaClass.getName())
        }
    }

    private fun dependencyConstraintCoordinatesFor(
        dependencyConstraint: DependencyConstraint,
        resolver: ComponentDependencyResolver
    ): ModuleMetadataSpec.DependencyCoordinates {
        if (dependencyConstraint is DefaultProjectDependencyConstraint) {
            val identifier = resolver.resolveComponentCoordinates(dependencyConstraint)
            return projectDependencyCoordinatesFor(identifier)
        } else {
            var identifier = resolver.resolveComponentCoordinates(dependencyConstraint)
            if (identifier == null) {
                identifier = ResolvedCoordinates.Companion.create(dependencyConstraint.getGroup(), dependencyConstraint.getName(), null)
            }
            return moduleDependencyCoordinatesFor(identifier!!, dependencyConstraint.getVersionConstraint())
        }
    }

    private fun moduleDependencyCoordinatesFor(identifier: ResolvedCoordinates, dependencyConstraint: VersionConstraint): ModuleMetadataSpec.DependencyCoordinates {
        val constraint = of(dependencyConstraint)
        val version = versionFor(constraint, identifier.getVersion())

        return ModuleMetadataSpec.DependencyCoordinates(identifier.getGroup(), identifier.getName(), version)
    }

    private fun projectDependencyCoordinatesFor(identifier: ResolvedCoordinates): ModuleMetadataSpec.DependencyCoordinates {
        val constraint = of(identifier.getVersion())
        val version = versionFor(constraint, identifier.getVersion())

        return ModuleMetadataSpec.DependencyCoordinates(identifier.getGroup(), identifier.getName(), version)
    }

    private fun artifactSelectorFor(dependencyArtifact: DependencyArtifact): ModuleMetadataSpec.ArtifactSelector {
        return ModuleMetadataSpec.ArtifactSelector(
            dependencyArtifact.getName(),
            dependencyArtifact.getType(),
            if (dependencyArtifact.getExtension() == null) dependencyArtifact.getType() else dependencyArtifact.getExtension(),
            if (Strings.isNullOrEmpty(dependencyArtifact.getClassifier())) null else dependencyArtifact.getClassifier()
        )
    }

    private fun capabilitiesFor(capabilities: MutableCollection<out Capability>): MutableList<ModuleMetadataSpec.Capability?> {
        if (capabilities.isEmpty()) {
            return mutableListOf<ModuleMetadataSpec.Capability?>()
        }

        val metadataCapabilities = ArrayList<ModuleMetadataSpec.Capability?>()
        for (capability in capabilities) {
            metadataCapabilities.add(
                ModuleMetadataSpec.Capability(
                    capability.getGroup(),
                    capability.getName(),
                    if (StringUtils.isNotEmpty(capability.getVersion())) capability.getVersion() else null
                )
            )
        }
        return metadataCapabilities
    }

    private fun attributesFor(attributes: AttributeContainer): MutableList<ModuleMetadataSpec.Attribute?> {
        if (attributes.isEmpty()) {
            return mutableListOf<ModuleMetadataSpec.Attribute?>()
        }

        val metadataAttributes = ArrayList<ModuleMetadataSpec.Attribute?>()
        for (attribute in sorted(attributes).values) {
            val name = attribute.getName()
            val value: Any? = attributes.getAttribute(attribute)
            val effectiveValue = attributeValueFor(value!!)
            requireNotNull(effectiveValue) { String.format("Cannot write attribute %s with unsupported value %s of type %s.", name, value, value.javaClass.getName()) }
            metadataAttributes.add(
                ModuleMetadataSpec.Attribute(name, effectiveValue)
            )
        }
        return metadataAttributes
    }

    private fun dependencyAttributesFor(variant: String?, group: String?, name: String?, attributes: AttributeContainer): MutableList<ModuleMetadataSpec.Attribute?> {
        checker.validateAttributes(variant, group, name, attributes)
        return attributesFor(attributes)
    }

    private fun attributeValueFor(value: Any): Any {
        if (value is Boolean || value is Int || value is String) {
            return value
        } else if (value is Named) {
            return value.getName()
        } else if (value is Enum<*>) {
            return value.name
        } else {
            return null
        }
    }

    private fun dependenciesOf(variant: SoftwareComponentVariant, dependencyResolver: ComponentDependencyResolver): MutableList<ModuleMetadataSpec.Dependency?> {
        if (variant.getDependencies().isEmpty()) {
            return mutableListOf<ModuleMetadataSpec.Dependency?>()
        }
        val dependencies = ArrayList<ModuleMetadataSpec.Dependency?>()
        val additionalExcludes: MutableSet<ExcludeRule?> = variant.getGlobalExcludes()
        for (moduleDependency in variant.getDependencies()) {
            if (moduleDependency.getArtifacts().isEmpty()) {
                dependencies.add(
                    dependencyFor(
                        moduleDependency,
                        additionalExcludes,
                        dependencyResolver,
                        null,
                        variant.getName()
                    )
                )
            } else {
                for (dependencyArtifact in moduleDependency.getArtifacts()) {
                    dependencies.add(
                        dependencyFor(
                            moduleDependency,
                            additionalExcludes,
                            dependencyResolver,
                            dependencyArtifact,
                            variant.getName()
                        )
                    )
                }
            }
        }
        return dependencies
    }

    private fun dependencyConstraintsFor(variant: SoftwareComponentVariant, dependencyResolver: ComponentDependencyResolver): MutableList<ModuleMetadataSpec.DependencyConstraint?> {
        if (variant.getDependencyConstraints().isEmpty()) {
            return mutableListOf<ModuleMetadataSpec.DependencyConstraint?>()
        }
        val dependencyConstraints = ArrayList<ModuleMetadataSpec.DependencyConstraint?>()
        for (dependencyConstraint in variant.getDependencyConstraints()) {
            dependencyConstraints.add(
                dependencyConstraintFor(dependencyConstraint, dependencyResolver, variant.getName())
            )
        }
        return dependencyConstraints
    }

    private fun versionFor(
        versionConstraint: ImmutableVersionConstraint,
        resolvedVersion: String?
    ): ModuleMetadataSpec.Version? {
        checker.sawDependencyOrConstraint()
        if (resolvedVersion == null && isEmpty(versionConstraint)) {
            return null
        }
        checker.sawVersion()

        val isStrict = !versionConstraint.getStrictVersion().isEmpty()
        val version: String?
        val preferred: String?
        if (resolvedVersion != null) {
            version = resolvedVersion
            preferred = null
        } else {
            version = if (isStrict)
                versionConstraint.getStrictVersion()
            else
                if (!versionConstraint.getRequiredVersion().isEmpty())
                    versionConstraint.getRequiredVersion()
                else
                    null
            preferred = if (!versionConstraint.getPreferredVersion().isEmpty())
                versionConstraint.getPreferredVersion()
            else
                null
        }
        return ModuleMetadataSpec.Version(
            version,
            if (isStrict) version else null,
            preferred,
            versionConstraint.getRejectedVersions()
        )
    }

    private fun collectCoordinates(coordinates: MutableMap<SoftwareComponent?, ComponentData?>) {
        for (publication in publications) {
            val component: SoftwareComponentInternal? = publication.component.getOrNull()
            if (component != null) {
                coordinates.put(
                    component,
                    ComponentData(publication.getCoordinates(), publication.getAttributes())
                )
            }
        }
    }

    private fun checkVariant(variant: SoftwareComponentVariant) {
        checker.registerVariant(
            variant.getName(),
            variant.getAttributes(),
            variant.getCapabilities()
        )
    }

    private fun excludedRulesFor(moduleDependency: ModuleDependency, additionalExcludes: MutableSet<ExcludeRule?>): MutableSet<ExcludeRule?> {
        return if (moduleDependency.isTransitive())
            Sets.union<ExcludeRule?>(additionalExcludes, moduleDependency.getExcludeRules())
        else mutableSetOf<ExcludeRule?>(DefaultExcludeRule(null, null))
    }

    private fun sorted(attributes: AttributeContainer): MutableMap<String?, Attribute<*>> {
        val sortedAttributes: MutableMap<String?, Attribute<*>> = TreeMap<String?, Attribute<*>>()
        for (attribute in attributes.keySet()) {
            sortedAttributes.put(attribute.getName(), attribute)
        }
        return sortedAttributes
    }

    private fun coordinatesOf(childComponent: SoftwareComponent?): ModuleVersionIdentifier? {
        if (childComponent is ComponentWithCoordinates) {
            return childComponent.getCoordinates()
        }
        val componentData = componentCoordinates.get(childComponent)
        if (componentData != null) {
            return componentData.coordinates
        }
        return null
    }

    private fun isEmpty(versionConstraint: ImmutableVersionConstraint?): Boolean {
        return of() == versionConstraint
    }

    companion object {
        private fun capabilitySelectorsFor(
            capabilitySelectors: MutableSet<CapabilitySelector>,
            targetComponent: ModuleMetadataSpec.DependencyCoordinates
        ): MutableList<ModuleMetadataSpec.Capability?> {
            if (capabilitySelectors.isEmpty()) {
                return mutableListOf<ModuleMetadataSpec.Capability?>()
            }

            val metadataCapabilities = ArrayList<ModuleMetadataSpec.Capability?>()
            for (capabilitySelector in capabilitySelectors) {
                metadataCapabilities.add(resolveCapability(targetComponent, capabilitySelector))
            }
            return metadataCapabilities
        }

        private fun resolveCapability(
            componentCoordinates: ModuleMetadataSpec.DependencyCoordinates,
            capabilitySelector: CapabilitySelector
        ): ModuleMetadataSpec.Capability {
            if (capabilitySelector is SpecificCapabilitySelector) {
                val specificSelector = capabilitySelector
                return ModuleMetadataSpec.Capability(
                    specificSelector.getGroup(),
                    specificSelector.getName(),
                    null
                )
            } else if (capabilitySelector is FeatureCapabilitySelector) {
                val featureSelector = capabilitySelector
                return ModuleMetadataSpec.Capability(
                    componentCoordinates.group,
                    componentCoordinates.name + "-" + featureSelector.getFeatureName(),
                    null
                )
            } else {
                throw UnsupportedOperationException("Unsupported capability selector type: " + capabilitySelector.javaClass.getName())
            }
        }

        private fun collectOwners(
            publications: MutableCollection<out PublicationInternal<*>>,
            owners: MutableMap<SoftwareComponent?, SoftwareComponent?>
        ) {
            for (publication in publications) {
                val component: SoftwareComponent? = publication.component.getOrNull()
                if (component is ComponentWithVariants) {
                    val componentWithVariants = component
                    for (child in componentWithVariants.getVariants()) {
                        owners.put(child, component)
                    }
                }
            }
        }

        fun relativeUrlTo(
            @Suppress("unused") from: ModuleVersionIdentifier?,
            to: ModuleVersionIdentifier
        ): String {
            // TODO - do not assume Maven layout
            val path = StringBuilder()
            path.append("../../")
            path.append(to.getName())
            path.append("/")
            path.append(to.getVersion())
            path.append("/")
            path.append(to.getName())
            path.append("-")
            path.append(to.getVersion())
            path.append(".module")
            return path.toString()
        }
    }
}
