/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.VersionConstraintInternal
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.component.model.LocalComponentDependencyMetadata
import org.gradle.internal.component.model.LocalOriginDependencyMetadata

class ExternalModuleDependencyMetadataConverter(excludeRuleConverter: ExcludeRuleConverter) : AbstractDependencyMetadataConverter(excludeRuleConverter) {
    override fun createDependencyMetadata(dependency: ModuleDependency): LocalOriginDependencyMetadata {
        val externalModuleDependency = dependency as ExternalModuleDependency
        val force = externalModuleDependency.isForce()
        val changing = externalModuleDependency.isChanging()
        val transitive = externalModuleDependency.isTransitive()

        val moduleId: ModuleIdentifier? = DefaultModuleIdentifier.newId(
            nullToEmpty(dependency.getGroup()),
            nullToEmpty(dependency.getName())
        )

        val version = (externalModuleDependency.getVersionConstraint() as VersionConstraintInternal).asImmutable()

        val selector = DefaultModuleComponentSelector.newSelector(
            moduleId!!,
            version!!,
            dependency.getAttributes(),
            dependency.getCapabilitySelectors()
        )

        return LocalComponentDependencyMetadata(
            selector,
            dependency.getTargetConfiguration(),
            convertArtifacts(dependency.getArtifacts()),
            convertExcludeRules(dependency.getExcludeRules()),
            force,
            changing,
            transitive,
            false,
            dependency.isEndorsingStrictVersions(),
            dependency.getReason()
        )
    }

    override fun canConvert(dependency: ModuleDependency): Boolean {
        return dependency is ExternalModuleDependency
    }

    companion object {
        private fun nullToEmpty(input: String?): String {
            return if (input == null) "" else input
        }
    }
}
