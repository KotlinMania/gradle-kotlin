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

import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolutionResult
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedVariantResult
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.internal.artifacts.resolver.ResolutionAccess
import org.gradle.api.internal.attributes.AttributeDesugaring
import org.gradle.api.internal.provider.DefaultProvider
import org.gradle.api.provider.Provider
import org.gradle.internal.Actions
import org.gradle.util.internal.ConfigureUtil
import java.util.concurrent.Callable

class DefaultResolutionResult(
    private val resolutionAccess: ResolutionAccess,
    private val attributeDesugaring: AttributeDesugaring
) : ResolutionResult {
    override fun getRoot(): ResolvedComponentResult {
        return getRootComponent().get()
    }

    override fun getRootComponent(): Provider<ResolvedComponentResult> {
        return DefaultProvider<ResolvedComponentResult>(Callable {
            val graph = this.graph
            val nodes = graph.structure().nodes()
            graph.getComponent(nodes.owner(nodes.root()))
        })
    }

    override fun getRootVariant(): Provider<ResolvedVariantResult> {
        return DefaultProvider<ResolvedVariantResult>(Callable {
            val graph = this.graph
            graph.getVariant(graph.structure().nodes().root())
        })
    }

    private val graph: ResolvedGraphResult
        get() = resolutionAccess.results.getValue().visitedGraph.getResolvedGraphResultSource().get()

    override fun getRequestedAttributes(): AttributeContainer {
        return attributeDesugaring.desugar(resolutionAccess.attributes)
    }

    // TODO: The below methods should operate directly on a GraphStructure
    // to avoid traversing the graph.
    override fun getAllDependencies(): MutableSet<out DependencyResult> {
        val out: MutableSet<DependencyResult> = LinkedHashSet<DependencyResult>()
        allDependencies(Action { e: DependencyResult -> out.add(e) })
        return out
    }

    override fun allDependencies(action: Action<in DependencyResult>) {
        DefaultResolvedComponentResult.Companion.eachElement(getRoot(), Actions.doNothing<ResolvedComponentResult>(), action, HashSet<ResolvedComponentResult>())
    }

    override fun allDependencies(closure: Closure<*>) {
        allDependencies(ConfigureUtil.configureUsing<DependencyResult>(closure))
    }

    override fun getAllComponents(): MutableSet<ResolvedComponentResult> {
        val out: MutableSet<ResolvedComponentResult> = LinkedHashSet<ResolvedComponentResult>()
        DefaultResolvedComponentResult.Companion.eachElement(getRoot(), Actions.doNothing<ResolvedComponentResult>(), Actions.doNothing<DependencyResult>(), out)
        return out
    }

    override fun allComponents(action: Action<in ResolvedComponentResult>) {
        DefaultResolvedComponentResult.Companion.eachElement(getRoot(), action, Actions.doNothing<DependencyResult>(), HashSet<ResolvedComponentResult>())
    }

    override fun allComponents(closure: Closure<*>) {
        allComponents(ConfigureUtil.configureUsing<ResolvedComponentResult>(closure))
    }

    override fun equals(o: Any): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val that = o as DefaultResolutionResult
        return resolutionAccess == that.resolutionAccess
    }

    override fun hashCode(): Int {
        return resolutionAccess.hashCode()
    }
}
