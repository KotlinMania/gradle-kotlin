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
import com.google.common.collect.ImmutableSet
import it.unimi.dsi.fastutil.ints.Int2IntMap
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
import it.unimi.dsi.fastutil.ints.Int2LongMap
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap
import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntList
import it.unimi.dsi.fastutil.longs.Long2IntMap
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap
import it.unimi.dsi.fastutil.longs.LongArrayList
import it.unimi.dsi.fastutil.longs.LongList
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.internal.attributes.AttributeDesugaring
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.capabilities.ImmutableCapability
import org.gradle.internal.component.external.model.DefaultImmutableCapability.Companion.defaultCapabilityForComponent
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.internal.resolve.ModuleVersionResolveException
import java.util.BitSet

/**
 * Responsible for building a [GraphStructure] from raw graph data.
 *
 *
 * This builder constructs a memory-efficient, CC-serializable, immutable representation
 * of the graph, which can be used directly as a data source for internal consumers or
 * as a backing structure for various public APIs.
 */
class GraphStructureBuilder {
    private var rootNodeId: Long = 0

    // Nodes
    private val nodeIdToIndex: Long2IntMap = Long2IntOpenHashMap()
    private val nodeOwnerIndices = IntArrayList()
    private val nodeAttributes: MutableList<ImmutableAttributes> = ArrayList<ImmutableAttributes>()
    private val nodeCapabilities: MutableList<ImmutableCapabilities> = ArrayList<ImmutableCapabilities>()
    private val nodeVariantNames: MutableList<String> = ArrayList<String>()
    private val externalVariantIds: Int2LongMap = Int2LongOpenHashMap()

    // Edges
    private val edgeIndices = IntArrayList()
    private val edgeConstraints = BitSet()
    private val edgeSelectors: MutableList<ComponentSelector> = ArrayList<ComponentSelector>()
    private val edgeTargetNodeIds: LongList = LongArrayList()
    private val edgeFailureMap: Int2ObjectMap<GraphStructure.Edges.EdgeFailure> = Int2ObjectOpenHashMap<GraphStructure.Edges.EdgeFailure>()

    // Components
    private val componentIdToIndex: Long2IntMap = Long2IntOpenHashMap()
    private val componentSelectionReasons: MutableList<ComponentSelectionReasonInternal> = ArrayList<ComponentSelectionReasonInternal>()
    private val componentRepositoryNames = ArrayList<String?>()
    private val componentIds: MutableList<ComponentIdentifier> = ArrayList<ComponentIdentifier>()
    private val componentModuleVersionIds: MutableList<ModuleVersionIdentifier> = ArrayList<ModuleVersionIdentifier>()

    init {
        componentIdToIndex.defaultReturnValue(-1)
        nodeIdToIndex.defaultReturnValue(-1)
    }

    /**
     * Must be called before any other methods.
     */
    fun start(rootNodeId: Long) {
        this.rootNodeId = rootNodeId
    }

    /**
     * Add a component to the graph. Must be called before any nodes belonging to the
     * component are added. The order of components added by this method is preserved
     * so that the component added by the nth call to this method is addressable by the
     * nth component in the resulting graph structure.
     */
    fun addComponent(
        id: Long,
        selectionReason: ComponentSelectionReasonInternal,
        repositoryName: String?,
        componentId: ComponentIdentifier,
        moduleVersionId: ModuleVersionIdentifier
    ) {
        componentIdToIndex.put(id, componentIds.size)
        componentSelectionReasons.add(selectionReason)
        componentRepositoryNames.add(repositoryName)
        componentIds.add(componentId)
        componentModuleVersionIds.add(moduleVersionId)
    }

    /**
     * Adds a node to the graph. Must be called after its owning component has been
     * added. The order of nodes added by this method is preserved so that the node
     * added by the nth call to this method is addressable by the nth node in
     * the resulting graph structure.
     */
    fun addNode(
        id: Long,
        ownerId: Long,
        attributes: ImmutableAttributes,
        rawCapabilities: ImmutableCapabilities,
        variantName: String,
        externalVariantId: Long
    ) {
        val nodeIndex = nodeOwnerIndices.size
        nodeIdToIndex.put(id, nodeIndex)

        val ownerIndex = componentIdToIndex.get(ownerId)
        check(ownerIndex != -1) { "Cannot find owner component for node " + id + " with owner " + ownerId }
        nodeOwnerIndices.add(ownerIndex)
        nodeAttributes.add(attributes)
        nodeCapabilities.add(capabilitiesFor(rawCapabilities, ownerIndex))
        nodeVariantNames.add(variantName)
        if (externalVariantId != -1L) {
            externalVariantIds.put(nodeIndex, externalVariantId)
        }
        edgeIndices.add(edgeSelectors.size)
    }

