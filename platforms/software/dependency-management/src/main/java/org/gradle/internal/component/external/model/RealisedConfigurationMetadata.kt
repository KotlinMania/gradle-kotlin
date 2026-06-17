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
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.component.model.ComponentArtifactMetadata.getName
import org.gradle.internal.component.model.ConfigurationGraphResolveMetadata.isVisible
import org.gradle.internal.component.model.ExcludeMetadata
import org.gradle.internal.component.model.VariantGraphResolveMetadata.getAttributes
import org.gradle.internal.component.model.VariantGraphResolveMetadata.getCapabilities
import org.gradle.internal.component.model.VariantGraphResolveMetadata.getName
import org.gradle.internal.component.model.VariantGraphResolveMetadata.isExternalVariant
import org.gradle.internal.component.model.VariantGraphResolveMetadata.isTransitive
import org.gradle.internal.component.model.VariantIdentifier
import org.gradle.internal.component.model.VariantResolveMetadata.isExternalVariant

class RealisedConfigurationMetadata(
    name: String,
    id: VariantIdentifier,
    componentId: ModuleComponentIdentifier,
    transitive: Boolean,
    visible: Boolean,
    hierarchy: ImmutableSet<String>,
    artifacts: ImmutableList<out ModuleComponentArtifactMetadata>,
    excludes: ImmutableList<ExcludeMetadata>,
    attributes: ImmutableAttributes,
    capabilities: ImmutableCapabilities,
    configDependencies: ImmutableList<ModuleDependencyMetadata>,
    val isAddedByRule: Boolean,
    externalVariant: Boolean
) : AbstractConfigurationMetadata(name, id, componentId, transitive, visible, artifacts, hierarchy, excludes, attributes, configDependencies, capabilities, externalVariant) {
    constructor(
        name: String,
        id: VariantIdentifier,
        componentId: ModuleComponentIdentifier,
        transitive: Boolean,
        visible: Boolean,
        hierarchy: ImmutableSet<String>,
        artifacts: ImmutableList<out ModuleComponentArtifactMetadata>,
        excludes: ImmutableList<ExcludeMetadata>,
        componentLevelAttributes: ImmutableAttributes,
        capabilities: ImmutableCapabilities,
        addedByRule: Boolean,
        externalVariant: Boolean
    ) : this(name, id, componentId, transitive, visible, hierarchy, artifacts, excludes, componentLevelAttributes, capabilities, null, addedByRule, externalVariant)

    val dependencies: MutableList<out ModuleDependencyMetadata>
        get() = getConfigDependencies()

    fun withDependencies(dependencies: ImmutableList<ModuleDependencyMetadata>): RealisedConfigurationMetadata {
        return RealisedConfigurationMetadata(
            getName(),
            getId(),
            componentId,
            isTransitive(),
            isVisible(),
            getHierarchy(),
            getArtifacts(),
            excludes,
            getAttributes(),
            getCapabilities(),
            dependencies,
            this.isAddedByRule,
            isExternalVariant()
        )
    }
}
