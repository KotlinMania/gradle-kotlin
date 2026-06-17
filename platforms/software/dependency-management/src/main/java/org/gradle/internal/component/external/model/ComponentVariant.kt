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

import org.gradle.internal.component.model.VariantResolveMetadata

/**
 * An _immutable_ view of the variant of a component.
 *
 * TODO - this should replace or merge into VariantResolveMetadata, OutgoingVariant, ConfigurationMetadata
 */
interface ComponentVariant : VariantResolveMetadata {
    @JvmField
    val dependencies: ImmutableList<out Dependency?>?

    @JvmField
    val dependencyConstraints: ImmutableList<out DependencyConstraint?>?

    @JvmField
    val files: ImmutableList<out File?>?

    interface Dependency {
        @JvmField
        val group: String?

        @JvmField
        val module: String?

        @JvmField
        val versionConstraint: VersionConstraint?

        @JvmField
        val excludes: ImmutableList<ExcludeMetadata?>?

        @JvmField
        val reason: String?

        @JvmField
        val attributes: ImmutableAttributes?

        @JvmField
        val capabilitySelectors: MutableSet<CapabilitySelector?>?

        @JvmField
        val isEndorsingStrictVersions: Boolean

        @JvmField
        val dependencyArtifact: IvyArtifactName?
    }

    interface DependencyConstraint {
        @JvmField
        val group: String?

        @JvmField
        val module: String?

        @JvmField
        val versionConstraint: VersionConstraint?

        @JvmField
        val reason: String?

        @JvmField
        val attributes: ImmutableAttributes?
    }

    interface File {
        @JvmField
        val name: String?

        @JvmField
        val uri: String?
    }
}
