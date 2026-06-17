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
import org.gradle.internal.Describables
import org.gradle.internal.DisplayName
import org.gradle.internal.Factory
import org.gradle.internal.component.model.ComponentConfigurationIdentifier
import org.gradle.internal.component.model.DefaultVariantMetadata
import org.gradle.internal.component.model.ExcludeMetadata
import org.gradle.internal.component.model.IvyArtifactName
import org.gradle.internal.component.model.ModuleConfigurationMetadata
import org.gradle.internal.component.model.VariantIdentifier

abstract class AbstractConfigurationMetadata : ModuleConfigurationMetadata {
    private val name: String
    private val id: VariantIdentifier
    protected val componentId: ModuleComponentIdentifier?
    val artifacts: ImmutableList<out ModuleComponentArtifactMetadata?>?
    private val transitive: Boolean
    private val visible: Boolean
    private val hierarchy: ImmutableSet<String?>?
    private val excludes: ImmutableList<ExcludeMetadata?>?
    private val attributes: ImmutableAttributes?
    private val capabilities: ImmutableCapabilities?
    private val externalVariant: Boolean

    private val lock = Any()

    // Should be final, and set in constructor
    private var configDependencies: ImmutableList<ModuleDependencyMetadata?>? = null
    private var configDependenciesFactory: Factory<MutableList<ModuleDependencyMetadata?>?>? = null

    internal constructor(
        name: String,
        id: VariantIdentifier,
        componentId: ModuleComponentIdentifier?,
        transitive: Boolean,
        visible: Boolean,
        artifacts: ImmutableList<out ModuleComponentArtifactMetadata?>?,
        hierarchy: ImmutableSet<String?>?,
        excludes: ImmutableList<ExcludeMetadata?>?,
        attributes: ImmutableAttributes?,
        configDependencies: ImmutableList<ModuleDependencyMetadata?>?,
        capabilities: ImmutableCapabilities?,
        externalVariant: Boolean
    ) {
        this.name = name
        this.id = id
        this.componentId = componentId
        this.transitive = transitive
        this.visible = visible
        this.artifacts = artifacts
        this.hierarchy = hierarchy
        this.excludes = excludes
        this.attributes = attributes
        this.configDependencies = configDependencies
        this.capabilities = capabilities
        this.externalVariant = externalVariant
    }

    internal constructor(
        name: String,
        id: VariantIdentifier,
        componentId: ModuleComponentIdentifier?,
        transitive: Boolean,
        visible: Boolean,
        artifacts: ImmutableList<out ModuleComponentArtifactMetadata?>?,
        hierarchy: ImmutableSet<String?>?,
        excludes: ImmutableList<ExcludeMetadata?>?,
        attributes: ImmutableAttributes?,
        configDependenciesFactory: Factory<MutableList<ModuleDependencyMetadata?>?>?,
        capabilities: ImmutableCapabilities?,
        externalVariant: Boolean
    ) {
        this.name = name
        this.id = id
        this.componentId = componentId
        this.transitive = transitive
        this.visible = visible
        this.artifacts = artifacts
        this.hierarchy = hierarchy
        this.excludes = excludes
        this.attributes = attributes
        this.configDependenciesFactory = configDependenciesFactory
        this.capabilities = capabilities
        this.externalVariant = externalVariant
    }

    override fun asDescribable(): DisplayName? {
        return Describables.of(id.componentId, "configuration", name)
    }

    override fun toString(): String {
        return asDescribable()!!.getDisplayName()
    }

    override fun getId(): VariantIdentifier {
        return id
    }

    override fun getName(): String {
        return name
    }

    val identifier: VariantResolveMetadata.Identifier
        get() = ComponentConfigurationIdentifier(id.componentId, name)

    override fun getHierarchy(): ImmutableSet<String?>? {
        return hierarchy
    }

    override fun isTransitive(): Boolean {
        return transitive
    }

    override fun isVisible(): Boolean {
        return visible
    }

    override fun isExternalVariant(): Boolean {
        return externalVariant
    }

    fun setDependencies(dependencies: MutableList<ModuleDependencyMetadata?>) {
        synchronized(lock) {
            assert(
                this.configDependencies == null // Can only set once: should really be part of the constructor
            )
            this.configDependencies = ImmutableList.copyOf<ModuleDependencyMetadata?>(dependencies)
        }
    }

    fun setConfigDependenciesFactory(dependenciesFactory: Factory<MutableList<ModuleDependencyMetadata?>?>?) {
        synchronized(lock) {
            assert(
                this.configDependencies == null // Can only set once: should really be part of the constructor
            )
            assert(
                this.configDependenciesFactory == null // Can only set once: should really be part of the constructor
            )
            this.configDependenciesFactory = dependenciesFactory
        }
    }

    val artifactVariants: MutableSet<out VariantResolveMetadata?>?
        get() = ImmutableSet.of<DefaultVariantMetadata?>(
            DefaultVariantMetadata(
                name,
                this.identifier,
                asDescribable()!!,
                getAttributes()!!,
                this.artifacts!!,
                getCapabilities()!!
            )
        )

    override fun getExcludes(): ImmutableList<ExcludeMetadata?>? {
        return excludes
    }

    override fun artifact(artifact: IvyArtifactName?): ModuleComponentArtifactMetadata? {
        return DefaultModuleComponentArtifactMetadata(componentId, artifact)
    }

    override fun getAttributes(): ImmutableAttributes? {
        return attributes
    }

    override fun getCapabilities(): ImmutableCapabilities? {
        return capabilities
    }

    open fun getConfigDependencies(): ImmutableList<ModuleDependencyMetadata?>? {
        synchronized(lock) {
            if (configDependenciesFactory != null) {
                configDependencies = ImmutableList.copyOf<ModuleDependencyMetadata?>(configDependenciesFactory!!.create())
                configDependenciesFactory = null
            }
            return configDependencies
        }
    }
}
