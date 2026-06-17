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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder

import com.google.common.collect.ImmutableList
import com.google.common.collect.Iterables
import com.google.common.collect.Lists
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphEdge
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode
import java.util.stream.Collectors
import java.util.stream.Stream

object MessageBuilderHelper {
    fun formattedPathsTo(edge: DependencyGraphEdge): MutableList<String> {
        return findPathsTo(edge).stream().map<String> { path: MutableList<DependencyGraphEdge>? ->
            val header = if (Iterables.getLast<DependencyGraphEdge>(path!!).getDependencyMetadata().isConstraint) "Constraint" else "Dependency"
            val formattedPath = MessageBuilderHelper.streamNodeNames(path)
                .collect(Collectors.joining(" --> "))
            header + " path: " + formattedPath
        }.collect(Collectors.toList())
    }

    fun findPathNamesTo(edge: DependencyGraphEdge): ImmutableList<ImmutableList<String>> {
        return findPathsTo(edge).stream()
            .map<ImmutableList<String>> { p: MutableList<DependencyGraphEdge>? -> MessageBuilderHelper.streamNodeNames(p!!).collect(ImmutableList.toImmutableList<String>()) }
            .collect(ImmutableList.toImmutableList<ImmutableList<String>>())
    }

    fun findPathsTo(edge: DependencyGraphEdge): MutableList<MutableList<DependencyGraphEdge>> {
        val acc: MutableList<MutableList<DependencyGraphEdge>> = ArrayList<MutableList<DependencyGraphEdge>>(1)
        pathTo(edge, ArrayList<DependencyGraphEdge>(), acc, HashSet<DependencyGraphNode>())
        return acc
    }

    private fun pathTo(
        edge: DependencyGraphEdge,
        currentPath: MutableList<DependencyGraphEdge>,
        accumulator: MutableList<MutableList<DependencyGraphEdge>>,
        alreadySeen: MutableSet<DependencyGraphNode>
    ) {
        val from = edge.getFrom()
        if (alreadySeen.add(from)) {
            currentPath.add(edge)

            val incomingEdges = from.getIncomingEdges()
            if (!incomingEdges.isEmpty()) {
                for (dependent in incomingEdges) {
                    val otherPath: MutableList<DependencyGraphEdge> = ArrayList<DependencyGraphEdge>(currentPath)
                    pathTo(dependent, otherPath, accumulator, alreadySeen)
                }
            } else {
                // We've hit the root of the path
                accumulator.add(Lists.reverse<DependencyGraphEdge>(currentPath))
            }
        }
    }

    private fun streamNodeNames(path: MutableList<DependencyGraphEdge>): Stream<String> {
        return path.stream().map<String> { edge: DependencyGraphEdge? -> edge!!.getFrom().getDisplayName() }
    }
}
