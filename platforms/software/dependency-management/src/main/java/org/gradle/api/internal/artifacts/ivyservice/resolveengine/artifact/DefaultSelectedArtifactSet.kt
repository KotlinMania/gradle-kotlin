/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact

import org.gradle.api.internal.artifacts.configurations.ResolutionHost
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.results.VisitedGraphResults
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import java.util.function.Consumer

/**
 * Resolves a [ResolvedArtifactSet] into a [SelectedArtifactSet].
 */
class DefaultSelectedArtifactSet(
    artifactResolver: ResolvedArtifactSetResolver,
    graphResults: VisitedGraphResults,
    resolvedArtifacts: ResolvedArtifactSet,
    resolutionHost: ResolutionHost
) : SelectedArtifactSet {
    private val graphResults: VisitedGraphResults
    private val resolvedArtifacts: ResolvedArtifactSet
    private val artifactResolver: ResolvedArtifactSetResolver
    private val resolutionHost: ResolutionHost

    init {
        this.artifactResolver = artifactResolver
        this.graphResults = graphResults
        this.resolvedArtifacts = resolvedArtifacts
        this.resolutionHost = resolutionHost
    }

    override fun visitDependencies(context: TaskDependencyResolveContext) {
        graphResults.visitFailures(Consumer { failure: Throwable? -> context.visitFailure(failure) })
        context.add(resolvedArtifacts)
    }

    override fun visitArtifacts(visitor: ArtifactVisitor, continueOnSelectionFailure: Boolean) {
        if (graphResults.hasAnyFailure()) {
            graphResults.visitFailures(Consumer { failure: Throwable? -> visitor.visitFailure(failure) })
            if (!continueOnSelectionFailure) {
                return
            }
        }

        artifactResolver.visitInUnmanagedWorkerThread(resolvedArtifacts, visitor, resolutionHost)
    }
}
