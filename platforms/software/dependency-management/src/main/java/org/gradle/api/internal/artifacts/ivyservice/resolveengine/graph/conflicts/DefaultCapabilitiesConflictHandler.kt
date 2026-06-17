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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts

import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.CapabilitiesResolutionInternal
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.NodeState
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ResolveState
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.strict.StrictVersionConstraints.equals
import org.gradle.api.internal.capabilities.CapabilityInternal
import org.gradle.internal.component.model.ComponentGraphResolveMetadata.getId
import org.gradle.internal.component.model.ComponentGraphResolveState.getId
import org.gradle.internal.component.model.VariantGraphResolveMetadata.getId
import java.util.ArrayDeque
import java.util.Deque

class DefaultCapabilitiesConflictHandler(rules: ImmutableList<CapabilitiesResolutionInternal.CapabilityResolutionRule>, resolveState: ResolveState) : CapabilitiesConflictHandler {
    private val resolver: CapabilityConflictResolver
    private val resolveState: ResolveState

    /**
     * Tracks conflicted nodes by the capability id.
     */
    private val capabilityWithoutVersionToTracker: MutableMap<String, ConflictedNodesTracker> = HashMap<String, ConflictedNodesTracker>()

    private val conflicts: Deque<String> = ArrayDeque<String>()

    init {
        this.resolver = CapabilityConflictResolver(rules)
        this.resolveState = resolveState
    }

    override fun registerCandidate(node: NodeState): Boolean {
        val capabilities = node.getMetadata().getCapabilities()
        if (capabilities!!.isEmpty) {
            // If there's more than one node selected for the same component, we need to add
            // the implicit capability to the list, in order to make sure we can discover conflicts
            // between variants of the same module.
            // We also need to add the implicit capability if it was seen before as an explicit
            // capability in order to detect the conflict between the two.
            // Note that the fact that the implicit capability is not included in other cases
            // is not a bug but a performance optimization.
            if (node.getComponent().hasMoreThanOneSelectedNodeUsingVariantAwareResolution() ||
                hasSeenNonDefaultCapabilityExplicitly(node.getComponent().getImplicitCapability())
            ) {
                val capability: CapabilityInternal = node.getComponent().getImplicitCapability()
                return registerCapability(node, capability)
            }

            return false
        } else {
            val defaultCapabilityHasConflict = hasSeenNonDefaultCapabilityExplicitly(node.getComponent().getImplicitCapability())

            var foundConflict = false
            for (capability in capabilities) {
                // Only process non-default capabilities
                // Or, for the default capability if we have seen that capability on a node for which it is not the default
                // Or, the component has multiple selected variants, in which case two nodes in that component may conflict with each other
                if ((capability != node.getComponent().getImplicitCapability()) || defaultCapabilityHasConflict || node.getComponent().hasMoreThanOneSelectedNodeUsingVariantAwareResolution()) {
                    foundConflict = foundConflict or registerCapability(node, capability!!)
                }
            }

            return foundConflict
        }
    }

    private fun registerCapability(node: NodeState, capability: CapabilityInternal): Boolean {
        val tracker = capabilityWithoutVersionToTracker.computeIfAbsent(capability.getCapabilityId()) { k: String? -> ConflictedNodesTracker(capability) }
        tracker.removeDeselectedCandidates()

        // This is a performance optimization. Most modules do not declare capabilities. So, instead of systematically registering
        // an implicit capability for each module that we see, we only consider modules which _declare_ capabilities. If they do,
        // then we try to find a module which provides the same capability. If that module has been found, then we register it.
        // Otherwise, we have nothing to do. This avoids most registrations.
        val module = resolveState.findModule(DefaultModuleIdentifier.newId(capability.getGroup(), capability.getName()))
        if (module != null) {
            for (version in module.getVersions()) {
                for (potentialNode in version.getNodes()) {
                    // Collect nodes as implicit capability providers if different from current node, selected and not having explicit capabilities
                    if (node !== potentialNode && potentialNode.isSelected() && potentialNode.getMetadata().getCapabilities()!!.isEmpty) {
                        tracker.add(potentialNode)
                    }
                }
            }
        }

        if (tracker.add(node) && tracker.hasPotentialConflict()) {
            // If the root node is in conflict, find its module ID
            var rootId: ModuleIdentifier? = null
            for (ns in tracker) {
                if (ns.isRoot()) {
                    rootId = ns.getComponent().getModule().getId()
                }
            }

            // TODO: Why do we need this copy? Why can't we update the nodes in the tracker?
            val candidatesForConflict = tracker.candidatesCopy
            if (rootId != null && candidatesForConflict.size > 1) {
                // This is a special case for backwards compatibility: it is possible to have
                // a cycle where the root component depends on a library which transitively
                // depends on a different version of the root module. In this case, we effectively
                // allow 2 modules to have the same capability, so we filter the nodes coming
                // from transitive dependencies
                val rootModuleId: ModuleIdentifier? = rootId
                candidatesForConflict.removeIf { n: NodeState? -> !n!!.isRoot() && n.getComponent().getModule().getId() == rootModuleId }
            }

            // For a conflict we want at least 2 nodes, and at least one of them should not be rejected
            // TODO: Seems odd to filter for rejected nodes here
            if (candidatesForConflict.size > 1 && !candidatesForConflict.stream().allMatch { obj: NodeState? -> obj!!.isRejectedForCapabilityConflict() }) {
                if (tracker.createOrUpdateConflict(candidatesForConflict)) {
                    conflicts.add(tracker.capabilityId)
                }
                return true
            }
        }

        return false
    }

