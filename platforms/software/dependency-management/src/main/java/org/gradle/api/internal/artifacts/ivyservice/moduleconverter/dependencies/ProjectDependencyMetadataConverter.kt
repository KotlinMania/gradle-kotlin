/*
 * Copyright 2013 the original author or authors.
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

import com.google.common.collect.ImmutableSet
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.capability.CapabilitySelector
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.internal.artifacts.dependencies.ProjectDependencyInternal
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector
import org.gradle.internal.component.model.LocalComponentDependencyMetadata
import org.gradle.internal.component.model.LocalOriginDependencyMetadata

class ProjectDependencyMetadataConverter(excludeRuleConverter: ExcludeRuleConverter) : AbstractDependencyMetadataConverter(excludeRuleConverter) {
    override fun createDependencyMetadata(dependency: ModuleDependency): LocalOriginDependencyMetadata {
        val projectDependency = dependency as ProjectDependencyInternal

        val selector: ComponentSelector = DefaultProjectComponentSelector(
            projectDependency.getTargetProjectIdentity(),
            (projectDependency.getAttributes() as AttributeContainerInternal).asImmutable(),
            ImmutableSet.copyOf<CapabilitySelector>(projectDependency.getCapabilitySelectors())
        )

        val excludes = convertExcludeRules(dependency.getExcludeRules())
        return LocalComponentDependencyMetadata(
            selector,
            projectDependency.getTargetConfiguration(),
            convertArtifacts(dependency.getArtifacts()),
            excludes,
            false,
            false,
            dependency.isTransitive(),
            false,
            dependency.isEndorsingStrictVersions(),
            dependency.getReason()
        )
    }

    override fun canConvert(dependency: ModuleDependency): Boolean {
        return dependency is ProjectDependency
    }
}
