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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult

import com.google.common.collect.ImmutableSet
import org.gradle.api.artifacts.UnresolvedDependency
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.internal.DomainObjectContext
import org.gradle.api.internal.artifacts.ComponentSelectorConverter
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector
import org.gradle.api.internal.artifacts.ivyservice.DefaultUnresolvedDependency
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphEdge
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphPathResolver
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.RootGraphNode
import org.gradle.internal.resolve.ModuleVersionResolveException

class ResolutionFailureCollector(
    componentSelectorConverter: ComponentSelectorConverter,
    owner: DomainObjectContext
) : DependencyGraphVisitor {
    private val componentSelectorConverter: ComponentSelectorConverter
    private val owner: DomainObjectContext

    private val failuresByRevisionId: MutableMap<ComponentSelector, BrokenDependency> = LinkedHashMap<ComponentSelector, BrokenDependency>()
    private var root: RootGraphNode? = null

    init {
        this.componentSelectorConverter = componentSelectorConverter
        this.owner = owner
    }

    override fun start(root: RootGraphNode) {
        this.root = root
    }

    override fun visitNode(node: DependencyGraphNode) {
        for (dependency in node.getOutgoingEdges()) {
            val failure = dependency.getFailure()
            if (failure != null) {
                addUnresolvedDependency(dependency, dependency.getRequested(), failure)
            }
        }
    }

    fun complete(extraFailures: MutableSet<UnresolvedDependency>): MutableSet<UnresolvedDependency> {
        if (extraFailures.isEmpty() && failuresByRevisionId.isEmpty()) {
            return ImmutableSet.of<UnresolvedDependency>()
        }

        val builder = ImmutableSet.builder<UnresolvedDependency>()
        builder.addAll(extraFailures)
        for (entry in failuresByRevisionId.entries) {
            val paths = DependencyGraphPathResolver.calculatePaths(entry.value.requiredBy, root!!, owner)

            val key = entry.key
            val moduleVersionId = componentSelectorConverter.getModuleVersionId(key)
            val selector = DefaultModuleVersionSelector.newSelector(moduleVersionId)
            builder.add(DefaultUnresolvedDependency(selector, entry.value.failure.withIncomingPaths(paths)))
        }
        return builder.build()
    }

    private fun addUnresolvedDependency(dependency: DependencyGraphEdge, selector: ComponentSelector, failure: ModuleVersionResolveException) {
        var breakage = failuresByRevisionId.get(selector)
        if (breakage == null) {
            breakage = BrokenDependency(failure)
            failuresByRevisionId.put(selector, breakage)
        }
        breakage.requiredBy.add(dependency.getFrom())
    }

    private class BrokenDependency(failure: ModuleVersionResolveException) {
        val failure: ModuleVersionResolveException
        val requiredBy: MutableList<DependencyGraphNode> = ArrayList<DependencyGraphNode>()

        init {
            this.failure = failure
        }
    }
}
