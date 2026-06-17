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

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.internal.component.external.descriptor.Artifact
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactMetadata
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata
import org.gradle.internal.component.external.model.ModuleDependencyMetadata
import org.gradle.internal.component.model.ConfigurationMetadata
import org.gradle.internal.component.model.Exclude
import org.gradle.internal.component.model.ExcludeMetadata

internal class IvyConfigurationHelper(
    private val artifactDefinitions: ImmutableList<Artifact>,
    private val artifacts: MutableMap<Artifact, ModuleComponentArtifactMetadata>,
    private val excludes: ImmutableList<Exclude>,
    private val dependencies: ImmutableList<IvyDependencyDescriptor>,
    private val componentId: ModuleComponentIdentifier
) {
    fun filterArtifacts(name: String, hierarchy: MutableCollection<String>): ImmutableList<ModuleComponentArtifactMetadata> {
        val artifacts: MutableSet<ModuleComponentArtifactMetadata> = LinkedHashSet<ModuleComponentArtifactMetadata>()
        collectArtifactsFor(name, artifacts)
        for (parent in hierarchy) {
            collectArtifactsFor(parent, artifacts)
        }
        return ImmutableList.copyOf<ModuleComponentArtifactMetadata>(artifacts)
    }

    private fun collectArtifactsFor(name: String, dest: MutableCollection<ModuleComponentArtifactMetadata>) {
        for (artifact in artifactDefinitions) {
            if (artifact.configurations.contains(name)) {
                var artifactMetadata = artifacts.get(artifact)
                if (artifactMetadata == null) {
                    artifactMetadata = DefaultModuleComponentArtifactMetadata(componentId, artifact.artifactName)
                    artifacts.put(artifact, artifactMetadata)
                }
                dest.add(artifactMetadata)
            }
        }
    }

    fun filterExcludes(hierarchy: ImmutableSet<String>): ImmutableList<ExcludeMetadata> {
        val filtered = ImmutableList.builder<ExcludeMetadata>()
        for (exclude in excludes) {
            for (config in exclude.configurations!!) {
                if (hierarchy.contains(config)) {
                    filtered.add(exclude)
                    break
                }
            }
        }
        return filtered.build()
    }

    fun filterDependencies(config: ConfigurationMetadata): ImmutableList<ModuleDependencyMetadata> {
        val filteredDependencies = ImmutableList.builder<ModuleDependencyMetadata>()
        for (dependency in dependencies) {
            if (include(dependency, config.name!!, config.hierarchy)) {
                filteredDependencies.add(IvyDependencyMetadata(config, dependency))
            }
        }
        return filteredDependencies.build()
    }

    private fun include(dependency: IvyDependencyDescriptor, configName: String, hierarchy: MutableCollection<String>): Boolean {
        val dependencyConfigurations = dependency.getConfMappings().keySet()
        for (moduleConfiguration in dependencyConfigurations) {
            if (moduleConfiguration == "%" || hierarchy.contains(moduleConfiguration)) {
                return true
            }
            if (moduleConfiguration == "*") {
                var include = true
                for (conf2 in dependencyConfigurations) {
                    if (conf2.startsWith("!") && conf2.substring(1) == configName) {
                        include = false
                        break
                    }
                }
                if (include) {
                    return true
                }
            }
        }
        return false
    }
}
