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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph

import org.gradle.api.Describable
import org.gradle.api.internal.DomainObjectContext
import org.gradle.internal.Describables
import java.util.ArrayDeque
import java.util.Deque

object DependencyGraphPathResolver {
    fun calculatePaths(
        fromNodes: MutableList<DependencyGraphNode>,
        toNode: DependencyGraphNode,
        owner: DomainObjectContext
    ): MutableCollection<MutableList<Describable>> {
        // Compute the shortest path from each component that has a direct dependency on the broken
        // dependency back to the root component. Each search is an independent BFS in the reverse
        // ("dependents") direction, which is robust against cycles in the resolved graph (e.g.
        // mutually-dependent KMP variants like room-runtime <-> room-common-jvm).

        val root = toNode.getOwner()
        val rootDescribable: Describable = Describables.of(owner.getDisplayName())

        val directDependees: MutableSet<DependencyGraphComponent> = LinkedHashSet<DependencyGraphComponent>()
        for (node in fromNodes) {
            directDependees.add(node.getOwner())
        }

        val paths: MutableList<MutableList<Describable>> = ArrayList<MutableList<Describable>>()
        for (dependee in directDependees) {
            val path = shortestPathToRoot(dependee, root, rootDescribable)
            if (path != null) {
                paths.add(path)
            }
        }
        return paths
    }

    private fun shortestPathToRoot(
        start: DependencyGraphComponent,
        root: DependencyGraphComponent,
        rootDescribable: Describable
    ): MutableList<Describable>? {
        if (start === root) {
            val single: MutableList<Describable> = ArrayList<Describable>(1)
            single.add(rootDescribable)
            return single
        }
        val predecessor: MutableMap<DependencyGraphComponent, DependencyGraphComponent> = HashMap<DependencyGraphComponent, DependencyGraphComponent>()
        predecessor.put(start, null)
        val queue: Deque<DependencyGraphComponent> = ArrayDeque<DependencyGraphComponent>()
        queue.add(start)
        var reachedRoot = false
        while (!queue.isEmpty()) {
            val current = queue.removeFirst()
            if (current === root) {
                reachedRoot = true
                break
            }
            // TODO: this walks every dependents edge including constraint edges; constraints
            //  don't "pull in" a component, so paths that traverse them aren't useful to users.
            //  Restricting to non-constraint edges would shift many existing test expectations,
            //  so it is deferred.
            for (next in current.getDependents()) {
                if (!predecessor.containsKey(next)) {
                    predecessor.put(next, current)
                    queue.addLast(next)
                }
            }
        }
        if (!reachedRoot) {
            return null
        }

        // Walk predecessors from root back to start; this naturally produces [root, ..., start].
        val chain: MutableList<DependencyGraphComponent> = ArrayList<DependencyGraphComponent>()
        var walk: DependencyGraphComponent? = root
        while (walk != null) {
            chain.add(walk)
            walk = predecessor.get(walk)
        }

        // Output format expected by ModuleVersionResolveException#getMessage:
        // [<rootDisplayName>, <intermediate componentIds...>, <directDependee componentId>].
        // Substitute rootDescribable for root's componentId, then emit the rest in order.
        val result: MutableList<Describable> = ArrayList<Describable>(chain.size)
        result.add(rootDescribable)
        for (i in 1..<chain.size) {
            result.add(Describables.of(chain.get(i).getComponentId().getDisplayName()))
        }
        return result
    }
}
