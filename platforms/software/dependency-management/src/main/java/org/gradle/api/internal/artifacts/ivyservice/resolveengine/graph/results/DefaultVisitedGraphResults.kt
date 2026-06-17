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

import org.gradle.api.artifacts.UnresolvedDependency
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.GraphStructure
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ResolvedDependencyGraph
import org.gradle.api.internal.artifacts.result.ResolvedGraphResult
import org.gradle.api.internal.attributes.ImmutableAttributes
import java.lang.ref.SoftReference
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Consumer
import java.util.function.Supplier

/**
 * Default implementation of [VisitedGraphResults].
 */
class DefaultVisitedGraphResults(
    resolvedDependencyGraph: ResolvedDependencyGraph,
    unresolvedDependencies: MutableSet<UnresolvedDependency>
) : VisitedGraphResults {
    private val requestAttributes: ImmutableAttributes
    private val graphStructureSource: Supplier<GraphStructure>
    private val unresolvedDependencies: MutableSet<UnresolvedDependency>

    /**
     * ResolvedGraphResult is a wrapper over the underlying GraphStructure and provides
     * no additional context. Only hold a soft reference to it to avoid retained memory
     * if no other references to the wrapper graph exist.
     */
    private val lock: Lock = ReentrantLock()
    private var resolvedGraphResult: SoftReference<ResolvedGraphResult>? = null
    private val resolvedGraphResultSource: Supplier<ResolvedGraphResult>

    init {
        this.requestAttributes = resolvedDependencyGraph.requestAttributes
        this.graphStructureSource = resolvedDependencyGraph.graphSource
        this.unresolvedDependencies = unresolvedDependencies

        this.resolvedGraphResultSource = Supplier {
            lock.lock()
            try {
                if (resolvedGraphResult != null) {
                    val value = resolvedGraphResult!!.get()
                    if (value != null) {
                        return@Supplier value
                    }
                }

                val value = ResolvedGraphResult(
                    graphStructureSource.get(),
                    resolvedDependencyGraph.availableVariantsByComponent
                )
                this.resolvedGraphResult = SoftReference<ResolvedGraphResult>(value)
                return@Supplier value
            } finally {
                lock.unlock()
            }
        }
    }

    override fun getRequestedAttributes(): ImmutableAttributes {
        return requestAttributes
    }

    override fun hasAnyFailure(): Boolean {
        return !unresolvedDependencies.isEmpty()
    }

    override fun visitFailures(visitor: Consumer<Throwable>) {
        for (unresolvedDependency in unresolvedDependencies) {
            visitor.accept(unresolvedDependency.getProblem())
        }
    }

    override fun getUnresolvedDependencies(): MutableSet<UnresolvedDependency> {
        return unresolvedDependencies
    }

    override fun getGraphStructureSource(): Supplier<GraphStructure> {
        return graphStructureSource
    }

    override fun getResolvedGraphResultSource(): Supplier<ResolvedGraphResult> {
        return resolvedGraphResultSource
    }
}
