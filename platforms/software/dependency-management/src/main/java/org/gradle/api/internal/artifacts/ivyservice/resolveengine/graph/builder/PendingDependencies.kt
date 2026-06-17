/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder

import org.gradle.api.artifacts.ModuleIdentifier

/**
 * Tracks hard (non-constraint) dependencies targeting a given module. A module should end up in a graph if it has
 * hard dependencies. Also tracks all constraints that have been observed for a module. These constraints should be
 * activated when the hard edge count becomes positive.
 */
class PendingDependencies internal constructor(private val moduleIdentifier: ModuleIdentifier) {
    private val constraintProvidingNodes: MutableSet<NodeState>
    private var hardEdges = 0

    init {
        this.constraintProvidingNodes = LinkedHashSet<NodeState>()
    }

    fun addIncomingHardEdge(): Boolean {
        increaseHardEdgeCount()
        if (!hasConstraintProviders()) {
            return false
        }

        assert(hardEdges == 1)

        for (node in constraintProvidingNodes) {
            node.prepareForConstraintNoLongerPending(moduleIdentifier)
        }
        constraintProvidingNodes.clear()

        return true
    }

    fun registerConstraintProvider(nodeState: NodeState) {
        check(hardEdges == 0) { "Cannot add a pending node for a dependency which is not pending" }
        constraintProvidingNodes.add(nodeState)
    }

    fun unregisterConstraintProvider(nodeState: NodeState) {
        check(hardEdges == 0) { "Cannot remove a pending node for a dependency which is not pending" }
        constraintProvidingNodes.remove(nodeState)
    }

    val isPending: Boolean
        /**
         * Return true iff all nodes in this module have no non-constraint edges
         */
        get() = hardEdges == 0

    fun hasConstraintProviders(): Boolean {
        return !constraintProvidingNodes.isEmpty()
    }

    fun increaseHardEdgeCount() {
        hardEdges++
    }

    fun decreaseHardEdgeCount() {
        assert(hardEdges > 0) { "Cannot remove a hard edge when none recorded" }
        hardEdges--
    }

    fun retarget(pendingDependencies: PendingDependencies) {
        hardEdges += pendingDependencies.hardEdges
    }
}
