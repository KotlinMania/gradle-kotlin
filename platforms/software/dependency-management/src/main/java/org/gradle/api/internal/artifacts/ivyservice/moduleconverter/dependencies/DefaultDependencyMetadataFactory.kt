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
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.DependencyConstraint
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.capability.CapabilitySelector
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependencyConstraint
import org.gradle.api.internal.artifacts.dependencies.DependencyConstraintInternal
import org.gradle.api.internal.artifacts.dependencies.ProjectDependencyInternal
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector
import org.gradle.internal.component.model.ExcludeMetadata
import org.gradle.internal.component.model.IvyArtifactName
import org.gradle.internal.component.model.LocalComponentDependencyMetadata
import org.gradle.internal.component.model.LocalOriginDependencyMetadata
import org.gradle.util.internal.WrapUtil

class DefaultDependencyMetadataFactory(vararg dependencyDescriptorFactories: DependencyMetadataConverter) : DependencyMetadataFactory {
    private val dependencyDescriptorFactories: MutableList<DependencyMetadataConverter>

    init {
        this.dependencyDescriptorFactories = WrapUtil.toList<DependencyMetadataConverter>(*dependencyDescriptorFactories)
    }

    override fun createDependencyMetadata(dependency: ModuleDependency): LocalOriginDependencyMetadata {
        val factoryInternal = findFactoryForDependency(dependency)
        return factoryInternal.createDependencyMetadata(dependency)
    }

    override fun createDependencyConstraintMetadata(dependencyConstraint: DependencyConstraint): LocalOriginDependencyMetadata {
        val selector = createSelector(dependencyConstraint)
        return LocalComponentDependencyMetadata(
            selector,
            null,
            ImmutableList.of<IvyArtifactName>(),
            ImmutableList.of<ExcludeMetadata>(),
            (dependencyConstraint as DependencyConstraintInternal).isForce(),
            false,
            false,
            true,
            false,
            dependencyConstraint.getReason()
        )
    }

    private fun createSelector(dependencyConstraint: DependencyConstraint): ComponentSelector {
        if (dependencyConstraint is DefaultProjectDependencyConstraint) {
            val projectDependency = dependencyConstraint.projectDependency as ProjectDependencyInternal

            return DefaultProjectComponentSelector(
                projectDependency.getTargetProjectIdentity(),
                (projectDependency.getAttributes() as ImmutableAttributes).asImmutable(),
                ImmutableSet.of<CapabilitySelector>()
            )
        }

        return DefaultModuleComponentSelector.newSelector(
            DefaultModuleIdentifier.newId(nullToEmpty(dependencyConstraint.getGroup()), nullToEmpty(dependencyConstraint.getName())),
            dependencyConstraint.getVersionConstraint(),
            dependencyConstraint.getAttributes(),
            ImmutableSet.of<E>()
        )
    }

    private fun findFactoryForDependency(dependency: ModuleDependency): DependencyMetadataConverter {
        for (dependencyMetadataConverter in dependencyDescriptorFactories) {
            if (dependencyMetadataConverter.canConvert(dependency)) {
                return dependencyMetadataConverter
            }
        }
        throw InvalidUserDataException("Can't map dependency of type: " + dependency.javaClass)
    }

    private fun nullToEmpty(input: String?): String {
        return if (input == null) "" else input
    }
}
