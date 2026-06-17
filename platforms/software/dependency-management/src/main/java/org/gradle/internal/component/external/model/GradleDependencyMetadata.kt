/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.internal.component.external.model

import com.google.common.base.Objects
import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.internal.component.local.model.DefaultProjectDependencyMetadata
import org.gradle.internal.component.model.DependencyMetadata
import org.gradle.internal.component.model.ExcludeMetadata
import org.gradle.internal.component.model.ForcingDependencyMetadata
import org.gradle.internal.component.model.IvyArtifactName

class GradleDependencyMetadata private constructor(
    val selector: ModuleComponentSelector,
    val excludes: ImmutableList<ExcludeMetadata?>,
    val isConstraint: Boolean,
    val isEndorsingStrictVersions: Boolean,
    val reason: String?,
    private val force: Boolean,
    val artifacts: ImmutableList<IvyArtifactName?>
) : ModuleDependencyMetadata, ForcingDependencyMetadata {
    private val hashCode: Int

    constructor(
        selector: ModuleComponentSelector,
        excludes: ImmutableList<ExcludeMetadata?>,
        constraint: Boolean,
        endorsing: Boolean,
        reason: String?,
        force: Boolean,
        artifact: IvyArtifactName?
    ) : this(selector, excludes, constraint, endorsing, reason, force, if (artifact == null) ImmutableList.of<IvyArtifactName?>() else ImmutableList.of<IvyArtifactName?>(artifact))

    init {
        this.hashCode = computeHashCode(
            selector,
            excludes,
            isConstraint,
            isEndorsingStrictVersions,
            reason,
            force,
            artifacts
        )
    }

    val dependencyArtifact: IvyArtifactName?
        get() = if (artifacts.isEmpty()) null else artifacts.get(0)

    override fun withRequestedVersion(requestedVersion: VersionConstraint): ModuleDependencyMetadata? {
        if (requestedVersion == selector.getVersionConstraint()) {
            return this
        }
        return GradleDependencyMetadata(
            DefaultModuleComponentSelector.Companion.newSelector(selector.getModuleIdentifier(), requestedVersion, selector.getAttributes(), selector.getCapabilitySelectors()), excludes,
            this.isConstraint,
            this.isEndorsingStrictVersions, reason, force, artifacts
        )
    }

    override fun withReason(reason: String?): ModuleDependencyMetadata? {
        if (Objects.equal(reason, this.reason)) {
            return this
        }
        return GradleDependencyMetadata(selector, excludes, this.isConstraint, this.isEndorsingStrictVersions, reason, force, artifacts)
    }

    override fun withEndorseStrictVersions(endorse: Boolean): ModuleDependencyMetadata? {
        if (endorse == this.isEndorsingStrictVersions) {
            return this
        }
        return GradleDependencyMetadata(selector, excludes, this.isConstraint, endorse, reason, force, artifacts)
    }

    override fun withTarget(target: ComponentSelector?): DependencyMetadata? {
        if (target is ModuleComponentSelector) {
            return GradleDependencyMetadata(
                target, excludes,
                this.isConstraint, this.isEndorsingStrictVersions, reason, force, artifacts
            )
        }
        return DefaultProjectDependencyMetadata((target as org.gradle.api.artifacts.component.ProjectComponentSelector?)!!, this)
    }

    override fun withTargetAndArtifacts(target: ComponentSelector?, artifacts: ImmutableList<IvyArtifactName?>): DependencyMetadata? {
        if (target is ModuleComponentSelector) {
            return GradleDependencyMetadata(
                target, excludes,
                this.isConstraint, this.isEndorsingStrictVersions, reason, force, artifacts
            )
        }
        return DefaultProjectDependencyMetadata((target as org.gradle.api.artifacts.component.ProjectComponentSelector?)!!, this.withArtifacts(artifacts))
    }

    private fun withArtifacts(artifacts: ImmutableList<IvyArtifactName?>): DependencyMetadata {
        return GradleDependencyMetadata(selector, excludes, this.isConstraint, this.isEndorsingStrictVersions, reason, force, artifacts)
    }

    val isChanging: Boolean
        get() = false

    val isTransitive: Boolean
        get() =// Constraints are _never_ transitive
            !this.isConstraint

    override fun toString(): String {
        return selector.toString()
    }

    override fun isForce(): Boolean {
        return force
    }

    override fun forced(): ForcingDependencyMetadata? {
        return GradleDependencyMetadata(selector, excludes, this.isConstraint, this.isEndorsingStrictVersions, reason, true, artifacts)
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val that = o as GradleDependencyMetadata
        return this.isConstraint == that.isConstraint && this.isEndorsingStrictVersions == that.isEndorsingStrictVersions && force == that.force &&
                Objects.equal(selector, that.selector) &&
                Objects.equal(excludes, that.excludes) &&
                Objects.equal(reason, that.reason) &&
                Objects.equal(artifacts, that.artifacts)
    }

    override fun hashCode(): Int {
        return hashCode
    }

    companion object {
        private fun computeHashCode(
            selector: ModuleComponentSelector,
            excludes: MutableList<ExcludeMetadata?>,
            constraint: Boolean,
            endorsing: Boolean,
            reason: String?,
            force: Boolean,
            artifacts: MutableList<IvyArtifactName?>
        ): Int {
            var result = selector.hashCode()
            result = 31 * result + excludes.hashCode()
            result = 31 * result + java.lang.Boolean.hashCode(constraint)
            result = 31 * result + java.lang.Boolean.hashCode(endorsing)
            result = 31 * result + (if (reason != null) reason.hashCode() else 0)
            result = 31 * result + java.lang.Boolean.hashCode(force)
            result = 31 * result + artifacts.hashCode()
            return result
        }
    }
}
