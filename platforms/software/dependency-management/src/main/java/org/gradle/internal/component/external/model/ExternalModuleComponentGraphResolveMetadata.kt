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
package org.gradle.internal.component.external.model

import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.internal.component.model.ComponentGraphResolveMetadata
import org.gradle.internal.component.model.ConfigurationGraphResolveMetadata

/**
 * Component metadata for external module components.
 *
 *
 * Like [ComponentGraphResolveMetadata], methods on this interface should be thread safe and fast -- meaning
 * they do not run user code or execute network requests. This is not currently the case. Instead, that logic should
 * be migrated to [ExternalModuleComponentGraphResolveState]
 */
interface ExternalModuleComponentGraphResolveMetadata : ComponentGraphResolveMetadata {
    override fun getId(): ModuleComponentIdentifier?

    /**
     * Was the metadata artifact for this component missing? When true, the metadata for this component was generated using some defaults.
     */
    @JvmField
    val isMissing: Boolean


    /**
     * Returns the set of variants of this component to use for variant aware resolution of the dependency graph nodes.
     * May be empty, in which case selection falls back to an ecosystem-specific selection strategy.
     */
    val variantsForGraphTraversal: MutableList<out ExternalModuleVariantGraphResolveMetadata?>?

    /**
     * Returns the names of all legacy configurations for this component.
     * May be empty, in which case the component should provide at least one variant via [.getVariantsForGraphTraversal].
     */
    @JvmField
    val configurationNames: MutableSet<String?>?

    /**
     * Get a configuration by name.
     *
     *
     * Configurations are a legacy concept. Only ivy components should expose configurations.
     */
    fun getConfiguration(name: String?): ConfigurationGraphResolveMetadata?
}
