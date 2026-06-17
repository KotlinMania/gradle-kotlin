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

import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.component.ProjectComponentSelector
import org.gradle.internal.component.local.model.DefaultProjectDependencyMetadata
import org.gradle.internal.component.model.DependencyMetadata
import org.gradle.internal.component.model.IvyArtifactName

/**
 * A [ModuleDependencyMetadata] implementation that is backed by an [ExternalDependencyDescriptor].
 */
abstract class ExternalModuleDependencyMetadata(@JvmField val reason: String?, val isEndorsingStrictVersions: Boolean, @JvmField val artifacts: ImmutableList<IvyArtifactName?>?) : ModuleDependencyMetadata {
    abstract val dependencyDescriptor: ExternalDependencyDescriptor?

    abstract val excludes: ImmutableList<ExcludeMetadata?>?

    override fun withTarget(target: ComponentSelector?): DependencyMetadata? {
        if (target is ModuleComponentSelector) {
            val moduleTarget = target
            val newSelector: ModuleComponentSelector = DefaultModuleComponentSelector.Companion.newSelector(
                moduleTarget.getModuleIdentifier(),
                moduleTarget.getVersionConstraint(),
                moduleTarget.getAttributes(),
                moduleTarget.getCapabilitySelectors()
            )
            if (newSelector == selector) {
                return this
            }
            return withRequested(newSelector)
        } else if (target is ProjectComponentSelector) {
            val projectTarget = target
            return DefaultProjectDependencyMetadata(projectTarget, this)
        } else {
            throw IllegalArgumentException("Unexpected selector provided: " + target)
        }
    }

    override fun withTargetAndArtifacts(target: ComponentSelector?, artifacts: ImmutableList<IvyArtifactName?>?): DependencyMetadata? {
        if (target is ModuleComponentSelector) {
            val moduleTarget = target
            val newSelector: ModuleComponentSelector = DefaultModuleComponentSelector.Companion.newSelector(
                moduleTarget.getModuleIdentifier(),
                moduleTarget.getVersionConstraint(),
                moduleTarget.getAttributes(),
                moduleTarget.getCapabilitySelectors()
            )
            if (newSelector == selector && this.artifacts == artifacts) {
                return this
            }
            return withRequestedAndArtifacts(newSelector, artifacts)
        } else if (target is ProjectComponentSelector) {
            val projectTarget = target
            return DefaultProjectDependencyMetadata(projectTarget, this.withArtifacts(artifacts)!!)
        } else {
            throw IllegalArgumentException("Unexpected selector provided: " + target)
        }
    }

    override fun withRequestedVersion(requestedVersion: VersionConstraint): ModuleDependencyMetadata? {
        val selector: ModuleComponentSelector = selector
        if (requestedVersion == selector.getVersionConstraint()) {
            return this
        }
        val newSelector: ModuleComponentSelector =
            DefaultModuleComponentSelector.Companion.newSelector(selector.getModuleIdentifier(), requestedVersion, selector.getAttributes(), selector.getCapabilitySelectors())
        return withRequested(newSelector)
    }

    protected abstract fun withRequested(newSelector: ModuleComponentSelector?): ModuleDependencyMetadata?

    protected abstract fun withArtifacts(newArtifacts: ImmutableList<IvyArtifactName?>?): ModuleDependencyMetadata?

    protected abstract fun withRequestedAndArtifacts(newSelector: ModuleComponentSelector?, newArtifacts: ImmutableList<IvyArtifactName?>?): ModuleDependencyMetadata?

    val selector: ModuleComponentSelector
        get() = this.dependencyDescriptor!!.getSelector()

    val isChanging: Boolean
        get() = this.dependencyDescriptor!!.isChanging()

    val isTransitive: Boolean
        get() = this.dependencyDescriptor!!.isTransitive()

    val isConstraint: Boolean
        get() = this.dependencyDescriptor!!.isConstraint()

    override fun toString(): String {
        return this.dependencyDescriptor.toString()
    }
}
