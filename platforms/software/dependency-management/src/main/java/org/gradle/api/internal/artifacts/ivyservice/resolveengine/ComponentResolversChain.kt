/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine

import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ComponentResolvers
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ErrorHandlingArtifactResolver
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.component.ArtifactType
import org.gradle.internal.component.model.ComponentArtifactMetadata
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata
import org.gradle.internal.component.model.ComponentOverrideMetadata
import org.gradle.internal.resolve.resolver.ArtifactResolver
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult
import org.gradle.internal.resolve.result.BuildableComponentResolveResult

/**
 * A factory for the various resolver services backed by a chain of repositories.
 */
class ComponentResolversChain(providers: MutableList<ComponentResolvers>) : ComponentResolvers {
    private val dependencyToComponentIdResolver: DependencyToComponentIdResolverChain
    private val componentMetaDataResolver: ComponentMetaDataResolverChain
    private val artifactResolverChain: ArtifactResolver

    init {
        val depToComponentIdResolvers: MutableList<DependencyToComponentIdResolver> = ArrayList<DependencyToComponentIdResolver>(providers.size)
        val componentMetaDataResolvers: MutableList<ComponentMetaDataResolver> = ArrayList<ComponentMetaDataResolver>(1 + providers.size)
        componentMetaDataResolvers.add(VirtualComponentMetadataResolver.Companion.INSTANCE)
        val artifactResolvers: MutableList<ArtifactResolver> = ArrayList<ArtifactResolver>(providers.size)
        for (provider in providers) {
            depToComponentIdResolvers.add(provider.componentIdResolver)
            componentMetaDataResolvers.add(provider.componentResolver)
            artifactResolvers.add(provider.artifactResolver)
        }
        dependencyToComponentIdResolver = DependencyToComponentIdResolverChain(depToComponentIdResolvers)
        componentMetaDataResolver = ComponentMetaDataResolverChain(componentMetaDataResolvers)
        artifactResolverChain = ErrorHandlingArtifactResolver(ArtifactResolverChain(artifactResolvers))
    }

    override fun getComponentIdResolver(): DependencyToComponentIdResolver {
        return dependencyToComponentIdResolver
    }

    override fun getComponentResolver(): ComponentMetaDataResolver {
        return componentMetaDataResolver
    }

    override fun getArtifactResolver(): ArtifactResolver {
        return artifactResolverChain
    }

    private class ComponentMetaDataResolverChain(private val resolvers: MutableList<ComponentMetaDataResolver>) : ComponentMetaDataResolver {
        override fun resolve(identifier: ComponentIdentifier, componentOverrideMetadata: ComponentOverrideMetadata, result: BuildableComponentResolveResult) {
            for (resolver in resolvers) {
                if (result.hasResult()) {
                    return
                }
                resolver.resolve(identifier, componentOverrideMetadata, result)
            }
        }

        override fun isFetchingMetadataCheap(identifier: ComponentIdentifier): Boolean {
            for (resolver in resolvers) {
                if (!resolver.isFetchingMetadataCheap(identifier)) {
                    return false
                }
            }
            return true
        }
    }

    private class ArtifactResolverChain(private val resolvers: MutableList<ArtifactResolver>) : ArtifactResolver {
        override fun resolveArtifact(component: ComponentArtifactResolveMetadata, artifact: ComponentArtifactMetadata, result: BuildableArtifactResolveResult) {
            for (resolver in resolvers) {
                if (result.hasResult()) {
                    return
                }
                resolver.resolveArtifact(component, artifact, result)
            }
        }

        override fun resolveArtifactsWithType(component: ComponentArtifactResolveMetadata, artifactType: ArtifactType, result: BuildableArtifactSetResolveResult) {
            for (resolver in resolvers) {
                if (result.hasResult()) {
                    return
                }
                resolver.resolveArtifactsWithType(component, artifactType, result)
            }
        }
    }

    private class DependencyToComponentIdResolverChain(resolvers: MutableList<DependencyToComponentIdResolver>) : DependencyToComponentIdResolver {
        // Using an array here because we're going to iterate pretty often and it avoids the creation of an iterator
        // that checks for concurrent modification
        private val resolvers: Array<DependencyToComponentIdResolver>

        init {
            this.resolvers = resolvers.toTypedArray<DependencyToComponentIdResolver>()
        }

        override fun resolve(
            selector: ComponentSelector,
            overrideMetadata: ComponentOverrideMetadata,
            acceptor: VersionSelector,
            rejector: VersionSelector?,
            result: BuildableComponentIdResolveResult,
            consumerAttributes: ImmutableAttributes
        ) {
            for (resolver in resolvers) {
                if (result.hasResult()) {
                    return
                }
                resolver.resolve(selector, overrideMetadata, acceptor, rejector, result, consumerAttributes)
            }
        }
    }
}
