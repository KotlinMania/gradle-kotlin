/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.api.internal.artifacts.repositories.resolver

import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata
import org.gradle.internal.component.model.IvyArtifactName
import org.gradle.internal.resource.ExternalResourceName

interface ResourcePattern {
    /**
     * Returns this pattern converted to a String.
     */
    val pattern: String?

    /**
     * Returns the path for the given artifact.
     */
    fun getLocation(artifact: ModuleComponentArtifactMetadata): ExternalResourceName?

    /**
     * Returns the pattern which can be used to search for versions of the given artifact.
     * The returned pattern should include at least one [revision] placeholder.
     */
    fun toVersionListPattern(module: ModuleIdentifier, artifact: IvyArtifactName): ExternalResourceName?

    /**
     * Returns the path for the given module.
     */
    fun toModulePath(moduleIdentifier: ModuleIdentifier): ExternalResourceName?

    /**
     * Returns the path for the given component.
     */
    fun toModuleVersionPath(componentIdentifier: ModuleComponentIdentifier): ExternalResourceName?

    /**
     * Checks if the given identifier contains sufficient information to bind the tokens in this pattern.
     */
    fun isComplete(moduleIdentifier: ModuleIdentifier): Boolean

    /**
     * Checks if the given identifier contains sufficient information to bind the tokens in this pattern.
     */
    fun isComplete(componentIdentifier: ModuleComponentIdentifier): Boolean

    /**
     * Checks if the given identifier contains sufficient information to bind the tokens in this pattern.
     */
    fun isComplete(id: ModuleComponentArtifactMetadata): Boolean
}
