/*
 * Copyright 2014 the original author or authors.
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

import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.component.ArtifactType
import org.gradle.internal.component.model.ComponentArtifactMetadata
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata
import org.gradle.internal.component.model.ComponentOverrideMetadata
import org.gradle.internal.resolve.ModuleVersionNotFoundException
import org.gradle.internal.resolve.resolver.ArtifactResolver
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult
import org.gradle.internal.resolve.result.BuildableComponentResolveResult

/**
 * Used as a fallback when no repositories are defined for a given resolution.
 */
class NoRepositoriesResolver : ComponentResolvers, DependencyToComponentIdResolver, ComponentMetaDataResolver, ArtifactResolver {
    override fun getComponentIdResolver(): DependencyToComponentIdResolver {
        return this
    }

    override fun getComponentResolver(): ComponentMetaDataResolver {
        return this
    }

    override fun getArtifactResolver(): ArtifactResolver {
        return this
    }

    override fun resolve(
        selector: ComponentSelector,
        overrideMetadata: ComponentOverrideMetadata,
        acceptor: VersionSelector,
        rejector: VersionSelector?,
        result: BuildableComponentIdResolveResult,
        consumerAttributes: ImmutableAttributes
    ) {
        result.failed(
            ModuleVersionNotFoundException(
                selector,
                org.gradle.internal.Factory { String.format("Cannot resolve external dependency %s because no repositories are defined.", selector) },
                ImmutableList.of<String?>()
            )
        )
    }

    override fun resolve(identifier: ComponentIdentifier, componentOverrideMetadata: ComponentOverrideMetadata, result: BuildableComponentResolveResult) {
        throw UnsupportedOperationException()
    }

    override fun isFetchingMetadataCheap(identifier: ComponentIdentifier): Boolean {
        return true
    }

    override fun resolveArtifactsWithType(component: ComponentArtifactResolveMetadata, artifactType: ArtifactType, result: BuildableArtifactSetResolveResult) {
        throw UnsupportedOperationException()
    }

    override fun resolveArtifact(component: ComponentArtifactResolveMetadata, artifact: ComponentArtifactMetadata, result: BuildableArtifactResolveResult) {
        throw UnsupportedOperationException()
    }
}
