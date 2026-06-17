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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.result

import com.google.common.collect.ImmutableList
import it.unimi.dsi.fastutil.ints.Int2IntMap
import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.IntList
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.component.external.model.ImmutableCapabilities
import java.util.BitSet

/**
 * Default implementation of [GraphStructure].
 */
@JvmRecord
data class DefaultGraphStructure(
    val nodes: DefaultNodes,
    val edges: DefaultEdges,
    val components: DefaultComponents
) : GraphStructure {
    @JvmRecord
    data class DefaultNodes(
        val root: Int,
        val owners: IntList,
        val attributes: ImmutableList<ImmutableAttributes>,
        val capabilities: ImmutableList<ImmutableCapabilities>,
        val variantNames: ImmutableList<String>,
        val externalVariantIndices: Int2IntMap
    ) : GraphStructure.Nodes {
        override fun count(): Int {
            return owners.size
        }

        override fun owner(index: Int): Int {
            return owners.getInt(index)
        }

        override fun attributes(index: Int): ImmutableAttributes {
            return attributes.get(index)
        }

        override fun capabilities(index: Int): ImmutableCapabilities {
            return capabilities.get(index)
        }

        override fun variantName(index: Int): String {
            return variantNames.get(index)
        }

        override fun externalVariantIndex(index: Int): Int {
            return externalVariantIndices.getOrDefault(index, -1)
        }
    }

    @JvmRecord
    data class DefaultEdges(
        val indices: IntList,
        val selectors: ImmutableList<ComponentSelector>,
        val constraints: BitSet,
        val targetNodeIndices: IntList,
        val failures: Int2ObjectMap<GraphStructure.Edges.EdgeFailure>
    ) : GraphStructure.Edges {
        override fun start(nodeIndex: Int): Int {
            return indices.getInt(nodeIndex)
        }

        override fun end(nodeIndex: Int): Int {
            return indices.getInt(nodeIndex + 1)
        }

        override fun selector(index: Int): ComponentSelector {
            return selectors.get(index)
        }

        override fun constraint(index: Int): Boolean {
            return constraints.get(index)
        }

        override fun targetNode(index: Int): Int {
            return targetNodeIndices.getInt(index)
        }

        override fun failure(index: Int): GraphStructure.Edges.EdgeFailure {
            val failure = failures.get(index)
            requireNotNull(failure) { "No failure for edge " + index }
            return failure
        }
    }

    @JvmRecord
    data class DefaultComponents(
        val selectionReasons: ImmutableList<ComponentSelectionReasonInternal>,
        val repositoryNames: MutableList<String?>,
        val ids: ImmutableList<ComponentIdentifier>,
        val moduleVersionIds: ImmutableList<ModuleVersionIdentifier>
    ) : GraphStructure.Components {
        override fun count(): Int {
            return ids.size
        }

        override fun id(componentIndex: Int): ComponentIdentifier {
            return ids.get(componentIndex)
        }

        override fun selectionReason(componentIndex: Int): ComponentSelectionReasonInternal {
            return selectionReasons.get(componentIndex)
        }

        override fun repositoryName(componentIndex: Int): String? {
            return repositoryNames.get(componentIndex)
        }

        override fun moduleVersionId(componentIndex: Int): ModuleVersionIdentifier {
            return moduleVersionIds.get(componentIndex)
        }
    }
}
