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
import com.google.common.collect.ImmutableMap
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.ivyservice.NamespaceId
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema
import org.gradle.internal.component.external.descriptor.Artifact
import org.gradle.internal.component.external.descriptor.Configuration
import org.gradle.internal.component.external.model.AbstractMutableModuleComponentResolveMetadata
import org.gradle.internal.component.model.Exclude

class DefaultMutableIvyModuleResolveMetadata : AbstractMutableModuleComponentResolveMetadata, MutableIvyModuleResolveMetadata {
    private val artifactDefinitions: ImmutableList<Artifact>
    private val configurationDefinitions: ImmutableMap<String, Configuration>
    private val dependencies: ImmutableList<IvyDependencyDescriptor>

    private var excludes: ImmutableList<Exclude>
    private var extraAttributes: ImmutableMap<NamespaceId, String>
    private var branch: String? = null

    constructor(
        attributesFactory: AttributesFactory,
        id: ModuleVersionIdentifier,
        componentIdentifier: ModuleComponentIdentifier,
        dependencies: MutableList<IvyDependencyDescriptor>,
        configurationDefinitions: MutableCollection<Configuration>,
        artifactDefinitions: MutableCollection<out Artifact>,
        excludes: MutableCollection<out Exclude>,
        schema: ImmutableAttributesSchema
    ) : super(attributesFactory, id, componentIdentifier, schema) {
        this.configurationDefinitions = toMap(configurationDefinitions)
        this.artifactDefinitions = ImmutableList.copyOf<Artifact>(artifactDefinitions)
        this.dependencies = ImmutableList.copyOf<IvyDependencyDescriptor>(dependencies)
        this.excludes = ImmutableList.of<Exclude>()
        this.extraAttributes = ImmutableMap.of<NamespaceId, String>()
        this.excludes = ImmutableList.copyOf<Exclude>(excludes)
    }

    internal constructor(metadata: IvyModuleResolveMetadata) : super(metadata) {
        this.configurationDefinitions = metadata.getConfigurationDefinitions()
        this.artifactDefinitions = metadata.getArtifactDefinitions()
        this.dependencies = metadata.getDependencies()
        this.excludes = metadata.getExcludes()
        this.branch = metadata.getBranch()
        this.extraAttributes = metadata.getExtraAttributes()
    }

    override fun getConfigurationDefinitions(): ImmutableMap<String, Configuration> {
        return configurationDefinitions
    }

    override fun getArtifactDefinitions(): ImmutableList<Artifact> {
        return artifactDefinitions
    }

    override fun getExcludes(): ImmutableList<Exclude> {
        return excludes
    }

    override fun getExtraAttributes(): ImmutableMap<NamespaceId, String> {
        return extraAttributes
    }

    override fun setExtraAttributes(extraAttributes: MutableMap<NamespaceId, String>) {
        this.extraAttributes = ImmutableMap.copyOf<NamespaceId, String>(extraAttributes)
    }

    override fun getBranch(): String? {
        return branch
    }

    override fun setBranch(branch: String) {
        this.branch = branch
    }

    override fun asImmutable(): IvyModuleResolveMetadata {
        return DefaultIvyModuleResolveMetadata(this)
    }

    override fun getDependencies(): ImmutableList<IvyDependencyDescriptor> {
        return dependencies
    }

    companion object {
        private fun toMap(configurations: MutableCollection<Configuration>): ImmutableMap<String, Configuration> {
            val builder = ImmutableMap.builder<String, Configuration>()
            for (configuration in configurations) {
                builder.put(configuration.name, configuration)
            }
            return builder.build()
        }
    }
}
