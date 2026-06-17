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
package org.gradle.internal.component.external.model.maven

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.GradlePomModuleDescriptorBuilder
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema
import org.gradle.api.internal.model.NamedObjectInstantiator
import org.gradle.internal.component.external.descriptor.Configuration
import org.gradle.internal.component.external.model.AbstractLazyModuleComponentResolveMetadata.equals
import org.gradle.internal.component.external.model.AbstractModuleComponentResolveMetadata.equals
import org.gradle.internal.component.external.model.AbstractMutableModuleComponentResolveMetadata

class DefaultMutableMavenModuleResolveMetadata : AbstractMutableModuleComponentResolveMetadata, MutableMavenModuleResolveMetadata {
    val objectInstantiator: NamedObjectInstantiator

    private var packaging = "jar"
    private var relocated = false
    private var snapshotTimestamp: String? = null
    private val dependencies: ImmutableList<MavenDependencyDescriptor>
    protected val configurationDefinitions: ImmutableMap<String, Configuration>

    constructor(
        id: ModuleVersionIdentifier,
        componentIdentifier: ModuleComponentIdentifier,
        dependencies: MutableCollection<MavenDependencyDescriptor>,
        attributesFactory: AttributesFactory,
        objectInstantiator: NamedObjectInstantiator,
        schema: ImmutableAttributesSchema
    ) : super(attributesFactory, id, componentIdentifier, schema) {
        this.dependencies = ImmutableList.copyOf<MavenDependencyDescriptor>(dependencies)
        this.objectInstantiator = objectInstantiator
        this.configurationDefinitions = GradlePomModuleDescriptorBuilder.MAVEN2_CONFIGURATIONS
    }

    constructor(
        id: ModuleVersionIdentifier,
        componentIdentifier: ModuleComponentIdentifier,
        dependencies: MutableCollection<MavenDependencyDescriptor>,
        attributesFactory: AttributesFactory,
        objectInstantiator: NamedObjectInstantiator,
        schema: ImmutableAttributesSchema,
        configurationDefinitions: ImmutableMap<String, Configuration>
    ) : super(attributesFactory, id, componentIdentifier, schema) {
        this.dependencies = ImmutableList.copyOf<MavenDependencyDescriptor>(dependencies)
        this.objectInstantiator = objectInstantiator
        this.configurationDefinitions = configurationDefinitions
    }

    internal constructor(
        metadata: MavenModuleResolveMetadata,
        objectInstantiator: NamedObjectInstantiator
    ) : super(metadata) {
        this.packaging = metadata.getPackaging()
        this.relocated = metadata.isRelocated()
        this.snapshotTimestamp = metadata.getSnapshotTimestamp()
        this.dependencies = metadata.getDependencies()
        this.objectInstantiator = objectInstantiator
        this.configurationDefinitions = GradlePomModuleDescriptorBuilder.MAVEN2_CONFIGURATIONS
    }

    override fun asImmutable(): MavenModuleResolveMetadata {
        return DefaultMavenModuleResolveMetadata(this)
    }

    override fun getSnapshotTimestamp(): String? {
        return snapshotTimestamp
    }

    override fun setSnapshotTimestamp(snapshotTimestamp: String?) {
        this.snapshotTimestamp = snapshotTimestamp
    }

    override fun isRelocated(): Boolean {
        return relocated
    }

    override fun setRelocated(relocated: Boolean) {
        this.relocated = relocated
    }

    override fun getPackaging(): String {
        return packaging
    }

    override fun setPackaging(packaging: String) {
        this.packaging = packaging
    }

    override fun isPomPackaging(): Boolean {
        return DefaultMavenModuleResolveMetadata.Companion.POM_PACKAGING == packaging
    }

    override fun isKnownJarPackaging(): Boolean {
        return DefaultMavenModuleResolveMetadata.Companion.JAR_PACKAGINGS.contains(packaging)
    }

    override fun getDependencies(): ImmutableList<MavenDependencyDescriptor> {
        return dependencies
    }
}
