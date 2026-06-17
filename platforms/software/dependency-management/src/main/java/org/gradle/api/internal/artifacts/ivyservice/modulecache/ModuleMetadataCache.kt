/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.modulecache

import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepository
import org.gradle.internal.component.external.model.ExternalModuleComponentGraphResolveState
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata

interface ModuleMetadataCache {
    fun cacheMissing(repository: ModuleComponentRepository<*>?, id: ModuleComponentIdentifier?): CachedMetadata?

    fun cacheMetaData(repository: ModuleComponentRepository<*>?, id: ModuleComponentIdentifier?, metaData: ModuleComponentResolveMetadata?): CachedMetadata?

    fun getCachedModuleDescriptor(repository: ModuleComponentRepository<*>?, id: ModuleComponentIdentifier?): CachedMetadata?

    interface CachedMetadata {
        val moduleVersion: ResolvedModuleVersion?

        val metadata: ModuleComponentResolveMetadata?

        val age: Duration?

        val isMissing: Boolean

        val moduleSources: ModuleSources?

        /**
         * The metadata after being processed by component metadata rules.
         * Will be null the first time an entry is read from the filesystem cache during a build invocation.
         *
         * @param key the hash of the rules
         */
        fun getProcessedMetadata(key: Int): ExternalModuleComponentGraphResolveState?

        /**
         * Set the processed metadata to be cached in-memory only.
         */
        fun putProcessedMetadata(key: Int, state: ExternalModuleComponentGraphResolveState?)

        /**
         * Returns a copy of this cached metadata where the module metadata is safe to store
         * in-memory, cross-build. That is to say it shouldn't contain any reference to projects,
         * for example.
         */
        fun dehydrate(): CachedMetadata?
    }
}
