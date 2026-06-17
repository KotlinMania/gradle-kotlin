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

import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.internal.artifacts.DefaultResolvableArtifact
import org.gradle.api.internal.component.ArtifactType
import org.gradle.api.internal.tasks.TaskDependencyContainer
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.internal.Describables
import org.gradle.internal.component.model.ComponentArtifactMetadata
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata
import org.gradle.internal.component.model.ModuleSources
import org.gradle.internal.model.CalculatedValueFactory
import org.gradle.internal.resolve.resolver.ArtifactResolver
import org.gradle.internal.resolve.result.BuildableArtifactFileResolveResult
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult
import org.gradle.internal.resolve.result.DefaultBuildableArtifactFileResolveResult
import java.io.File
import java.util.function.Supplier

internal class RepositoryChainArtifactResolver(private val calculatedValueFactory: CalculatedValueFactory) : ArtifactResolver {
    private val repositories: MutableMap<String, ModuleComponentRepository<*>> = LinkedHashMap<String, ModuleComponentRepository<*>>()

    fun add(repository: ModuleComponentRepository<*>) {
        repositories.put(repository.id, repository)
    }

    override fun resolveArtifactsWithType(component: ComponentArtifactResolveMetadata, artifactType: ArtifactType, result: BuildableArtifactSetResolveResult) {
        val sourceRepository = findSourceRepository(component.getSources()!!)
        // First try to determine the artifacts locally before going remote
        sourceRepository.localAccess.resolveArtifactsWithType(component, artifactType, result)
        if (!result.hasResult()) {
            sourceRepository.remoteAccess.resolveArtifactsWithType(component, artifactType, result)
        }
    }

    override fun resolveArtifact(component: ComponentArtifactResolveMetadata, artifact: ComponentArtifactMetadata, result: BuildableArtifactResolveResult) {
        val sourceRepository = findSourceRepository(component.getSources()!!)
        val resolvableArtifact = sourceRepository.artifactCache.computeIfAbsent(artifact.getId()!!) { id: ComponentArtifactIdentifier? ->
            val artifactSource = calculatedValueFactory.create<File>(Describables.of(artifact.getId()!!), Supplier { resolveArtifactLater(artifact, component.getSources()!!, sourceRepository) })
            DefaultResolvableArtifact(
                component.getModuleVersionId(),
                artifact.getName()!!,
                artifact.getId()!!,
                TaskDependencyContainer { context: TaskDependencyResolveContext? -> context!!.add(artifact.getBuildDependencies()) },
                artifactSource,
                calculatedValueFactory
            )
        }

        result.resolved(resolvableArtifact)
    }

    private fun resolveArtifactLater(artifact: ComponentArtifactMetadata, sources: ModuleSources, sourceRepository: ModuleComponentRepository<*>): File {
        // First try to resolve the artifacts locally before going remote
        val artifactFile: BuildableArtifactFileResolveResult = DefaultBuildableArtifactFileResolveResult()
        sourceRepository.localAccess.resolveArtifact(artifact, sources, artifactFile)
        if (!artifactFile.hasResult()) {
            sourceRepository.remoteAccess.resolveArtifact(artifact, sources, artifactFile)
        }
        return artifactFile.getResult()!!
    }

    private fun findSourceRepository(sources: ModuleSources): ModuleComponentRepository<*> {
        val repositoryChainModuleSource: RepositoryChainModuleSource =
            sources.getSource<RepositoryChainModuleSource?>(RepositoryChainModuleSource::class.java)
                .orElseThrow<IllegalArgumentException>(java.util.function.Supplier { java.lang.IllegalArgumentException("No sources provided for artifact resolution") })!!

        val moduleVersionRepository: ModuleComponentRepository<*> = repositories.get(repositoryChainModuleSource.getRepositoryId())!!
        checkNotNull(moduleVersionRepository) { "Attempting to resolve artifacts from invalid repository" }
        return moduleVersionRepository
    }
}
