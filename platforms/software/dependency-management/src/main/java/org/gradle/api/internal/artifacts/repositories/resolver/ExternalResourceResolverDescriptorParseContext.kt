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
package org.gradle.api.internal.artifacts.repositories.resolver

import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ComponentResolvers
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.DescriptorParseContext
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector
import org.gradle.api.internal.artifacts.repositories.metadata.DefaultMetadataFileSource
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.component.ArtifactType
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier
import org.gradle.internal.component.model.ComponentArtifactMetadata
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata
import org.gradle.internal.component.model.DefaultComponentOverrideMetadata
import org.gradle.internal.component.model.ModuleSource
import org.gradle.internal.component.model.MutableModuleSources
import org.gradle.internal.hash.ChecksumService
import org.gradle.internal.resolve.resolver.ArtifactResolver
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult
import org.gradle.internal.resolve.result.BuildableComponentResolveResult
import org.gradle.internal.resolve.result.DefaultBuildableArtifactResolveResult
import org.gradle.internal.resolve.result.DefaultBuildableArtifactSetResolveResult
import org.gradle.internal.resolve.result.DefaultBuildableComponentIdResolveResult
import org.gradle.internal.resolve.result.DefaultBuildableComponentResolveResult
import org.gradle.internal.resource.local.FileResourceRepository
import org.gradle.internal.resource.local.LocallyAvailableExternalResource
import java.util.function.Consumer

/**
 * ParserSettings that control the scope of searches carried out during parsing.
 * If the parser asks for a resolver for the currently resolving revision, the resolver scope is only the repository where the module was resolved.
 * If the parser asks for a resolver for a different revision, the resolver scope is all repositories.
 */
class ExternalResourceResolverDescriptorParseContext(
    private val mainResolvers: ComponentResolvers,
    private val fileResourceRepository: FileResourceRepository,
    private val checksumService: ChecksumService
) : DescriptorParseContext {
    private val sources = MutableModuleSources()

    override fun getMetaDataArtifact(moduleComponentIdentifier: ModuleComponentIdentifier, artifactType: ArtifactType): LocallyAvailableExternalResource {
        return resolveMetaDataArtifactFile(moduleComponentIdentifier, mainResolvers.componentResolver, mainResolvers.artifactResolver, artifactType)
    }

    override fun getMetaDataArtifact(selector: ModuleComponentSelector, acceptor: VersionSelector, artifactType: ArtifactType): LocallyAvailableExternalResource {
        val idResolveResult: BuildableComponentIdResolveResult = DefaultBuildableComponentIdResolveResult()
        mainResolvers.componentIdResolver.resolve(selector, DefaultComponentOverrideMetadata.EMPTY, acceptor, null, idResolveResult, ImmutableAttributes.EMPTY)
        return getMetaDataArtifact((idResolveResult.id as org.gradle.api.artifacts.component.ModuleComponentIdentifier?)!!, artifactType)
    }

    private fun resolveMetaDataArtifactFile(
        moduleComponentIdentifier: ModuleComponentIdentifier,
        componentResolver: ComponentMetaDataResolver,
        artifactResolver: ArtifactResolver,
        artifactType: ArtifactType
    ): LocallyAvailableExternalResource {
        val moduleVersionResolveResult: BuildableComponentResolveResult = DefaultBuildableComponentResolveResult()
        componentResolver.resolve(moduleComponentIdentifier, DefaultComponentOverrideMetadata.EMPTY, moduleVersionResolveResult)

        val moduleArtifactsResolveResult: BuildableArtifactSetResolveResult = DefaultBuildableArtifactSetResolveResult()
        val artifactMetadata: ComponentArtifactResolveMetadata = moduleVersionResolveResult.state.prepareForArtifactResolution().getArtifactMetadata()
        artifactResolver.resolveArtifactsWithType(artifactMetadata, artifactType, moduleArtifactsResolveResult)

        val artifactResolveResult: BuildableArtifactResolveResult = DefaultBuildableArtifactResolveResult()
        val artifactMetaData: ComponentArtifactMetadata = moduleArtifactsResolveResult.result!!.iterator().next()!!
        artifactResolver.resolveArtifact(artifactMetadata, artifactMetaData, artifactResolveResult)

        val file = artifactResolveResult.getResult()!!.file
        val resource = fileResourceRepository.resource(file)
        val id = artifactMetaData.getId()
        if (id is ModuleComponentArtifactIdentifier) {
            sources.add(DefaultMetadataFileSource(id, file, checksumService.sha1(file)))
        }
        return resource
    }

    fun appendSources(sources: MutableModuleSources) {
        this.sources.withSources(Consumer { source: ModuleSource? -> sources.add(source!!) })
    }
}
