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
package org.gradle.api.internal.artifacts.ivyservice.projectmodule

import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ComponentResolvers
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.component.ArtifactType
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector
import org.gradle.internal.component.model.ComponentArtifactMetadata
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata
import org.gradle.internal.component.model.ComponentGraphSpecificResolveState
import org.gradle.internal.component.model.ComponentOverrideMetadata
import org.gradle.internal.resolve.resolver.ArtifactResolver
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult
import org.gradle.internal.resolve.result.BuildableComponentResolveResult
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope

@ServiceScope(Scope.Project::class)
class ProjectDependencyResolver(private val localComponentRegistry: LocalComponentRegistry, private val artifactResolver: ProjectArtifactResolver) : ComponentMetaDataResolver,
    DependencyToComponentIdResolver, ArtifactResolver, ComponentResolvers {
    override fun getArtifactResolver(): ArtifactResolver {
        return this
    }

    override fun getComponentIdResolver(): DependencyToComponentIdResolver {
        return this
    }

    override fun getComponentResolver(): ComponentMetaDataResolver {
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
        if (selector is DefaultProjectComponentSelector) {
            val projectSelector = selector
            val projectId = projectSelector.toIdentifier()
            val component = localComponentRegistry.getComponent(projectId)
            if (rejector != null && rejector.accept(component.moduleVersionId.getVersion())) {
                result.rejected(projectId, component.moduleVersionId)
            } else {
                result.resolved(component, ComponentGraphSpecificResolveState.EMPTY_STATE)
            }
        }
    }

    override fun resolve(identifier: ComponentIdentifier, componentOverrideMetadata: ComponentOverrideMetadata, result: BuildableComponentResolveResult) {
        if (isProjectModule(identifier)) {
            val projectId = identifier as ProjectComponentIdentifier
            val component = localComponentRegistry.getComponent(projectId)
            result.resolved(component, ComponentGraphSpecificResolveState.EMPTY_STATE)
        }
    }

    override fun isFetchingMetadataCheap(identifier: ComponentIdentifier): Boolean {
        return true
    }

    override fun resolveArtifactsWithType(component: ComponentArtifactResolveMetadata, artifactType: ArtifactType, result: BuildableArtifactSetResolveResult) {
        if (Companion.isProjectModule(component.getId()!!)) {
            throw UnsupportedOperationException("Resolving artifacts by type is not yet supported for project modules")
        }
    }

    override fun resolveArtifact(component: ComponentArtifactResolveMetadata, artifact: ComponentArtifactMetadata, result: BuildableArtifactResolveResult) {
        if (Companion.isProjectModule(artifact.getComponentId()!!)) {
            artifactResolver.resolveArtifact(component, artifact, result)
        }
    }

    companion object {
        private fun isProjectModule(componentId: ComponentIdentifier): Boolean {
            return componentId is ProjectComponentIdentifier
        }
    }
}
