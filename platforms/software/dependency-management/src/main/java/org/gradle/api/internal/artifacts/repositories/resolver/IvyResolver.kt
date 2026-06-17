/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.api.artifacts.ComponentMetadataListerDetails
import org.gradle.api.artifacts.ComponentMetadataSupplierDetails
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepositoryAccess
import org.gradle.api.internal.artifacts.repositories.descriptor.IvyRepositoryDescriptor
import org.gradle.api.internal.artifacts.repositories.metadata.ImmutableMetadataSources
import org.gradle.api.internal.artifacts.repositories.metadata.MetadataArtifactProvider
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport
import org.gradle.api.internal.component.ArtifactType
import org.gradle.internal.action.InstantiatingAction
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata
import org.gradle.internal.component.external.model.ivy.IvyComponentArtifactResolveMetadata
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata
import org.gradle.internal.hash.ChecksumService
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult
import org.gradle.internal.resource.local.FileStore
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder

class IvyResolver(
    descriptor: IvyRepositoryDescriptor,
    transport: RepositoryTransport,
    locallyAvailableResourceFinder: LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata>,
    private val dynamicResolve: Boolean,
    artifactFileStore: FileStore<ModuleComponentArtifactIdentifier>,
    componentMetadataSupplierFactory: InstantiatingAction<ComponentMetadataSupplierDetails>?,
    componentMetadataVersionListerFactory: InstantiatingAction<ComponentMetadataListerDetails>?,
    metadataSources: ImmutableMetadataSources,
    metadataArtifactProvider: MetadataArtifactProvider,
    injector: Instantiator,
    checksumService: ChecksumService,
    continueOnConnectionFailure: Boolean
) : ExternalResourceResolver(
    descriptor,
    transport.isLocal(),
    transport.getRepository(),
    transport.getResourceAccessor(),
    locallyAvailableResourceFinder,
    artifactFileStore,
    metadataSources,
    metadataArtifactProvider,
    componentMetadataSupplierFactory,
    componentMetadataVersionListerFactory,
    injector,
    checksumService,
    continueOnConnectionFailure
) {
    val isM2compatible: Boolean
    private val localRepositoryAccess: IvyLocalRepositoryAccess
    private val remoteRepositoryAccess: IvyRemoteRepositoryAccess

    init {
        this.isM2compatible = descriptor.isM2Compatible()
        this.localRepositoryAccess = IvyResolver.IvyLocalRepositoryAccess()
        this.remoteRepositoryAccess = IvyResolver.IvyRemoteRepositoryAccess()
    }

    override fun toString(): String {
        return "Ivy repository '" + getName() + "'"
    }

    override fun isDynamicResolveMode(): Boolean {
        return dynamicResolve
    }

    override fun isMetaDataArtifact(artifactType: ArtifactType): Boolean {
        return artifactType == ArtifactType.IVY_DESCRIPTOR
    }

    override fun getLocalAccess(): ModuleComponentRepositoryAccess<ModuleComponentResolveMetadata> {
        return localRepositoryAccess
    }

    override fun getRemoteAccess(): ModuleComponentRepositoryAccess<ModuleComponentResolveMetadata> {
        return remoteRepositoryAccess
    }

    private inner class IvyLocalRepositoryAccess : LocalRepositoryAccess() {
        override fun resolveJavadocArtifacts(module: ComponentArtifactResolveMetadata, result: BuildableArtifactSetResolveResult) {
            val ivyModule = module as IvyComponentArtifactResolveMetadata
            val artifacts = ivyModule.getConfigurationArtifacts("javadoc")
            if (artifacts != null) {
                result.resolved(artifacts)
            }
        }

        override fun resolveSourceArtifacts(module: ComponentArtifactResolveMetadata, result: BuildableArtifactSetResolveResult) {
            val ivyModule = module as IvyComponentArtifactResolveMetadata
            val artifacts = ivyModule.getConfigurationArtifacts("sources")
            if (artifacts != null) {
                result.resolved(artifacts)
            }
        }
    }

    private inner class IvyRemoteRepositoryAccess : RemoteRepositoryAccess()
}