    override fun hasConflicts(): Boolean {
        return !conflicts.isEmpty()
    }

    override fun resolveNextConflict() {
        val capabilityInConflict = conflicts.remove()

        val conflictTracker: ConflictedNodesTracker = capabilityWithoutVersionToTracker.get(capabilityInConflict)!!
        val conflict = conflictTracker.updateClearAndReturnConflict()

        if (!conflict.isValidConflict) {
            return
        }

        // TODO: What if, after we filter, we only have nodes with the default capability?
        // We should treat this conflict as a version conflict.
        resolver.resolve(conflict.group, conflict.name, conflict.nodes)
    }

    /**
     * Has the given capability been seen as a non-default capability on a node?
     * This is needed to determine if default capabilities need to enter conflict detection.
     */
    private fun hasSeenNonDefaultCapabilityExplicitly(capability: CapabilityInternal): Boolean {
        return capabilityWithoutVersionToTracker.containsKey(capability.getCapabilityId())
    }

    private class CapabilityConflict(group: String, name: String, nodes: MutableSet<NodeState>, nodeToDependentNodes: MutableMap<NodeState, MutableSet<NodeState>>) {
        private val group: String
        private val name: String
        private val nodes: MutableSet<NodeState>
        private val nodeToDependentNodes: MutableMap<NodeState, MutableSet<NodeState>>

        private constructor(group: String, name: String, nodes: MutableSet<NodeState>, alreadySeen: Boolean) : this(
            group,
            name,
            nodes,
            if (alreadySeen) buildDependentRelationships(nodes) < NodeState,
            Set < NodeState shr mutableMapOf<Any, Any>()
        )

        init {
            this.group = group
            this.name = name
            this.nodes = nodes
            this.nodeToDependentNodes = nodeToDependentNodes
        }

        fun withDifferentNodes(selectedNodes: MutableSet<NodeState>): CapabilityConflict {
            return CapabilityConflict(group, name, selectedNodes, nodeToDependentNodes)
        }

        val isValidConflict: Boolean
            /**
             * Validates that the conflict has at least one node that is not rejected.
             *
             * @return `true` if the conflict is valid
             */
            get() = !nodes.isEmpty() && nodes.stream().anyMatch { node: NodeState? -> !node!!.isRejectedForCapabilityConflict() }

        companion object {
            private fun buildDependentRelationships(nodes: MutableSet<NodeState>): MutableMap<NodeState, MutableSet<NodeState>> {
                val nodeToDependents = HashMap<NodeState, MutableSet<NodeState>>()
                for (node in nodes) {
                    val reachableNodes = node.getReachableNodes()
                    for (possibleDependency in nodes) {
                        if (node === possibleDependency) {
                            continue
                        }
                        if (reachableNodes.contains(possibleDependency)) {
                            nodeToDependents.computeIfAbsent(possibleDependency) { k: NodeState? -> HashSet<NodeState?>() }.add(node)
                        }
                    }
                }
                return nodeToDependents
            }
        }
    }

