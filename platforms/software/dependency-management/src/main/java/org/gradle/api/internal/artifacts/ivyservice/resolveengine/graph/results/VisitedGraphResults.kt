/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.results

import java.util.function.Consumer

/**
 * Models the result of resolving dependency graph. Provides access to the root
 * of the dependency graph, as well as access to any failures that occurred while
 * building the graph.
 */
interface VisitedGraphResults {
    /**
     * The attributes that were requested for the root of the dependency graph.
     */
    val requestedAttributes: ImmutableAttributes?

    /**
     * Returns true if any failures occurred while building these results.
     */
    fun hasAnyFailure(): Boolean

    /**
     * Visits all failures that occurred while resolving the graph.
     *
     *
     * No failures are visited if [.hasAnyFailure] is false
     */
    fun visitFailures(visitor: Consumer<Throwable>)

    /**
     * Returns all failures to resolve a dependency.
     * These failures are also accessible via the resolution result.
     */
    val unresolvedDependencies: MutableSet<UnresolvedDependency>?

    /**
     * Get the raw structure of the resolved graph, without mapping to any
     * public API. This is the most efficient way to obtain a rich
     * representation of a dependency graph.
     */
    val graphStructureSource: Supplier<GraphStructure>?

    /**
     * Returns the public view of the resolved graph.
     */
    val resolvedGraphResultSource: Supplier<ResolvedGraphResult>?
}
