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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.selectors

import org.gradle.api.artifacts.component.ProjectComponentSelector
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector
import org.gradle.internal.resolve.result.ComponentIdResolveResult

interface ResolvableSelectorState {
    /**
     * The raw component selector being resolved, after any substitution.
     */
    val selector: ComponentSelector?

    /**
     * The version constraint that applies to this selector, if any.
     * Will return null for a project selector.
     */
    val versionConstraint: ResolvedVersionConstraint?

    /**
     * Resolve the selector to a component identifier.
     */
    fun resolve(allRejects: VersionSelector?): ComponentIdResolveResult?

    /**
     * Resolve the prefer constraint of the selector to a component identifier.
     */
    fun resolvePrefer(allRejects: VersionSelector?): ComponentIdResolveResult?

    /**
     * Mark the selector as resolved.
     * This is used when another selector resolved to a component identifier that satisfies this selector.
     * In that case, a call to [.resolve] is not required.
     */
    fun markResolved()

    val isForce: Boolean

    val isSoftForce: Boolean

    val isFromLock: Boolean

    fun hasStrongOpinion(): Boolean

    val firstDependencyArtifact: IvyArtifactName?

    val isChanging: Boolean

    val isProject: Boolean
        get() = this.selector is ProjectComponentSelector
}
