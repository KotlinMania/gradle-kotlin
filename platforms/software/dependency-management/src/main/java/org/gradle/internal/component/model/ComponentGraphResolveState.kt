/*
 * Copyright 2022 the original author or authors.
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
package org.gradle.internal.component.model

import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.capabilities.ImmutableCapability

/**
 * State for a component instance (e.g. version of a component) that is used to perform dependency graph resolution and that is independent of the particular
 * graph being resolved.
 *
 *
 * Resolution happens in multiple steps. The first step is to calculate the dependency graph, which involves selecting component instances and one or more variants of each instance.
 * This type exposes only the information and operations required to do this. In particular, it does not expose any information about artifacts unless this is actually required for graph resolution,
 * which only happens in certain specific cases (and something we should deprecate).
 *
 *
 * The subsequent resolution steps to select artifacts, are performed using the instance returned by [.prepareForArtifactResolution].
 *
 *
 * Instances of this type are obtained via the methods of [org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver] or [org.gradle.internal.resolve.resolver.ComponentMetaDataResolver].
 *
 *
 * This interface says nothing about thread safety, however some subtypes may be required to be thread safe.
 *
 * @see ComponentGraphSpecificResolveState for dependency graph specific state for the component.
 */
interface ComponentGraphResolveState {
    /**
     * A unique id for this component within the current build tree. Note that this id is not stable across Gradle invocations.
     */
    fun getInstanceId(): Long

    /**
     * The component identifier for this component. This identifier is stable but may not be unique.
     */
    fun getId(): ComponentIdentifier?

    /**
     * The immutable metadata for this component.
     */
    fun getMetadata(): ComponentGraphResolveMetadata?

    /**
     * Returns the candidates for variant selection during graph resolution.
     */
    fun getCandidatesForGraphVariantSelection(): GraphSelectionCandidates?

    /**
     * Does this instance represent some temporary or mutated view of the component?
     *
     * Generally you should avoid retaining ad hoc instances in memory, whereas non-ad hoc instances can safely be cached for the lifetime of the build tree.
     *
     * @return true when this instance is ad hoc, false when this instance is not ad hoc and can be cached.
     */
    fun isAdHoc(): Boolean

    /**
     * Creates the state that can be used for artifact resolution for this component instance.
     *
     *
     * Note that this may be expensive, and should be used only when required.
     */
    fun prepareForArtifactResolution(): ComponentArtifactResolveState?

    /**
     * Returns the default capability for this component.
     *
     * @return default capability for this component.
     */
    fun getDefaultCapability(): ImmutableCapability?
}
