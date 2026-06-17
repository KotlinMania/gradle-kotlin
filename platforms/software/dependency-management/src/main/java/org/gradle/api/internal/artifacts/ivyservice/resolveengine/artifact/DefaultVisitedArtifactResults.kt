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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact

import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.ResolutionStrategy

class DefaultVisitedArtifactResults(artifactsById: ImmutableList<ArtifactSet>) : VisitedArtifactResults {
    // Index of the artifact set == the id of the artifact set
    private val artifactsById: MutableList<ArtifactSet>

    init {
        this.artifactsById = artifactsById
    }

    override fun select(
        consumerServices: ArtifactSelectionServices?,
        spec: ArtifactSelectionSpec,
        lenient: Boolean
    ): SelectedArtifactResults? {
        val resolvedArtifactSets: MutableList<ResolvedArtifactSet?> = ArrayList<ResolvedArtifactSet?>(artifactsById.size)
        for (artifactSet in artifactsById) {
            val resolvedArtifacts = artifactSet.select(consumerServices, spec)
            if (!lenient || resolvedArtifacts !is UnavailableResolvedArtifactSet) {
                resolvedArtifactSets.add(resolvedArtifacts)
            } else {
                resolvedArtifactSets.add(ResolvedArtifactSet.EMPTY)
            }
        }

        return DefaultSelectedArtifactResults(spec.getSortOrder(), resolvedArtifactSets)
    }

    private class DefaultSelectedArtifactResults(sortOrder: ResolutionStrategy.SortOrder?, resolvedArtifactsById: MutableList<ResolvedArtifactSet?>) : SelectedArtifactResults {
        val artifacts: ResolvedArtifactSet?

        // Index of the artifact set == the id of the artifact set
        private val resolvedArtifactsById: MutableList<ResolvedArtifactSet?>

        init {
            this.resolvedArtifactsById = resolvedArtifactsById
            var artifacts: ResolvedArtifactSet? = CompositeResolvedArtifactSet.Companion.of(resolvedArtifactsById)
            if (sortOrder == ResolutionStrategy.SortOrder.DEPENDENCY_FIRST) {
                artifacts = CompositeResolvedArtifactSet.Companion.reverse(artifacts)
            }
            this.artifacts = artifacts
        }

        override fun getArtifactsWithId(id: Int): ResolvedArtifactSet? {
            return resolvedArtifactsById.get(id)
        }
    }
}
