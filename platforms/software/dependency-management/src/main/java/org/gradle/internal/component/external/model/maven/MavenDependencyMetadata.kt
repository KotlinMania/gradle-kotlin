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
package org.gradle.internal.component.external.model.maven

import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.internal.component.external.model.ExternalModuleDependencyMetadata
import org.gradle.internal.component.external.model.ModuleDependencyMetadata
import org.gradle.internal.component.model.IvyArtifactName

/**
 * Represents a dependency declared in a Maven POM file.
 */
open class MavenDependencyMetadata private constructor(@JvmField val dependencyDescriptor: MavenDependencyDescriptor, reason: String?, endorsing: Boolean, artifacts: ImmutableList<IvyArtifactName>) :
    ExternalModuleDependencyMetadata(reason, endorsing, artifacts) {
    @JvmOverloads
    constructor(dependencyDescriptor: MavenDependencyDescriptor, reason: String? = null, endorsing: Boolean = false) : this(
        dependencyDescriptor,
        reason,
        endorsing,
        dependencyDescriptor.getConfigurationArtifacts()
    )

    val excludes: ImmutableList<ExcludeMetadata>
        get() = dependencyDescriptor.getConfigurationExcludes()

    override fun withReason(reason: String): ModuleDependencyMetadata {
        return MavenDependencyMetadata(dependencyDescriptor, reason, isEndorsingStrictVersions, artifacts)
    }

    override fun withEndorseStrictVersions(endorse: Boolean): ModuleDependencyMetadata {
        return MavenDependencyMetadata(dependencyDescriptor, reason, endorse, artifacts)
    }

    override fun withRequested(newSelector: ModuleComponentSelector): ModuleDependencyMetadata {
        val newDescriptor = dependencyDescriptor.withRequested(newSelector)
        return MavenDependencyMetadata(newDescriptor, reason, isEndorsingStrictVersions, artifacts)
    }

    override fun withArtifacts(newArtifacts: ImmutableList<IvyArtifactName>): ModuleDependencyMetadata {
        return MavenDependencyMetadata(dependencyDescriptor, reason, isEndorsingStrictVersions, newArtifacts)
    }

    override fun withRequestedAndArtifacts(newSelector: ModuleComponentSelector, newArtifacts: ImmutableList<IvyArtifactName>): ModuleDependencyMetadata {
        val newDelegate = dependencyDescriptor.withRequested(newSelector)
        return MavenDependencyMetadata(newDelegate, reason, isEndorsingStrictVersions, newArtifacts)
    }
}
