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

/**
 * A node in the dependency graph. Represents a variant.
 */
interface DependencyGraphNode : ResolvedGraphVariant {
    val isRoot: Boolean

    val owner: DependencyGraphComponent?

    val incomingEdges: MutableCollection<out DependencyGraphEdge>?

    val outgoingEdges: MutableCollection<out DependencyGraphEdge>?

    /**
     * The outgoing file dependencies of this node. Should be modelled edges to another node, but are treated separately for now.
     */
    val outgoingFileEdges: MutableSet<out LocalFileDependencyMetadata>?

    val metadata: VariantGraphResolveMetadata?

    val isSelected: Boolean

    val component: ComponentResolutionState?
}
