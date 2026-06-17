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
package org.gradle.api.internal.artifacts.ivyservice

import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import it.unimi.dsi.fastutil.longs.LongSet
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.RootGraphNode
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.GraphStructureBuilder
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ResolvedDependencyGraph
import org.gradle.api.internal.attributes.ImmutableAttributes
import java.util.function.Supplier

/**
 * Dependency graph visitor that will build a [ResolutionResult] eagerly.
 * It is designed to be used during resolution for build dependencies.
 */
class InMemoryResolutionResultBuilder : DependencyGraphVisitor {
    private val visitedComponents: LongSet = LongOpenHashSet()
    private val builder = GraphStructureBuilder()

    private var requestAttributes: ImmutableAttributes? = null

    override fun start(root: RootGraphNode) {
        this.requestAttributes = root.getResolveState().attributes
        builder.start(root.getNodeId())
    }

    override fun visitEdges(node: DependencyGraphNode) {
        val component = node.getOwner()
        if (visitedComponents.add(component.getResultId())) {
            builder.addComponent(
                component.getResultId(),
                component.getSelectionReason(),
                component.getRepositoryName(),
                component.getComponentId(),
                component.getModuleVersion()
            )
        }

        val externalVariant = node.getExternalVariant()
        val externalVariantId = if (externalVariant != null) externalVariant.getNodeId() else -1

        builder.addNode(
            node.getNodeId(),
            component.getResultId(),
            node.getMetadata().getAttributes()!!,
            node.getMetadata().getCapabilities()!!,
            node.getMetadata().getName(),
            externalVariantId
        )

        for (edge in node.getOutgoingEdges()) {
            val failure = edge.getFailure()
            if (failure == null) {
                val targetNodes = edge.getTargetNodes()
                check(!targetNodes.isEmpty()) { "Edge " + edge + " has no target nodes." }
                if (edge.isConstraint()) {
                    // Only write the first target node for constraints, as this is historical
                    // behavior. Eventually, we should model constraints differently in the public
                    // API so they do not report a target node at all, as constraints conceptually
                    // only target components.
                    val firstTargetNode: DependencyGraphNode = targetNodes.get(0)
                    if (!firstTargetNode.getComponent().module.isVirtualPlatform()) {
                        builder.addSuccessfulEdge(
                            edge.getRequested(),
                            true,
                            firstTargetNode.getNodeId()
                        )
                    }
                } else {
                    for (targetNode in targetNodes) {
                        if (!targetNode.getComponent().module.isVirtualPlatform()) {
                            builder.addSuccessfulEdge(
                                edge.getRequested(),
                                false,
                                targetNode.getNodeId()
                            )
                        }
                    }
                }
            } else {
                builder.addFailedEdge(
                    edge.getRequested(),
                    edge.isConstraint(),
                    edge.getReason(),
                    failure
                )
            }
        }
    }

    val resolvedDependencyGraph: ResolvedDependencyGraph
        get() {
            checkNotNull(requestAttributes) { "Resolution result not computed yet" }

            val structure = builder.build()
            return ResolvedDependencyGraph(
                requestAttributes!!,
                Supplier { structure },
                null
            )
        }
}
