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
package org.gradle.api.internal.artifacts.ivyservice

import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedModuleVersion
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata
import java.io.File
import java.time.Duration

/**
 * Determines whether cached external artifacts and metadata should be considered expired.
 */
interface CacheExpirationControl {
    fun versionListExpiry(moduleIdentifier: ModuleIdentifier?, moduleVersions: MutableSet<ModuleVersionIdentifier?>?, age: Duration?): Expiry?

    fun missingModuleExpiry(component: ModuleComponentIdentifier?, age: Duration?): Expiry?

    fun moduleExpiry(component: ModuleComponentIdentifier?, resolvedModuleVersion: ResolvedModuleVersion?, age: Duration?): Expiry?

    fun moduleExpiry(resolvedModuleVersion: ResolvedModuleVersion?, age: Duration?, changing: Boolean): Expiry?

    fun moduleArtifactsExpiry(
        moduleVersionId: ModuleVersionIdentifier?, artifacts: MutableSet<ModuleComponentArtifactMetadata?>?,
        age: Duration?, belongsToChangingModule: Boolean, moduleDescriptorInSync: Boolean
    ): Expiry?

    fun artifactExpiry(artifactMetadata: ModuleComponentArtifactMetadata?, cachedArtifactFile: File?, age: Duration?, belongsToChangingModule: Boolean, moduleDescriptorInSync: Boolean): Expiry?

    fun changingModuleExpiry(component: ModuleComponentIdentifier?, resolvedModuleVersion: ResolvedModuleVersion?, age: Duration?): Expiry?

    interface Expiry {
        val isMustCheck: Boolean

        val keepFor: Duration?
    }
}
