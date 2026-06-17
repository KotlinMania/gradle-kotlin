/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.api.internal.artifacts.result

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import it.unimi.dsi.fastutil.ints.IntList
import org.gradle.api.Action
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.ResolvedVariantResult
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasonInternal

class DefaultResolvedComponentResult(
    private val index: Int,
    private val nodeIndices: IntList,
    private val graph: ResolvedGraphResult
) : ResolvedComponentResultInternal {
    private var variants: ImmutableList<ResolvedVariantResult>? = null
    private var dependencies: ComponentDependencies? = null

    override fun index(): Int {
        return index
    }

    override fun graph(): ResolvedGraphResult {
        return graph
    }

    override fun getId(): ComponentIdentifier {
        return graph.structure().components().id(index)
    }

    @Deprecated("")
    override fun getRepositoryName(): String? {
        return graph.structure().components().repositoryName(index)
    }

    override fun getRepositoryId(): String? {
        return graph.structure().components().repositoryName(index)
    }

    override fun getDependencies(): MutableSet<out DependencyResult> {
        return this.allDependencies.componentDependencies
    }

    override fun getDependents(): MutableSet<out ResolvedDependencyResult> {
        return graph.getIncomingEdges(index)
    }

    override fun getSelectionReason(): ComponentSelectionReasonInternal {
        return graph.structure().components().selectionReason(index)
    }

    override fun getModuleVersion(): ModuleVersionIdentifier? {
        return graph.structure().components().moduleVersionId(index)
    }

    override fun toString(): String {
        return getId().getDisplayName()
    }

    @Synchronized
    override fun getVariants(): MutableList<ResolvedVariantResult> {
        if (variants == null) {
            variants = computeVariants(graph, nodeIndices)
        }
        return variants!!
    }

    override fun getAvailableVariants(): MutableList<ResolvedVariantResult> {
        val availableVariants = graph.getAvailableVariants(index)
        if (availableVariants == null) {
            return getVariants()
        }
        return availableVariants
    }

    override fun getDependenciesForVariant(variant: ResolvedVariantResult): MutableList<DependencyResult> {
        val selectedVariants = getVariants()
        val indexInComponent = selectedVariants.indexOf(variant)
        if (indexInComponent == -1) {
            val sameName = selectedVariants.stream()
                .filter { v: ResolvedVariantResult -> v.getDisplayName() == variant.getDisplayName() }
                .findFirst()
            val moreInfo = if (sameName.isPresent())
                "A variant with the same name exists but is not the same instance."
            else
                "There's no resolved variant with the same name."
            throw InvalidUserCodeException("Variant '" + variant.getDisplayName() + "' doesn't belong to resolved component '" + this + "'. " + moreInfo + " Most likely you are using a variant from another component to get the dependencies of this component.")
        }

        return this.allDependencies.variantDependencies.get(indexInComponent)
    }

    @get:Synchronized
    private val allDependencies: ComponentDependencies
        get() {
            if (dependencies == null) {
                dependencies = computeAllDependencies(graph, nodeIndices, this)
            }
            return dependencies!!
        }

    @JvmRecord
    private data class ComponentDependencies(
        val componentDependencies: ImmutableSet<out DependencyResult>,
        val variantDependencies: ImmutableList<ImmutableList<DependencyResult>>
    )

    companion object {
        private fun computeVariants(
            graph: ResolvedGraphResult,
            nodeIndices: IntList
        ): ImmutableList<ResolvedVariantResult> {
            val size = nodeIndices.size
            val builder = ImmutableList.builderWithExpectedSize<ResolvedVariantResult>(size)
            for (i in 0..<size) {
                builder.add(graph.getVariant(nodeIndices.getInt(i)))
            }
            return builder.build()
        }

        private fun computeAllDependencies(
            graph: ResolvedGraphResult,
            nodeIndices: IntList,
            fromComponent: ResolvedComponentResult
        ): ComponentDependencies {
            val componentDependencies = ImmutableSet.builder<DependencyResult>()
            val allVariantDependencies = ImmutableList.builderWithExpectedSize<ImmutableList<DependencyResult>>(nodeIndices.size)
            for (i in nodeIndices.indices) {
                val nodeIndex = nodeIndices.getInt(i)
                val variantDependencies: ImmutableList<DependencyResult> = computeDependenciesForVariant(graph, fromComponent, nodeIndex)
                allVariantDependencies.add(variantDependencies)
                for (dependency in variantDependencies) {
                    componentDependencies.add(dependency)
                }
            }

            return ComponentDependencies(
                componentDependencies.build(),
                allVariantDependencies.build()
            )
        }

        private fun computeDependenciesForVariant(
            graph: ResolvedGraphResult,
            fromComponent: ResolvedComponentResult,
            nodeIndex: Int
        ): ImmutableList<DependencyResult> {
            val edges = graph.structure().edges()

            val start = edges.start(nodeIndex)
            val end = edges.end(nodeIndex)
            val builder = ImmutableSet.builderWithExpectedSize<DependencyResult>(end - start)
            for (i in start..<end) {
                val selector = edges.selector(i)
                val constraint = edges.constraint(i)
                val targetNodeIndex = edges.targetNode(i)
                if (targetNodeIndex != -1) {
                    val targetComponentIndex = graph.structure().nodes().owner(targetNodeIndex)
                    builder.add(
                        DefaultResolvedDependencyResult(
                            selector,
                            constraint,
                            fromComponent,
                            graph.getComponent(targetComponentIndex),
                            graph.getVariant(targetNodeIndex)
                        )
                    )
                } else {
                    val failure = edges.failure(i)
                    builder.add(
                        DefaultUnresolvedDependencyResult(
                            selector,
                            fromComponent,
                            constraint,
                            failure.failure,
                            failure.reason
                        )
                    )
                }
            }
            return builder.build().asList()
        }

        /**
         * A recursive function that traverses the dependency graph of a given module and acts on each node and edge encountered.
         *
         * @param start A ResolvedComponentResult node, which represents the entry point into the sub-section of the dependency
         * graph to be traversed
         * @param moduleAction an action to be performed on each node (module) in the graph
         * @param dependencyAction an action to be performed on each edge (dependency) in the graph
         * @param visited tracks the visited nodes during the recursive traversal
         */
        // TODO: Internal consumers of this method should prefer to operate directly on a GraphStructure,
        // which does not incur the performance penalities of building the ResolutionResult public API.
        fun eachElement(
            start: ResolvedComponentResult,
            moduleAction: Action<in ResolvedComponentResult>,
            dependencyAction: Action<in DependencyResult>,
            visited: MutableSet<ResolvedComponentResult>
        ) {
            if (!visited.add(start)) {
                return
            }
            moduleAction.execute(start)
            for (d in start.getDependencies()) {
                dependencyAction.execute(d)
                if (d is ResolvedDependencyResult) {
                    eachElement(d.getSelected(), moduleAction, dependencyAction, visited)
                }
            }
        }
    }
}
