/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve

import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.artifacts.repositories.resolver.MetadataFetchingCost
import org.gradle.api.internal.component.ArtifactType
import org.gradle.internal.component.model.ComponentArtifactMetadata
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata
import org.gradle.internal.component.model.ComponentOverrideMetadata
import org.gradle.internal.component.model.ModuleSources
import org.gradle.internal.resolve.result.BuildableArtifactFileResolveResult
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult
import org.gradle.internal.resolve.result.BuildableModuleVersionListingResolveResult

/**
 * A ModuleComponentRepository that wraps another, but assumes that _all_ access is local. This means that the accessor returned
 * by [.getLocalAccess] will attempt both local _and_ remote access operations on the delegate.
 *
 * This is used to wrap a file-backed ExternalResourceRepository instance, so that both 'local' and 'remote' operations will
 * be considered local.
 */
class LocalModuleComponentRepository<T>(delegate: ModuleComponentRepository<T?>) : BaseModuleComponentRepository<T?>(delegate) {
    private val localAccess: LocalAccess = LocalModuleComponentRepository.LocalAccess()
    private val remoteAccess = RemoteAccess<T?>()

    override fun getLocalAccess(): ModuleComponentRepositoryAccess<T?> {
        return localAccess
    }

    override fun getRemoteAccess(): ModuleComponentRepositoryAccess<T?> {
        return remoteAccess
    }

    private inner class LocalAccess : ModuleComponentRepositoryAccess<T?> {
        override fun toString(): String {
            return "local adapter > " + delegate
        }

        override fun listModuleVersions(selector: ModuleComponentSelector?, overrideMetadata: ComponentOverrideMetadata?, result: BuildableModuleVersionListingResolveResult) {
            delegate.getLocalAccess().listModuleVersions(selector, overrideMetadata, result)
            if (!result.hasResult()) {
                delegate.getRemoteAccess().listModuleVersions(selector, overrideMetadata, result)
            }
        }

        override fun resolveComponentMetaData(
            moduleComponentIdentifier: ModuleComponentIdentifier?,
            requestMetaData: ComponentOverrideMetadata?,
            result: BuildableModuleComponentMetaDataResolveResult<T?>
        ) {
            delegate.getLocalAccess().resolveComponentMetaData(moduleComponentIdentifier, requestMetaData, result)
            if (!result.hasResult()) {
                delegate.getRemoteAccess().resolveComponentMetaData(moduleComponentIdentifier, requestMetaData, result)
            }
        }

        override fun resolveArtifactsWithType(component: ComponentArtifactResolveMetadata?, artifactType: ArtifactType?, result: BuildableArtifactSetResolveResult) {
            delegate.getLocalAccess().resolveArtifactsWithType(component, artifactType, result)
            if (!result.hasResult()) {
                delegate.getRemoteAccess().resolveArtifactsWithType(component, artifactType, result)
            }
        }

        override fun resolveArtifact(artifact: ComponentArtifactMetadata?, moduleSources: ModuleSources?, result: BuildableArtifactFileResolveResult) {
            delegate.getLocalAccess().resolveArtifact(artifact, moduleSources, result)
            if (!result.hasResult()) {
                delegate.getRemoteAccess().resolveArtifact(artifact, moduleSources, result)
            }
        }

        override fun estimateMetadataFetchingCost(moduleComponentIdentifier: ModuleComponentIdentifier?): MetadataFetchingCost? {
            return delegate.getRemoteAccess().estimateMetadataFetchingCost(moduleComponentIdentifier)
        }
    }

    private class RemoteAccess<T> : ModuleComponentRepositoryAccess<T?> {
        override fun toString(): String {
            return "empty"
        }

        override fun listModuleVersions(selector: ModuleComponentSelector?, overrideMetadata: ComponentOverrideMetadata?, result: BuildableModuleVersionListingResolveResult?) {
        }

        override fun resolveComponentMetaData(
            moduleComponentIdentifier: ModuleComponentIdentifier?,
            requestMetaData: ComponentOverrideMetadata?,
            result: BuildableModuleComponentMetaDataResolveResult<T?>?
        ) {
        }

        override fun resolveArtifactsWithType(component: ComponentArtifactResolveMetadata?, artifactType: ArtifactType?, result: BuildableArtifactSetResolveResult?) {
        }

        override fun resolveArtifact(artifact: ComponentArtifactMetadata?, moduleSources: ModuleSources?, result: BuildableArtifactFileResolveResult?) {
        }

        override fun estimateMetadataFetchingCost(moduleComponentIdentifier: ModuleComponentIdentifier?): MetadataFetchingCost {
            return MetadataFetchingCost.EXPENSIVE
        }
    }
}