    /**
     * Adds a successful edge targeting the given node. The source node is
     * attributed to the last added node.
     */
    fun addSuccessfulEdge(
        selector: ComponentSelector,
        constraint: Boolean,
        targetNodeId: Long
    ) {
        val edgeIndex = edgeSelectors.size
        edgeSelectors.add(selector)
        edgeConstraints.set(edgeIndex, constraint)
        edgeTargetNodeIds.add(targetNodeId)
    }

    /**
     * Adds a failed edge targeting the given node. The source node is
     * attributed to the last added node.
     */
    fun addFailedEdge(
        selector: ComponentSelector,
        constraint: Boolean,
        reason: ComponentSelectionReasonInternal,
        failure: ModuleVersionResolveException
    ) {
        val edgeIndex = edgeSelectors.size
        edgeSelectors.add(selector)
        edgeConstraints.set(edgeIndex, constraint)
        edgeTargetNodeIds.add(-1)
        edgeFailureMap.put(
            edgeIndex, GraphStructure.Edges.EdgeFailure(
                failure,
                reason
            )
        )
    }

    // TODO: Would probably be better if a node already knew its real capabilities instead of
    // correcting for them at the API surface.
    private fun capabilitiesFor(capabilities: ImmutableCapabilities, ownerIndex: Int): ImmutableCapabilities {
        if (!capabilities.isEmpty) {
            return capabilities
        }

        val moduleVersionId = componentModuleVersionIds.get(ownerIndex)
        return ImmutableCapabilities(ImmutableSet.of<ImmutableCapability?>(defaultCapabilityForComponent(moduleVersionId)))
    }

    /**
     * Builds the finalized graph structure. Calling any other method on this builder
     * after this method has been called will result in undefined behavior.
     */
    fun build(): GraphStructure {
        edgeIndices.add(edgeSelectors.size)

        val externalVariantIndices: Int2IntMap = Int2IntOpenHashMap(externalVariantIds.size())
        for (entry in externalVariantIds.int2LongEntrySet()) {
            val externalVariantIndex = nodeIdToIndex.get(entry.getLongValue())
            check(externalVariantIndex != -1) { "Cannot find node for external variant " + entry.getLongValue() }
            externalVariantIndices.put(entry.getIntKey(), externalVariantIndex)
        }

        val edgeTargetNodeIndices: IntList = IntArrayList(edgeTargetNodeIds.size)
        for (i in edgeTargetNodeIds.indices) {
            val targetNodeId = edgeTargetNodeIds.getLong(i)
            if (targetNodeId != -1L) {
                val targetNodeIndex = nodeIdToIndex.get(targetNodeId)
                check(targetNodeIndex != -1) { "Cannot find node index for target node " + targetNodeId }
                edgeTargetNodeIndices.add(targetNodeIndex)
            } else {
                edgeTargetNodeIndices.add(-1)
            }
        }

        val rootNodeIndex = nodeIdToIndex.get(rootNodeId)

        // Trim the lists to the actual size to save memory.
        // The below ImmutableList.copy also serves this same purpose.
        nodeOwnerIndices.trim()
        edgeIndices.trim()
        componentRepositoryNames.trimToSize()

        return DefaultGraphStructure(
            DefaultGraphStructure.DefaultNodes(
                rootNodeIndex,
                nodeOwnerIndices,
                ImmutableList.copyOf<ImmutableAttributes>(nodeAttributes),
                ImmutableList.copyOf<ImmutableCapabilities>(nodeCapabilities),
                ImmutableList.copyOf<String>(nodeVariantNames),
                externalVariantIndices
            ),
            DefaultGraphStructure.DefaultEdges(
                edgeIndices,
                ImmutableList.copyOf<ComponentSelector>(edgeSelectors),
                edgeConstraints,
                edgeTargetNodeIndices,
                edgeFailureMap
            ),
            DefaultGraphStructure.DefaultComponents(
                ImmutableList.copyOf<ComponentSelectionReasonInternal>(componentSelectionReasons),
                componentRepositoryNames,
                ImmutableList.copyOf<ComponentIdentifier>(componentIds),
                ImmutableList.copyOf<ModuleVersionIdentifier>(componentModuleVersionIds)
            )
        )
    }

    companion object {
        /**
         * Create a minimal graph containing only a root component and a
         * root variant, with no other outgoing edges.
         */
        fun empty(
            id: ModuleVersionIdentifier,
            componentIdentifier: ComponentIdentifier,
            attributes: ImmutableAttributes,
            rawCapabilities: ImmutableCapabilities,
            rootVariantName: String,
            attributeDesugaring: AttributeDesugaring
        ): GraphStructure {
            val builder = GraphStructureBuilder()
            builder.start(1L)
            builder.addComponent(0L, ComponentSelectionReasons.root(), null, componentIdentifier, id)
            builder.addNode(
                1L,
                0L,
                attributeDesugaring.desugar(attributes),
                rawCapabilities,
                rootVariantName,
                -1
            )

            return builder.build()
        }
    }
}
