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

import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.artifacts.capability.CapabilitySelector
import org.gradle.api.capabilities.Capability
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.component.model.ExcludeMetadata
import org.gradle.internal.component.model.IvyArtifactName

interface MutableComponentVariant {
    val name: String?

    val files: MutableList<out ComponentVariant.File>?

    fun addFile(name: String, uri: String)

    fun removeFile(file: ComponentVariant.File): Boolean

    val dependencies: MutableList<ComponentVariant.Dependency>?

    fun addDependency(
        group: String,
        module: String,
        versionConstraint: VersionConstraint,
        excludes: MutableList<ExcludeMetadata>,
        reason: String,
        attributes: ImmutableAttributes,
        requestedCapabilities: MutableSet<CapabilitySelector>,
        endorsing: Boolean,
        artifact: IvyArtifactName?
    )

    val dependencyConstraints: MutableList<ComponentVariant.DependencyConstraint>?

    fun addDependencyConstraint(group: String, module: String, versionConstraint: VersionConstraint, reason: String, attributes: ImmutableAttributes)

    val capabilities: MutableSet<Capability>?

    fun addCapability(group: String, name: String, version: String)

    fun addCapability(capability: Capability)

    val attributes: ImmutableAttributes?

    fun copy(variantName: String, attributes: ImmutableAttributes, capability: Capability): MutableComponentVariant?

    var isAvailableExternally: Boolean
}
