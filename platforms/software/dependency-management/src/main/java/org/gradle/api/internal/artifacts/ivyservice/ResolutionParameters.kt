/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice

import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.ResolutionStrategy
import org.gradle.api.internal.artifacts.configurations.ConflictResolution
import org.gradle.api.internal.artifacts.configurations.ResolutionHost
import org.gradle.api.internal.artifacts.dsl.ImmutableModuleReplacements
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.Conflict
import org.gradle.api.internal.attributes.immutable.artifact.ImmutableArtifactTypeRegistry
import org.gradle.internal.component.local.model.LocalComponentGraphResolveState
import org.gradle.internal.component.local.model.LocalVariantGraphResolveState
import org.gradle.operations.dependencies.configurations.ConfigurationIdentity

/**
 * Thread safe description of what and how to resolve. This type is almost entirely deeply immutable,
 * except for actions which must run user code. Actions which run user code or actions that compute
 * state lazily must be thread safe. These actions are assumed to be effectively immutable and must
 * be deterministic. All user-code must be guarded with the proper project locks. Properties exposed
 * by these parameters should assume they will be interacted with without project locks being
 * acquired beforehand.
 *
 *
 * These parameters contain almost all information required to perform a resolution, except for the
 * data still present in [LegacyResolutionParameters]. The non-migrated
 * data do not yet satisfy the immutability and thread safety requirements of this type, and therefore
 * have not yet been migrated to this class.
 */
class ResolutionParameters(
    /**
     * The "Host" or owner of the resolution.
     */
    @JvmField val resolutionHost: ResolutionHost,
    /**
     * The root component of the graph. Owns the root variant.
     */
    val rootComponent: LocalComponentGraphResolveState,
    /**
     * The root variant of the graph, specifying the declared dependencies and requested attributes.
     */
    val rootVariant: LocalVariantGraphResolveState,
    /**
     * Version locks to apply to specific modules during resolution.
     *
     *
     * Enforces that the resolution will choose specific versions for the given modules,
     * to be used to enforce consistent versions across different resolutions.
     */
    val moduleVersionLocks: ImmutableList<ModuleVersionLock>,
    /**
     * The default sort ordering of artifacts. May be overridden during artifact selection.
     */
    val defaultSortOrder: ResolutionStrategy.SortOrder,
    /**
     * The identity of the source of this resolution, if it is a Configuration.
     *
     *
     * Used by artifact transforms to identify the source configuration in build operations.
     * May be null if the resolution is not associated with a configuration.
     */
    @JvmField val configurationIdentity: ConfigurationIdentity?,
    /**
     * Specifies transformations applied to the attributes of producer artifact sets before
     * artifact selection is performed on them. These transformations are based on the
     * artifacts exposed the artifact sets being selected.
     *
     *
     * TODO #31538: This should go away. Observing the actual artifacts of an artifact set during
     * selection must be avoided, as we perform artifact selection during build dependency
     * resolution, which occurs before the actual artifacts are known.
     */
    val artifactTypeRegistry: ImmutableArtifactTypeRegistry,
    /**
     * Specifies the module replacements to apply during resolution.
     */
    val moduleReplacements: ImmutableModuleReplacements,
    /**
     * Specifies how module conflicts are resolved.
     */
    val moduleConflictResolutionStrategy: ConflictResolution,
    /**
     * Identifies this resolution within a lockfile.
     */
    val dependencyLockingId: String,
    /**
     * True if dependency locking is enabled, false otherwise.
     */
    val isDependencyLockingEnabled: Boolean,
    /**
     * True if all selectable variants should be included in the output
     * [org.gradle.api.artifacts.result.ResolutionResult]. This should generally
     * be false except for reporting use cases.
     *
     *
     * TODO: The reporting use case of this parameter may be better suited by a
     * separate "metadata fetching" API, allowing component metadata to be retrieved
     * without performing graph resolution.
     */
    val includeAllSelectableVariantResults: Boolean,
    /**
     * True if dependency verification is enabled, false otherwise.
     */
    val isDependencyVerificationEnabled: Boolean,
    /**
     * True if resolution should fail if dependencies with dynamic versions are present in the graph, false otherwise.
     */
    val isFailingOnDynamicVersions: Boolean,
    /**
     * True if resolution should fail if dependencies with changing versions are present in the graph, false otherwise.
     */
    val isFailingOnChangingVersions: Boolean,
    /**
     * Details about this resolution to provide additional context during failure cases.
     */
    val failureResolutions: FailureResolutions,
    /**
     * Controls the caching behavior for external dependencies.
     */
    val cacheExpirationControl: CacheExpirationControl
) {
    class ModuleVersionLock(
        /**
         * The module to lock.
         */
        val moduleId: ModuleIdentifier,
        /**
         * The version to enforce.
         */
        val version: String,
        /**
         * Why this version is enforced.
         */
        val reason: String,
        /**
         * Whether the version be enforced as a strict constraint.
         */
        val isStrict: Boolean
    )

    /**
     * Details about this resolution to provide additional context during failure cases.
     */
    interface FailureResolutions {
        /**
         * Provide resolutions to add to a failure to assist the user on resolving the provided
         * version conflict.
         */
        fun forVersionConflict(conflict: Conflict): MutableList<String>?
    }
}
