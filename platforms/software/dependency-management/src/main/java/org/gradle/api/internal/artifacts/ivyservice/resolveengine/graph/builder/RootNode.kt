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

import com.google.common.collect.ImmutableList
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.RootGraphNode
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata
import org.gradle.internal.component.local.model.LocalVariantGraphResolveMetadata
import org.gradle.internal.component.local.model.LocalVariantGraphResolveState
import org.gradle.internal.component.model.DependencyMetadata
import org.gradle.internal.component.model.VariantGraphResolveState

internal class RootNode(
    resultId: Long,
    moduleRevision: ComponentState,
    resolveState: ResolveState,
    private val syntheticDependencies: MutableList<out DependencyMetadata>,
    root: VariantGraphResolveState
) : NodeState(resultId, moduleRevision, resolveState, root, false), RootGraphNode {
    private val resolveOptimizations: ResolveOptimizations

    init {
        this.resolveOptimizations = resolveState.getResolveOptimizations()
    }

    override fun isRoot(): Boolean {
        return true
    }

    override fun getOutgoingFileEdges(): MutableSet<out LocalFileDependencyMetadata> {
        return getResolveState().files!!
    }

    override fun addIncomingEdge(dependencyEdge: EdgeState) {
        throw InvalidUserCodeException(
            "Cannot select root node '" + getMetadata().getDisplayName() + "' as a variant. " +
                    "Configurations should not act as both a resolution root and a variant simultaneously. " +
                    "Be sure to mark configurations meant for resolution as canBeConsumed=false or use the 'resolvable(String)' configuration factory method to create them."
        )
    }

    override fun isSelected(): Boolean {
        return true
    }

    override fun getResolveState(): LocalVariantGraphResolveState {
        return super.getResolveState() as LocalVariantGraphResolveState
    }

    override fun getMetadata(): LocalVariantGraphResolveMetadata {
        return super.getMetadata() as LocalVariantGraphResolveMetadata
    }

    override fun getResolveOptimizations(): ResolveOptimizations {
        return resolveOptimizations
    }

    override fun getAllDependencies(): MutableList<out DependencyMetadata> {
        val superDependencies = super.getAllDependencies()
        if (syntheticDependencies.isEmpty()) {
            return superDependencies
        }
        val expectedSize = superDependencies.size + syntheticDependencies.size
        val allDependencies = ImmutableList.builderWithExpectedSize<DependencyMetadata>(expectedSize)
        allDependencies.addAll(superDependencies)
        allDependencies.addAll(syntheticDependencies)
        return allDependencies.build()
    }
}
