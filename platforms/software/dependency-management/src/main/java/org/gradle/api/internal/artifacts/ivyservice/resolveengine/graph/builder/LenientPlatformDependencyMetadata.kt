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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder

import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.internal.component.external.model.ModuleDependencyMetadata
import org.gradle.internal.component.model.DependencyMetadata
import org.gradle.internal.component.model.ExcludeMetadata
import org.gradle.internal.component.model.ForcingDependencyMetadata
import org.gradle.internal.component.model.IvyArtifactName

/**
 * Dependency metadata for edges involving a virtual platform. Used in two cases:
 *
 *  1. A hard edge from a node to its owning virtual platform.
 *  1. A constraint edge from a virtual platform to a node of a participating module.
 *
 */
internal class LenientPlatformDependencyMetadata(
    val selector: ModuleComponentSelector,
// just for reporting
    private val platformId: ComponentIdentifier,
    force: Boolean,
    constraint: Boolean
) : ModuleDependencyMetadata, ForcingDependencyMetadata {
    private val force: Boolean
    val isConstraint: Boolean

    init {
        this.platformId = platformId
        this.force = force
        this.isConstraint = constraint
    }

    override fun withRequestedVersion(requestedVersion: VersionConstraint): ModuleDependencyMetadata? {
        throw UnsupportedOperationException("Applying component metadata rules to lenient platform dependencies is not supported.")
    }

    override fun withReason(reason: String): ModuleDependencyMetadata? {
        throw UnsupportedOperationException("Applying component metadata rules to lenient platform dependencies is not supported.")
    }

    override fun withEndorseStrictVersions(endorse: Boolean): ModuleDependencyMetadata? {
        throw UnsupportedOperationException("Applying component metadata rules to lenient platform dependencies is not supported.")
    }

    val excludes: ImmutableList<ExcludeMetadata>
        get() = ImmutableList.of<ExcludeMetadata>()

    val artifacts: ImmutableList<IvyArtifactName>
        get() = ImmutableList.of<IvyArtifactName>()

    override fun withTarget(target: ComponentSelector): DependencyMetadata {
        // TODO: This gets called when performing substitutions.
        //       We probably shouldn't ignore this.
        return this
    }

    override fun withTargetAndArtifacts(target: ComponentSelector, artifacts: ImmutableList<IvyArtifactName>): DependencyMetadata {
        // TODO: This gets called when performing substitutions.
        //       We probably shouldn't ignore this.
        return this
    }

    val isChanging: Boolean
        get() = false

    val isTransitive: Boolean
        get() = !this.isConstraint

    val isEndorsingStrictVersions: Boolean
        get() = false

    val reason: String
        get() = "belongs to platform " + platformId

    override fun toString(): String {
        return selector.getDisplayName()
    }

    override fun isForce(): Boolean {
        return force
    }

    override fun forced(): ForcingDependencyMetadata {
        return LenientPlatformDependencyMetadata(selector, platformId, true, this.isConstraint)
    }
}
