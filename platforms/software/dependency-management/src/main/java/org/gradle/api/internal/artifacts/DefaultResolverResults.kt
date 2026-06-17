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
package org.gradle.api.internal.artifacts

import org.gradle.api.artifacts.ResolvedConfiguration
import org.gradle.api.internal.artifacts.ResolverResults.LegacyResolverResults
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedArtifactSet
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.results.VisitedGraphResults

/**
 * Default implementation of [ResolverResults].
 */
class DefaultResolverResults(
    val visitedGraph: VisitedGraphResults?,
    val visitedArtifacts: VisitedArtifactSet?,
    val legacyResults: ResolverResults.LegacyResolverResults?,
    val isFullyResolved: Boolean
) : ResolverResults {
    /**
     * Default implementation of [LegacyResolverResults].
     */
    class DefaultLegacyResolverResults private constructor(private val configuration: ResolvedConfiguration?) : ResolverResults.LegacyResolverResults {
        val resolvedConfiguration: ResolvedConfiguration
            get() {
                checkNotNull(configuration) { "Cannot get resolved configuration when only build dependencies are resolved." }

                return configuration
            }

        companion object {
            /**
             * Create a new legacy result representing the result of resolving build dependencies.
             */
            fun buildDependenciesResolved(): ResolverResults.LegacyResolverResults {
                return DefaultLegacyResolverResults(null)
            }

            /**
             * Create a new legacy result representing the result of resolving the dependency graph.
             */
            fun graphResolved(configuration: ResolvedConfiguration?): ResolverResults.LegacyResolverResults {
                return DefaultLegacyResolverResults(configuration)
            }
        }
    }

    companion object {
        /**
         * Create a new result representing the result of resolving build dependencies.
         */
        fun buildDependenciesResolved(
            graphResults: VisitedGraphResults?,
            visitedArtifacts: VisitedArtifactSet?,
            legacyResolverResults: ResolverResults.LegacyResolverResults?
        ): ResolverResults {
            return DefaultResolverResults(
                graphResults,
                visitedArtifacts,
                legacyResolverResults,
                false
            )
        }

        /**
         * Create a new result representing the result of resolving the dependency graph.
         */
        fun graphResolved(
            graphResults: VisitedGraphResults?,
            visitedArtifacts: VisitedArtifactSet?,
            legacyResolverResults: ResolverResults.LegacyResolverResults?
        ): ResolverResults {
            return DefaultResolverResults(
                graphResults,
                visitedArtifacts,
                legacyResolverResults,
                true
            )
        }
    }
}
