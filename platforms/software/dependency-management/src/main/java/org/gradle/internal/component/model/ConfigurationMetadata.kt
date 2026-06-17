/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.internal.component.model

import org.gradle.internal.DisplayName

/**
 *
 * Note that this type is being replaced by several other interfaces that separate out the data and state required at various stages of dependency resolution.
 * You should try to use those interfaces instead of using this interface or introduce a new interface that provides a view over this type but exposes only the
 * data required.
 *
 *
 * @see VariantGraphResolveMetadata
 *
 * @see ConfigurationGraphResolveMetadata
 */
interface ConfigurationMetadata {
    /**
     * The set of configurations that this configuration extends. Includes this configuration.
     *
     * It would be good to remove this from the API, as consumers of this interface generally have no need
     * for this information. However it _is_ currently used by [IvyDependencyDescriptor.selectLegacyConfigurations]
     * to determine if the target 'runtime' configuration includes the target 'compile' configuration.
     */
    @JvmField
    val hierarchy: ImmutableSet<String>?

    @JvmField
    val name: String?

    fun asDescribable(): DisplayName?

    @JvmField
    val attributes: ImmutableAttributes?

    /**
     * Returns the dependencies that apply to this configuration.
     *
     * If the implementation supports [DependencyMetadataRules], this method
     * is responsible for lazily applying the rules the first time it is called.
     */
    @JvmField
    val dependencies: MutableList<out DependencyMetadata>?

    /**
     * Returns the artifacts associated with this configuration, if known.
     */
    @JvmField
    val artifacts: ImmutableList<out ComponentArtifactMetadata>?

    /**
     * Returns the variants of this configuration. Should include at least one value. Exactly one variant must be selected and the artifacts of that variant used.
     */
    val artifactVariants: MutableSet<out VariantResolveMetadata>?

    /**
     * Returns the exclusions to apply to this configuration:
     * - Module exclusions apply to all outgoing dependencies from this configuration
     * - Artifact exclusions apply to artifacts obtained from this configuration
     */
    @JvmField
    val excludes: ImmutableList<ExcludeMetadata>?

    @JvmField
    val isTransitive: Boolean

    @JvmField
    val isVisible: Boolean

    /**
     * Find the component artifact with the given IvyArtifactName, creating a new one if none matches.
     *
     * This is used to create a ComponentArtifactMetadata from an artifact declared as part of a dependency.
     * The reason to do this lookup is that for a local component artifact, the file is part of the artifact metadata.
     * (For external module components, we just instantiate a new artifact metadata).
     */
    fun artifact(artifact: IvyArtifactName): ComponentArtifactMetadata?

    @JvmField
    val capabilities: ImmutableCapabilities?

    @JvmField
    val isExternalVariant: Boolean
}
