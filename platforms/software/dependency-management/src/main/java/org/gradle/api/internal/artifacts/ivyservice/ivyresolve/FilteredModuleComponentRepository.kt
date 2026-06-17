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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve

import org.gradle.api.Action
import org.gradle.api.artifacts.ComponentMetadataSupplierDetails
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact
import org.gradle.api.internal.artifacts.repositories.ArtifactResolutionDetails
import org.gradle.api.internal.artifacts.repositories.resolver.MetadataFetchingCost
import org.gradle.api.internal.component.ArtifactType
import org.gradle.internal.Factory
import org.gradle.internal.action.InstantiatingAction
import org.gradle.internal.component.external.model.ExternalModuleComponentGraphResolveState
import org.gradle.internal.component.model.ComponentArtifactMetadata
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata
import org.gradle.internal.component.model.ComponentOverrideMetadata
import org.gradle.internal.component.model.ModuleSources
import org.gradle.internal.resolve.result.BuildableArtifactFileResolveResult
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult
import org.gradle.internal.resolve.result.BuildableModuleVersionListingResolveResult

class FilteredModuleComponentRepository(private val delegate: ModuleComponentRepository<ExternalModuleComponentGraphResolveState?>, val filterAction: Action<in ArtifactResolutionDetails?>) :
    ModuleComponentRepository<ExternalModuleComponentGraphResolveState?> {
    override fun getId(): String? {
        return delegate.getId()
    }

    override fun getName(): String? {
        return delegate.getName()
    }

    override fun getLocalAccess(): ModuleComponentRepositoryAccess<ExternalModuleComponentGraphResolveState?> {
        return FilteredModuleComponentRepository.FilteringAccess(delegate.getLocalAccess())
    }

    override fun getRemoteAccess(): ModuleComponentRepositoryAccess<ExternalModuleComponentGraphResolveState?> {
        return FilteredModuleComponentRepository.FilteringAccess(delegate.getRemoteAccess())
    }

    override fun getArtifactCache(): MutableMap<ComponentArtifactIdentifier?, ResolvableArtifact?>? {
        return delegate.getArtifactCache()
    }

    override fun getComponentMetadataSupplier(): InstantiatingAction<ComponentMetadataSupplierDetails?>? {
        return delegate.getComponentMetadataSupplier()
    }

    override fun isContinueOnConnectionFailure(): Boolean {
        return delegate.isContinueOnConnectionFailure()
    }

    override fun isRepositoryDisabled(): Boolean {
        return delegate.isRepositoryDisabled()
    }

    private inner class FilteringAccess(private val delegate: ModuleComponentRepositoryAccess<ExternalModuleComponentGraphResolveState?>) :
        ModuleComponentRepositoryAccess<ExternalModuleComponentGraphResolveState?> {
        override fun listModuleVersions(selector: ModuleComponentSelector, overrideMetadata: ComponentOverrideMetadata?, result: BuildableModuleVersionListingResolveResult) {
            val identifier = selector.getModuleIdentifier()
            whenModulePresent(
                identifier, null,
                Runnable { delegate.listModuleVersions(selector, overrideMetadata, result) },
                Runnable { result.listed(mutableListOf<String?>()) })
        }

        override fun resolveComponentMetaData(
            moduleComponentIdentifier: ModuleComponentIdentifier,
            requestMetaData: ComponentOverrideMetadata?,
            result: BuildableModuleComponentMetaDataResolveResult<ExternalModuleComponentGraphResolveState?>
        ) {
            whenModulePresent(
                moduleComponentIdentifier.getModuleIdentifier(), moduleComponentIdentifier,
                Runnable { delegate.resolveComponentMetaData(moduleComponentIdentifier, requestMetaData, result) },
                Runnable { result.missing() })
        }

        override fun resolveArtifactsWithType(component: ComponentArtifactResolveMetadata?, artifactType: ArtifactType?, result: BuildableArtifactSetResolveResult?) {
            delegate.resolveArtifactsWithType(component, artifactType, result)
        }

        override fun resolveArtifact(artifact: ComponentArtifactMetadata?, moduleSources: ModuleSources?, result: BuildableArtifactFileResolveResult?) {
            delegate.resolveArtifact(artifact, moduleSources, result)
        }

        override fun estimateMetadataFetchingCost(moduleComponentIdentifier: ModuleComponentIdentifier): MetadataFetchingCost {
            return
            MetadataFetchingCost > whenModulePresent<MetadataFetchingCost?>(
                moduleComponentIdentifier.getModuleIdentifier(), moduleComponentIdentifier,
                org.gradle.internal.Factory { delegate.estimateMetadataFetchingCost(moduleComponentIdentifier) },
                org.gradle.internal.Factory { MetadataFetchingCost.CHEAP })
        }

        fun whenModulePresent(id: ModuleIdentifier, moduleComponentIdentifier: ModuleComponentIdentifier?, present: Runnable, absent: Runnable) {
            val details = DefaultArtifactResolutionDetails(id, moduleComponentIdentifier)
            filterAction.execute(details)
            if (details.notFound) {
                absent.run()
            } else {
                present.run()
            }
        }

        fun <T> whenModulePresent(id: ModuleIdentifier, moduleComponentIdentifier: ModuleComponentIdentifier?, present: Factory<T?>, absent: Factory<T?>): T? {
            val details = DefaultArtifactResolutionDetails(id, moduleComponentIdentifier)
            filterAction.execute(details)
            if (details.notFound) {
                return absent.create()
            }
            return present.create()
        }
    }

    private class DefaultArtifactResolutionDetails(val moduleId: ModuleIdentifier, val componentId: ModuleComponentIdentifier?) : ArtifactResolutionDetails {
        private var notFound = false

        val isVersionListing: Boolean
            get() = this.componentId == null

        override fun notFound() {
            notFound = true
        }
    }
}
