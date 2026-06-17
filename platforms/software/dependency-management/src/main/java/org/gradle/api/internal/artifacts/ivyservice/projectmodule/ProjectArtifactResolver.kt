/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.internal.artifacts.DefaultResolvableArtifact
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact
import org.gradle.api.internal.component.ArtifactType
import org.gradle.api.internal.project.HoldsProjectState
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectState
import org.gradle.api.internal.project.ProjectStateRegistry
import org.gradle.api.internal.tasks.NodeExecutionContext
import org.gradle.api.internal.tasks.TaskDependencyContainer
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.internal.Describables
import org.gradle.internal.component.local.model.LocalComponentArtifactMetadata
import org.gradle.internal.component.model.ComponentArtifactMetadata
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata
import org.gradle.internal.model.CalculatedValue
import org.gradle.internal.model.CalculatedValueContainerFactory
import org.gradle.internal.model.ValueCalculator
import org.gradle.internal.resolve.resolver.ArtifactResolver
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function

@ServiceScope(Scope.BuildTree::class)
class ProjectArtifactResolver(private val projectStateRegistry: ProjectStateRegistry, private val calculatedValueContainerFactory: CalculatedValueContainerFactory) : ArtifactResolver,
    HoldsProjectState {
    private val allResolvedArtifacts: MutableMap<ComponentArtifactIdentifier, ResolvableArtifact> = ConcurrentHashMap<ComponentArtifactIdentifier, ResolvableArtifact>()

    override fun resolveArtifactsWithType(component: ComponentArtifactResolveMetadata, artifactType: ArtifactType, result: BuildableArtifactSetResolveResult) {
        throw UnsupportedOperationException()
    }

    override fun resolveArtifact(component: ComponentArtifactResolveMetadata, artifact: ComponentArtifactMetadata, result: BuildableArtifactResolveResult) {
        // NOTE: This isn't thread-safe because we're not locking around allResolvedArtifacts to ensure we're not inserting multiple resolvableArtifacts for
        // the same artifact id.
        //
        // This should be replaced by a computeIfAbsent(...) to be thread-safe and ensure there's only ever one DefaultResolvableArtifact created for a single id.
        // This is not thread-safe because of lock juggling that happens for project state. When calculating the dependencies for an IDEA model, we can easily
        // deadlock when there are multiple projects that need to be locked at the same time.
        var resolvableArtifact = allResolvedArtifacts.get(artifact.getId())
        if (resolvableArtifact == null) {
            val projectArtifact = artifact as LocalComponentArtifactMetadata
            val projectId = artifact.getComponentId() as ProjectComponentIdentifier?
            val localArtifactFile: File? = projectStateRegistry.stateFor(projectId!!).fromMutableState<File>(Function { p: ProjectInternal? -> projectArtifact.file })
            if (localArtifactFile != null) {
                val artifactSource: CalculatedValue<File> = calculatedValueContainerFactory.create<File, ValueCalculator<File>>(Describables.of(artifact.getId()!!), resolveArtifactLater(artifact))
                resolvableArtifact = DefaultResolvableArtifact(
                    component.getModuleVersionId(),
                    artifact.getName()!!,
                    artifact.getId()!!,
                    TaskDependencyContainer { context: TaskDependencyResolveContext? -> context!!.add(artifact.getBuildDependencies()) },
                    artifactSource,
                    calculatedValueContainerFactory
                )
                allResolvedArtifacts.put(artifact.getId()!!, resolvableArtifact)
            }
        }
        if (resolvableArtifact != null) {
            result.resolved(resolvableArtifact)
        } else {
            result.notFound(artifact.getId())
        }
    }

    fun resolveArtifactLater(artifact: ComponentArtifactMetadata): ValueCalculator<File> {
        val projectArtifact = artifact as LocalComponentArtifactMetadata
        val projectId = artifact.getComponentId() as ProjectComponentIdentifier?
        val projectState = projectStateRegistry.stateFor(projectId!!)
        return ResolvingCalculator(projectState, projectArtifact)
    }

    override fun discardAll() {
        allResolvedArtifacts.clear()
    }

    private class ResolvingCalculator(private val projectState: ProjectState, private val projectArtifact: LocalComponentArtifactMetadata) : ValueCalculator<File> {
        override fun usesMutableProjectState(): Boolean {
            return true
        }

        override fun getOwningProject(): ProjectInternal {
            return projectState.getMutableModel()
        }

        override fun calculateValue(context: NodeExecutionContext): File {
            return projectState.fromMutableState<File>(Function { p: ProjectInternal? -> projectArtifact.file })
        }
    }
}
