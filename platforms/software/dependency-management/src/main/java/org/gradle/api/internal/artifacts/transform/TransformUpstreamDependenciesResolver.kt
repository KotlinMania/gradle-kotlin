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
package org.gradle.api.internal.artifacts.transform

import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedArtifactSet

/**
 * Companion type to [TransformStepNode] that knows how to compute extra dependent nodes
 * aside from the to be transformed artifact.
 */
interface TransformUpstreamDependenciesResolver {
    /**
     * Returns the dependencies that should be applied to the given transform step for an artifact
     * sourced from a component with the given identifier.
     */
    fun dependenciesFor(componentId: ComponentIdentifier, transformStep: TransformStep): TransformUpstreamDependencies?

    interface Factory {
        /**
         * Create a new instance of the resolver.
         *
         * @param visitedArtifacts The artifact set is used to resolve the dependencies of the transform steps.
         */
        fun create(visitedArtifacts: VisitedArtifactSet): TransformUpstreamDependenciesResolver?
    }

    companion object {
        /**
         * A resolver that always returns empty transform dependencies.
         */
        @JvmField
        val NO_DEPENDENCIES: TransformUpstreamDependenciesResolver =
            org.gradle.api.internal.artifacts.transform.TransformUpstreamDependenciesResolver { componentId: ComponentIdentifier, transformStep: TransformStep -> DefaultTransformUpstreamDependenciesResolver.Companion.NO_DEPENDENCIES }
    }
}