    /**
     * Tracks nodes that provide a given capability, detects conflicts among them,
     * and manages the lifecycle of pending conflicts.
     *
     *
     * Nodes are added to this tracker as **candidates** — they provide
     * the capability but are not necessarily in a conflict. A conflict is only created
     * when multiple candidates are detected. Only nodes that are part of a pending
     * conflict have their [NodeState.markInCapabilityConflict] called, and
     * only those nodes are notified when they leave a conflict.
     */
    private class ConflictedNodesTracker(capability: CapabilityInternal) : Iterable<NodeState> {
        private val group: String
        private val name: String
        private val capabilityId: String
        private val previousConflictedNodes: MutableList<MutableSet<NodeState>> = ArrayList<MutableSet<NodeState>>()

        /**
         * All nodes known to provide this capability. This set is used for
         * conflict detection. When it contains more than one node, a conflict
         * may exist. Nodes in this set are not necessarily in a conflict.
         */
        private var candidates: MutableSet<NodeState> = LinkedHashSet<NodeState>()

        /**
         * If non-null, the capability tracked by this tracker has a pending conflict
         * that must be resolved.
         */
        private var pendingConflict: CapabilityConflict? = null

        init {
            this.group = capability.getGroup()
            this.name = capability.getName()
            this.capabilityId = capability.getCapabilityId()
        }

        /**
         * Updates, clears and returns the current conflict.
         *
         * If we encounter a conflict with deselected nodes, it is possible that the deselection was
         * caused by the conflict deselection (it can happen for other reasons too)
         * Record this fact, so that later if we see the conflict again, we can compute node relationships
         * between conflict participants.
         */
        fun updateClearAndReturnConflict(): CapabilityConflict {
            val currentConflict = pendingConflict
            this.pendingConflict = null

            val selectedNodes: MutableSet<NodeState> = LinkedHashSet<NodeState>()
            val didFilter = false
            for (node in currentConflict.nodes) {
                if (node.isSelected() || (!currentConflict.nodeToDependentNodes.isEmpty() && currentConflict.nodeToDependentNodes.getOrDefault(
                        node,
                        TODO("Cannot convert element")
                    )<NodeState> kotlin . collections . mutableSetOf < kotlin . Any >()).stream().anyMatch(
                        NodeState::isSelected
                    )
                );
                run {
                    selectedNodes.add(node)
                }
                run {
                    didFilter = true
                    node.onFilteredFromConflict()
                }
            }

            if (didFilter) {
                previousConflictedNodes.add(candidates)
                candidates = selectedNodes
                return currentConflict!!.withDifferentNodes(selectedNodes)
            }

            return currentConflict!!
        }

        /**
         * Removes candidates that are not selected. This is purely a
         * candidate-tracking operation. If a removed candidate was part of a
         * pending conflict, it is also removed from the pending conflict so that
         * [.updateClearAndReturnConflict] will not attempt to notify it.
         */
        fun removeDeselectedCandidates() {
            val it = candidates.iterator()
            while (it.hasNext()) {
                val next = it.next()
                if (!next.isSelected()) {
                    it.remove()
                    if (pendingConflict != null && pendingConflict.nodes.remove(next)) {
                        next.onFilteredFromConflict()
                    }
                }
            }
        }

        fun add(node: NodeState): Boolean {
            return candidates.add(node)
        }

        override fun iterator(): MutableIterator<NodeState> {
            return candidates.iterator()
        }

        fun hasPotentialConflict(): Boolean {
            return candidates.size > 1
        }

        val candidatesCopy: MutableSet<NodeState>
            get() = LinkedHashSet<NodeState>(candidates)

        /**
         * Creates a new conflict object and replace pre-existing conflict with it.
         *
         * If we saw the conflict before, record relationship between nodes
         *
         * @param candidatesForConflict the conflict candidates
         *
         * @return true if this is the first time this tracker sees this conflict and
         * if a conflict on this capability will need to be resolved.
         */
        fun createOrUpdateConflict(candidatesForConflict: MutableSet<NodeState>): Boolean {
            val newConflict = pendingConflict == null
            for (node in candidatesForConflict) {
                if (newConflict || !pendingConflict.nodes.contains(node)) {
                    node.markInCapabilityConflict()
                }
            }
            this.pendingConflict = DefaultCapabilitiesConflictHandler.CapabilityConflict(group, name, candidatesForConflict, previousConflictedNodes.contains(candidatesForConflict))
            return newConflict
        }
    }
}
