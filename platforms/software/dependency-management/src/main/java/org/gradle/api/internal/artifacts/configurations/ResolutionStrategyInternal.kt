/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.configurations

import org.gradle.api.artifacts.ResolutionStrategy
import org.gradle.api.internal.artifacts.ComponentSelectionRulesInternal
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionsInternal

interface ResolutionStrategyInternal : ResolutionStrategy {
    /**
     * Discard any configuration state that is not required after graph resolution has been attempted.
     */
    fun maybeDiscardStateRequiredForGraphResolution()

    /**
     * Sets whether or not any configuration resolution is final and the state required for resolution can be
     * discarded.  Setting this to true implies that the configuration may be re-resolved again in the future.
     *
     * Defaults to false.
     */
    fun setKeepStateRequiredForGraphResolution(keepStateRequiredForGraphResolution: Boolean)

    /**
     * Gets the current expiry policy for dynamic revisions.
     *
     * @return the expiry policy
     */
    val cachePolicy: CachePolicy?

    /**
     * Until the feature 'settles' and we receive more feedback, it's internal
     *
     * @return conflict resolution
     */
    val conflictResolution: ConflictResolution?

    /**
     * @return the dependency substitution rule (may aggregate multiple rules)
     */
    val dependencySubstitutionRule: ImmutableActionSet<DependencySubstitutionInternal>?

    /**
     * Used by tests to validate behaviour of the 'task graph modified' state
     */
    fun assumeFluidDependencies()

    /**
     * Should the configuration be fully resolved to determine the task dependencies?
     * If not, we do a shallow 'resolve' of SelfResolvingDependencies only.
     */
    fun resolveGraphToDetermineTaskDependencies(): Boolean

    val sortOrder: ResolutionStrategy.SortOrder?

    override fun getDependencySubstitution(): DependencySubstitutionsInternal?

    /**
     * @return the version selection rules object
     */
    override fun getComponentSelection(): ComponentSelectionRulesInternal?

    /**
     * @return copy of this resolution strategy. See the contract of [org.gradle.api.artifacts.Configuration.copy].
     */
    fun copy(): ResolutionStrategyInternal?

    /**
     * Sets the validator to invoke before mutation. Any exception thrown by the action will veto the mutation.
     */
    fun setMutationValidator(action: MutationValidator)

    /**
     * Returns the dependency locking provider linked to this resolution strategy.
     *
     * @return dependency locking provider
     */
    val dependencyLockingProvider: DependencyLockingProvider?

    /**
     * Indicates if dependency locking is enabled.
     *
     * @return `true` if dependency locking is enabled, `false` otherwise
     */
    val isDependencyLockingEnabled: Boolean

    val capabilitiesResolutionRules: CapabilitiesResolutionInternal?

    val isFailingOnDynamicVersions: Boolean

    val isFailingOnChangingVersions: Boolean

    val isDependencyVerificationEnabled: Boolean

    @JvmField
    var includeAllSelectableVariantResults: Boolean
}
