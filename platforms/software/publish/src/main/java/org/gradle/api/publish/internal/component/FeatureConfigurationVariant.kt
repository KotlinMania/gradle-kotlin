/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.api.publish.internal.component

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationVariant
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint.Companion.of
import org.gradle.api.internal.attributes.AttributeContainerInternal

/**
 * A [ConfigurationSoftwareComponentVariant] which is aware of both Maven and Ivy publishing, and can optionally
 * be backed by resolution during publication.
 */
class FeatureConfigurationVariant(
    name: String,
    configuration: Configuration,
    variant: ConfigurationVariant,
    mavenScope: String,
    private val optional: Boolean,
    var dependencyMapping: ConfigurationVariantMapping.DefaultDependencyMappingDetails?
) : ConfigurationSoftwareComponentVariant(name, (variant.getAttributes() as AttributeContainerInternal).asImmutable(), variant.getArtifacts(), configuration), MavenPublishingAwareVariant,
    IvyPublishingAwareVariant, ResolutionBackedVariant {
    private val scopeMapping: MavenPublishingAwareVariant.ScopeMapping

    init {
        this.scopeMapping = MavenPublishingAwareVariant.ScopeMapping.Companion.of(mavenScope, optional)
    }

    override fun getScopeMapping(): MavenPublishingAwareVariant.ScopeMapping {
        return scopeMapping
    }

    override fun isOptional(): Boolean {
        return optional
    }

    override fun getPublishResolvedCoordinates(): Boolean {
        return dependencyMapping != null && dependencyMapping!!.getPublishResolvedCoordinates().getOrElse(false)
    }

    override fun getResolutionConfiguration(): Configuration? {
        return if (dependencyMapping != null) dependencyMapping!!.getResolutionConfiguration() else null
    }
}
