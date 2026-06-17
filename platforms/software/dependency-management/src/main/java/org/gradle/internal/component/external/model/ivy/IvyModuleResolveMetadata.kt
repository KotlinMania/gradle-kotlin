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
package org.gradle.internal.component.external.model.ivy

import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata

/**
 * Meta-data for a component resolved from an Ivy repository.
 */
interface IvyModuleResolveMetadata : ModuleComponentResolveMetadata {
    /**
     * {@inheritDoc}
     */
    override fun asMutable(): MutableIvyModuleResolveMetadata?

    /**
     * Returns the branch attribute for the module.
     *
     * @return the branch attribute for the module
     */
    @JvmField
    val branch: String?

    /**
     * Returns the Ivy definitions for the configurations of this module.
     */
    @JvmField
    val configurationDefinitions: ImmutableMap<String, Configuration>?

    /**
     * Returns the Ivy definitions for artifacts of this module.
     */
    @JvmField
    val artifactDefinitions: ImmutableList<Artifact>?

    /**
     * Returns the Ivy excludes for this module.
     */
    @JvmField
    val excludes: ImmutableList<Exclude>?

    /**
     * Returns the extra info for the module.
     *
     * @return the extra info for the module
     */
    @JvmField
    val extraAttributes: ImmutableMap<NamespaceId, String>?

    /**
     * Returns this metadata with all dependencies transformed to use the dynamic constraint version.
     */
    fun withDynamicConstraintVersions(): IvyModuleResolveMetadata?

    @JvmField
    val dependencies: ImmutableList<IvyDependencyDescriptor>?
}
