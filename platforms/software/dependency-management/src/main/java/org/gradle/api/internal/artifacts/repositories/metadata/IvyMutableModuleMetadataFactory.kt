/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.internal.artifacts.repositories.metadata

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema
import org.gradle.internal.component.external.descriptor.Artifact
import org.gradle.internal.component.external.descriptor.Configuration
import org.gradle.internal.component.external.model.PreferJavaRuntimeVariant
import org.gradle.internal.component.external.model.ivy.DefaultMutableIvyModuleResolveMetadata
import org.gradle.internal.component.external.model.ivy.IvyDependencyDescriptor
import org.gradle.internal.component.external.model.ivy.MutableIvyModuleResolveMetadata
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.component.model.Exclude
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import javax.inject.Inject

@ServiceScope(Scope.BuildSession::class)
class IvyMutableModuleMetadataFactory @Inject constructor(
    private val moduleIdentifierFactory: ImmutableModuleIdentifierFactory,
    private val attributesFactory: AttributesFactory,
    schema: PreferJavaRuntimeVariant
) : MutableModuleMetadataFactory<MutableIvyModuleResolveMetadata> {
    private val schema: ImmutableAttributesSchema

    init {
        this.schema = schema.schema
    }

    @JvmOverloads
    fun create(
        from: ModuleComponentIdentifier,
        dependencies: MutableList<IvyDependencyDescriptor>,
        configurationDefinitions: MutableCollection<Configuration> = DEFAULT_CONFIGURATION_LIST,
        artifactDefinitions: MutableCollection<out Artifact> = createDefaultArtifact(from),
        excludes: MutableCollection<out Exclude> = ImmutableList.of<Exclude>()
    ): MutableIvyModuleResolveMetadata {
        val mvi = asVersionIdentifier(from)
        return DefaultMutableIvyModuleResolveMetadata(
            attributesFactory,
            mvi,
            from,
            dependencies,
            configurationDefinitions,
            artifactDefinitions,
            excludes,
            schema
        )
    }

    override fun createForGradleModuleMetadata(from: ModuleComponentIdentifier): MutableIvyModuleResolveMetadata {
        return create(from, ImmutableList.of<IvyDependencyDescriptor>(), ImmutableList.of<Configuration>(), createDefaultArtifact(from), ImmutableList.of<Exclude>())
    }

    private fun createDefaultArtifact(from: ModuleComponentIdentifier): ImmutableList<out Artifact> {
        return ImmutableList.of<Artifact>(Artifact(DefaultIvyArtifactName(from.getModule(), "jar", "jar"), SINGLE_DEFAULT_CONFIGURATION_NAME))
    }

    private fun asVersionIdentifier(from: ModuleComponentIdentifier): ModuleVersionIdentifier {
        return moduleIdentifierFactory.moduleWithVersion(from.getGroup(), from.getModule(), from.getVersion())!!
    }

    override fun missing(from: ModuleComponentIdentifier): MutableIvyModuleResolveMetadata {
        val metadata = create(from, ImmutableList.of<IvyDependencyDescriptor>())
        metadata.isMissing = true
        return metadata
    }

    companion object {
        private val DEFAULT_CONFIGURATION = Configuration(Dependency.DEFAULT_CONFIGURATION, true, true, ImmutableSet.of<String?>())
        private val DEFAULT_CONFIGURATION_LIST: MutableList<Configuration> = ImmutableList.of<Configuration>(DEFAULT_CONFIGURATION)
        private val SINGLE_DEFAULT_CONFIGURATION_NAME = ImmutableSet.of<String>(Dependency.DEFAULT_CONFIGURATION)
    }
}
