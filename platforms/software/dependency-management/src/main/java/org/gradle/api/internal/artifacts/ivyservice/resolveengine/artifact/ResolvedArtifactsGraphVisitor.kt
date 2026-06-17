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
import org.gradle.api.artifacts.capability.CapabilitySelector
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphEdge
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.attributes.immutable.artifact.ImmutableArtifactTypeRegistry
import org.gradle.internal.component.model.ComponentGraphResolveState
import org.gradle.internal.component.model.IvyArtifactName
import org.gradle.internal.component.model.VariantGraphResolveState
import org.gradle.internal.model.CalculatedValueContainerFactory

/**
 * Calculates the artifacts contributed by each node in the graph and produces a
 * [VisitedArtifactResults] containing the artifacts for each node.
 */
class ResolvedArtifactsGraphVisitor(
    private val buildProjectDependencies: Boolean,
    private val artifactTypeRegistry: ImmutableArtifactTypeRegistry,
    private val artifactSetCache: VariantArtifactSetCache,
    private val calculatedValueContainerFactory: CalculatedValueContainerFactory
) : DependencyGraphVisitor {
    // State
    private val artifactSetsById: MutableList<ArtifactSet> = ArrayList<ArtifactSet>()

    override fun visitEdges(node: DependencyGraphNode) {
        var hasTransitiveIncomingEdge = false
        var builder: ImmutableList.Builder<ArtifactSet>? = null
        var implicitArtifactSet = ArtifactSet.EMPTY

        for (edge in node.incomingEdges!!) {
            hasTransitiveIncomingEdge = hasTransitiveIncomingEdge or edge.isTransitive

            if (!edge.isConstraint) {
                val adhocArtifacts: ArtifactSet? = maybeGetAdhocArtifacts(node, edge)
                if (adhocArtifacts != null) {
                    // The artifacts for this node were modified by the dependency.
                    // Do not use the implicit artifact set.
                    if (builder == null) {
                        builder = ImmutableList.builder<ArtifactSet>()
                    }
                    builder.add(adhocArtifacts)
                } else if (implicitArtifactSet === ArtifactSet.EMPTY) {
                    // We have not visited the implicit artifacts yet.
                    // Since the dependency does not modify the artifacts, we can use the same
                    // artifact set as other dependencies that do not modify the artifacts. We call
                    // this the implicit artifact set.
                    implicitArtifactSet = artifactSetCache.getImplicitVariant(node.owner!!.resolveState, node.resolveState)
                }
            }
        }

        if (node.isRoot || hasTransitiveIncomingEdge) {
            // Since file dependencies are not modeled as actual edges, we need to verify
            // there are edges to this node that would follow this file dependency.
            for (fileDependency in node.outgoingFileEdges!!) {
                if (builder == null) {
                    builder = ImmutableList.builder<ArtifactSet>()
                }
                builder.add(FileDependencyArtifactSet(fileDependency, node.id, artifactTypeRegistry, calculatedValueContainerFactory))
            }
        }

        var nodeArtifacts = implicitArtifactSet
        if (builder != null) {
            if (implicitArtifactSet !== ArtifactSet.EMPTY) {
                val extraArtifactSets = builder.build()
                nodeArtifacts = CompositeArtifactSet.of(
                    ImmutableList.builderWithExpectedSize<ArtifactSet>(extraArtifactSets.size + 1)
                        .add(implicitArtifactSet)
                        .addAll(extraArtifactSets)
                        .build()
                )
            } else {
                nodeArtifacts = CompositeArtifactSet.of(builder.build())
            }
        }

        if (!buildProjectDependencies) {
            nodeArtifacts = NoBuildDependenciesArtifactSet(nodeArtifacts)
        }
        artifactSetsById.add(nodeArtifacts)
    }

    fun complete(): VisitedArtifactResults {
        // Copy to shrink the list to the actual size
        return DefaultVisitedArtifactResults(ImmutableList.copyOf<ArtifactSet>(artifactSetsById))
    }

    companion object {
        /**
         * Process the given edge, and if it modifies the artifacts, return the artifact set representing
         * those modified artifacts.
         *
         * @return The adhoc artifact set if this edge modifies the artifacts, or null if this edge
         * is non-adhoc and should instead contribute the implicit artifact set.
         */
        private fun maybeGetAdhocArtifacts(node: DependencyGraphNode, dependency: DependencyGraphEdge): ArtifactSet? {
            val component: ComponentGraphResolveState = node.owner!!.resolveState
            val variant: VariantGraphResolveState = node.resolveState

            val attributes: ImmutableAttributes = dependency.attributes
            val artifacts: MutableList<IvyArtifactName> = dependency.dependencyMetadata.artifacts
            val exclusions: ExcludeSpec = dependency.exclusions
            val capabilitySelectors: MutableSet<CapabilitySelector> = dependency.dependencyMetadata.selector.getCapabilitySelectors()

            // If all dependency modifiers are empty, this edge does not produce an adhoc artifact set.
            if (artifacts.isEmpty() &&
                attributes.isEmpty() &&
                capabilitySelectors.isEmpty() && !exclusions.mayExcludeArtifacts()
            ) {
                return null
            }

            return VariantResolvingArtifactSet(component, variant, attributes, artifacts, exclusions, capabilitySelectors)
        }
    }
}
