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
import com.google.common.collect.ImmutableSet
import org.gradle.api.artifacts.capability.CapabilitySelector
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.NamedVariantIdentifier
import org.gradle.api.internal.artifacts.dsl.dependencies.PlatformSupport
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.Describables
import org.gradle.internal.DisplayName
import org.gradle.internal.component.model.ComponentArtifactMetadata
import org.gradle.internal.component.model.ExcludeMetadata
import org.gradle.internal.component.model.IvyArtifactName
import org.gradle.internal.component.model.ModuleConfigurationMetadata
import org.gradle.internal.component.model.VariantIdentifier

/**
 * An immutable [ConfigurationMetadata] wrapper around a [ComponentVariant].
 */
internal open class AbstractVariantBackedConfigurationMetadata : ModuleConfigurationMetadata {
    private val id: VariantIdentifier
    private val componentId: ModuleComponentIdentifier
    protected val variant: ComponentVariant
    private val dependencies: MutableList<out ModuleDependencyMetadata?>?

    constructor(componentId: ModuleComponentIdentifier, variant: ComponentVariant) {
        this.id = NamedVariantIdentifier(componentId, variant.name!!)
        this.componentId = componentId
        this.variant = variant
        val dependencies: MutableList<GradleDependencyMetadata?> = ArrayList<GradleDependencyMetadata?>(variant.getDependencies().size)
        // Forced dependencies are only supported for enforced platforms, so it is currently hardcoded.
        // Should we want to add this as a first class concept to Gradle metadata, then it should be available on the component variant
        // metadata as well.
        val forcedDependencies = PlatformSupport.hasForcedDependencies(variant)
        for (dependency in variant.getDependencies()) {
            val selector: ModuleComponentSelector = DefaultModuleComponentSelector.Companion.newSelector(
                DefaultModuleIdentifier.newId(dependency.getGroup(), dependency.getModule()),
                dependency.getVersionConstraint(),
                dependency.getAttributes(),
                dependency.getCapabilitySelectors()
            )
            val excludes: ImmutableList<ExcludeMetadata?>? = dependency.getExcludes()
            val dependencyArtifact = dependency.getDependencyArtifact()
            dependencies.add(GradleDependencyMetadata(selector, excludes, false, dependency.isEndorsingStrictVersions(), dependency.getReason(), forcedDependencies, dependencyArtifact))
        }
        for (dependencyConstraint in variant.getDependencyConstraints()) {
            dependencies.add(
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
                    forcedDependencies,
                    null
                )
            )
        }
        this.dependencies = ImmutableList.copyOf<GradleDependencyMetadata?>(dependencies)
    }

    constructor(componentId: ModuleComponentIdentifier, variant: ComponentVariant, dependencies: MutableList<out ModuleDependencyMetadata?>?) {
        this.id = NamedVariantIdentifier(componentId, variant.name!!)
        this.componentId = componentId
        this.variant = variant
        this.dependencies = dependencies
    }

    override fun getId(): VariantIdentifier {
        return id
    }

    override fun getName(): String {
        return variant.name!!
    }

    val identifier: VariantResolveMetadata.Identifier
        get() = variant.identifier

    override fun getHierarchy(): ImmutableSet<String?>? {
        return ImmutableSet.of<String?>(variant.name)
    }

    override fun toString(): String {
        return asDescribable()!!.getDisplayName()
    }

    override fun asDescribable(): DisplayName? {
        return Describables.of(id.componentId, "variant", variant.name!!)
    }

    override fun getAttributes(): ImmutableAttributes {
        return variant.attributes.asImmutable()
    }

    val artifactVariants: MutableSet<out VariantResolveMetadata?>?
        get() = ImmutableSet.of<ComponentVariant?>(variant)

    override fun isTransitive(): Boolean {
        return true
    }

    override fun isVisible(): Boolean {
        return true
    }

    override fun getExcludes(): ImmutableList<ExcludeMetadata?> {
        return ImmutableList.of<ExcludeMetadata?>()
    }

    override fun artifact(artifact: IvyArtifactName?): ComponentArtifactMetadata? {
        return DefaultModuleComponentArtifactMetadata(componentId, artifact)
    }

    override fun getCapabilities(): ImmutableCapabilities? {
        return variant.capabilities
    }

    val artifacts: ImmutableList<out ComponentArtifactMetadata?>
        get() = variant.artifacts

    override fun getDependencies(): MutableList<out ModuleDependencyMetadata?>? {
        return dependencies
    }

    override fun isExternalVariant(): Boolean {
        return variant.isExternalVariant()
    }

    override fun isEligibleForCaching(): Boolean {
        return variant.isEligibleForCaching()
    }
}
