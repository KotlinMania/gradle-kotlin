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

open class BaseModuleComponentRepositoryAccess<T>(val delegate: ModuleComponentRepositoryAccess<T?>) : ModuleComponentRepositoryAccess<T?> {
    override fun listModuleVersions(selector: ModuleComponentSelector?, overrideMetadata: ComponentOverrideMetadata?, result: BuildableModuleVersionListingResolveResult?) {
        delegate.listModuleVersions(selector, overrideMetadata, result)
    }

    override fun resolveComponentMetaData(
        moduleComponentIdentifier: ModuleComponentIdentifier?,
        requestMetaData: ComponentOverrideMetadata?,
        result: BuildableModuleComponentMetaDataResolveResult<T?>?
    ) {
        delegate.resolveComponentMetaData(moduleComponentIdentifier, requestMetaData, result)
    }

    override fun resolveArtifactsWithType(component: ComponentArtifactResolveMetadata?, artifactType: ArtifactType?, result: BuildableArtifactSetResolveResult?) {
        delegate.resolveArtifactsWithType(component, artifactType, result)
    }

    override fun resolveArtifact(artifact: ComponentArtifactMetadata?, moduleSources: ModuleSources?, result: BuildableArtifactFileResolveResult?) {
        delegate.resolveArtifact(artifact, moduleSources, result)
    }

    override fun estimateMetadataFetchingCost(moduleComponentIdentifier: ModuleComponentIdentifier?): MetadataFetchingCost? {
        return delegate.estimateMetadataFetchingCost(moduleComponentIdentifier)
    }
}
