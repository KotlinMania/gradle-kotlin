/*
 * Copyright 2026 the original author or authors.
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

import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntList
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.ResolvedVariantResult
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.GraphStructure
import org.gradle.internal.Describables
import java.util.function.IntFunction

/**
 * Shared state that all components and variants of a
 * [org.gradle.api.artifacts.result.ResolutionResult] share.
 */
class ResolvedGraphResult(
    private val structure: GraphStructure,
    private val availableVariantsByComponent: MutableList<MutableList<ResolvedVariantResult>>?
) {
    private val nodesByComponent: MutableList<IntList>

    private var components: Array<ResolvedComponentResultInternal?>?
    private var variants: Array<ResolvedVariantResult?>?
    private var resolvedEdgesByTarget: MutableList<MutableSet<ResolvedDependencyResult>>? = null

    init {
        this.nodesByComponent = computeNodeIndices(structure)
    }

    /**
     * Get the underlying raw graph structure that this resolved graph is based on.
     */
    fun structure(): GraphStructure {
        return structure
    }

    /**
     * Get the component at the given index.
     */
    @Synchronized
    fun getComponent(index: Int): ResolvedComponentResultInternal {
        if (components == null) {
            components = arrayOfNulls<ResolvedComponentResultInternal>(structure.components().count())
        }
        var component = components!![index]
        if (component == null) {
            component = DefaultResolvedComponentResult(
                index,
                nodesByComponent.get(index),
                this
            )
            components!![index] = component
        }
        return component
    }

    /**
     * Get the variant at the given index.
     */
    @Synchronized
    fun getVariant(index: Int): ResolvedVariantResult {
        if (variants == null) {
            variants = arrayOfNulls<ResolvedVariantResult>(structure.nodes().count())
        }
        var variant = variants!![index]
        if (variant == null) {
            val nodes = structure.nodes()
            val externalVariantIndex = nodes.externalVariantIndex(index)

            var externalVariant: ResolvedVariantResult? = null
            if (externalVariantIndex != -1) {
                externalVariant = getVariant(externalVariantIndex)
            }

            variant = DefaultResolvedVariantResult(
                structure.components().id(nodes.owner(index)),
                Describables.of(nodes.variantName(index)),
                nodes.attributes(index),
                nodes.capabilities(index).asSet().asList(),
                externalVariant
            )
            variants!![index] = variant
        }
        return variant
    }

    /**
     * Get all incoming edges for the component at the given index.
     */
    @Synchronized
    fun getIncomingEdges(targetComponentIndex: Int): MutableSet<ResolvedDependencyResult> {
        if (resolvedEdgesByTarget == null) {
            resolvedEdgesByTarget = computeResolvedEdgesByTarget(structure, IntFunction { index: Int -> this.getComponent(index) })
        }
        return resolvedEdgesByTarget!!.get(targetComponentIndex)
    }

    /**
     * Get the available variants for the component at the given index, if any.
     */
    fun getAvailableVariants(componentIndex: Int): MutableList<ResolvedVariantResult>? {
        if (availableVariantsByComponent == null) {
            return null
        }
        return availableVariantsByComponent.get(componentIndex)
    }

    fun availableVariantsByComponent(): MutableList<MutableList<ResolvedVariantResult>>? {
        return availableVariantsByComponent
    }

    companion object {
        private fun computeNodeIndices(structure: GraphStructure): MutableList<IntList> {
            val componentCount = structure.components().count()
            val nodesByComponent: MutableList<IntList> = ArrayList<IntList>(componentCount)
            for (i in 0..<componentCount) {
                nodesByComponent.add(IntArrayList())
            }

            val nodes = structure.nodes()
            for (i in 0..<nodes.count()) {
                val ownerId = nodes.owner(i)
                nodesByComponent.get(ownerId).add(i)
            }
            return nodesByComponent
        }

        private fun computeResolvedEdgesByTarget(
            structure: GraphStructure,
            componentSource: IntFunction<ResolvedComponentResultInternal>
        ): MutableList<MutableSet<ResolvedDependencyResult>> {
            val count = structure.components().count()
            val result: MutableList<MutableSet<ResolvedDependencyResult>> = ArrayList<MutableSet<ResolvedDependencyResult>>(count)
            for (i in 0..<count) {
                result.add(LinkedHashSet<ResolvedDependencyResult?>())
            }
            for (i in 0..<count) {
                val component = componentSource.apply(i)
                for (dependency in component.getDependencies()) {
                    if (dependency is ResolvedDependencyResult) {
                        val targetComponent = dependency.getSelected() as ResolvedComponentResultInternal
                        result.get(targetComponent.index()).add(dependency)
                    }
                }
            }
            return result
        }
    }
}
