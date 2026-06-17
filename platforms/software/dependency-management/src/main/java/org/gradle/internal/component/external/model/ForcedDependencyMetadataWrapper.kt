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
package org.gradle.internal.component.external.model

import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.internal.component.local.model.DefaultProjectDependencyMetadata
import org.gradle.internal.component.model.DelegatingDependencyMetadata
import org.gradle.internal.component.model.DependencyMetadata
import org.gradle.internal.component.model.ForcingDependencyMetadata
import org.gradle.internal.component.model.IvyArtifactName

class ForcedDependencyMetadataWrapper(private val delegate: ModuleDependencyMetadata) : DelegatingDependencyMetadata(
    delegate
), ForcingDependencyMetadata, ModuleDependencyMetadata {
    override fun getSelector(): ModuleComponentSelector {
        return delegate.selector
    }

    override fun withRequestedVersion(requestedVersion: VersionConstraint): ModuleDependencyMetadata? {
        return ForcedDependencyMetadataWrapper(delegate.withRequestedVersion(requestedVersion)!!)
    }

    override fun withReason(reason: String): ModuleDependencyMetadata {
        return ForcedDependencyMetadataWrapper(delegate.withReason(reason)!!)
    }

    override fun withEndorseStrictVersions(endorse: Boolean): ModuleDependencyMetadata? {
        return ForcedDependencyMetadataWrapper(delegate.withEndorseStrictVersions(endorse)!!)
    }

    override fun withTarget(target: ComponentSelector): DependencyMetadata {
        val dependencyMetadata = delegate.withTarget(target)
        if (dependencyMetadata is DefaultProjectDependencyMetadata) {
            return dependencyMetadata.forced()
        }
        return ForcedDependencyMetadataWrapper((dependencyMetadata as org.gradle.internal.component.external.model.ModuleDependencyMetadata?)!!)
    }

    override fun withTargetAndArtifacts(target: ComponentSelector, artifacts: ImmutableList<IvyArtifactName?>): DependencyMetadata {
        val dependencyMetadata = delegate.withTargetAndArtifacts(target, artifacts)
        if (dependencyMetadata is DefaultProjectDependencyMetadata) {
            return dependencyMetadata.forced()
        }
        return ForcedDependencyMetadataWrapper((dependencyMetadata as org.gradle.internal.component.external.model.ModuleDependencyMetadata?)!!)
    }

    override fun isForce(): Boolean {
        return true
    }

    override fun forced(): ForcingDependencyMetadata? {
        return this
    }

    fun unwrap(): ModuleDependencyMetadata {
        return delegate
    }
}
