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
package org.gradle.api.internal.artifacts.repositories.resolver

import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata
import org.gradle.internal.component.external.model.UrlBackedArtifactMetadata
import org.gradle.internal.component.model.ModuleDescriptorArtifactMetadata
import org.gradle.internal.resolve.result.ResourceAwareResolveResult
import org.gradle.internal.resource.ExternalResourceRepository
import org.gradle.internal.resource.ResourceExceptions
import org.gradle.internal.resource.local.FileStore
import org.gradle.internal.resource.local.LocallyAvailableExternalResource
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder
import org.gradle.internal.resource.transfer.CacheAwareExternalResourceAccessor
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal class DefaultExternalResourceArtifactResolver(
    private val repository: ExternalResourceRepository,
    private val locallyAvailableResourceFinder: LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata>,
    private val ivyPatterns: MutableList<ResourcePattern>,
    private val artifactPatterns: MutableList<ResourcePattern>,
    private val fileStore: FileStore<ModuleComponentArtifactIdentifier>,
    private val resourceAccessor: CacheAwareExternalResourceAccessor
) : ExternalResourceArtifactResolver {
    override fun resolveArtifact(artifact: ModuleComponentArtifactMetadata, result: ResourceAwareResolveResult): LocallyAvailableExternalResource {
        if (artifact is ModuleDescriptorArtifactMetadata) {
            return downloadStaticResource(ivyPatterns, artifact, result)
        }
        return downloadStaticResource(artifactPatterns, artifact, result)
    }

    override fun artifactExists(artifact: ModuleComponentArtifactMetadata, result: ResourceAwareResolveResult): Boolean {
        return staticResourceExists(artifactPatterns, artifact, result)
    }

    private fun staticResourceExists(patternList: MutableList<ResourcePattern>, artifact: ModuleComponentArtifactMetadata, result: ResourceAwareResolveResult): Boolean {
        for (resourcePattern in patternList) {
            if (isIncomplete(resourcePattern, artifact)) {
                continue
            }
            val location = resourcePattern.getLocation(artifact)
            result.attempted(location)
            LOGGER.debug("Loading {}", location)
            try {
                if (repository.resource(location, true).getMetaData() != null) {
                    return true
                }
            } catch (e: Exception) {
                throw ResourceExceptions.getFailed(location.getUri(), e)
            }
        }
        return false
    }

    private fun downloadStaticResource(patternList: MutableList<ResourcePattern>, artifact: ModuleComponentArtifactMetadata, result: ResourceAwareResolveResult): LocallyAvailableExternalResource {
        if (artifact is UrlBackedArtifactMetadata) {
            val urlArtifact = artifact
            return downloadByUrl(patternList, urlArtifact, result)
        } else {
            return downloadByCoords(patternList, artifact, result)
        }
    }

    private fun downloadByUrl(patternList: MutableList<ResourcePattern>, artifact: UrlBackedArtifactMetadata, result: ResourceAwareResolveResult): LocallyAvailableExternalResource {
        for (resourcePattern in patternList) {
            if (isIncomplete(resourcePattern, artifact)) {
                continue
            }
            val moduleDir = resourcePattern.toModuleVersionPath(normalizeComponentId(artifact))
            val location = moduleDir.resolve(normalizeRelativeUrl(artifact))
            result.attempted(location)
            LOGGER.debug("Loading {}", location)
            val localCandidates = locallyAvailableResourceFinder.findCandidates(artifact)
            try {
                val resource = resourceAccessor.getResource(location, artifact.getId().fileName, getFileStore(artifact), localCandidates)
                if (resource != null) {
                    return resource
                }
            } catch (e: Exception) {
                throw ResourceExceptions.getFailed(location.getUri(), e)
            }
        }
        return null
    }

    private fun normalizeRelativeUrl(artifact: UrlBackedArtifactMetadata): String {
        val componentId = artifact.getComponentId()
        if (componentId is MavenUniqueSnapshotComponentIdentifier) {
            // We need to replace the `-SNAPSHOT` in the relative URL but only for the version part
            val snapshotComponentId = componentId
            return artifact.relativeUrl.replace("-" + snapshotComponentId.getSnapshotVersion(), "-" + snapshotComponentId.getTimestampedVersion())
        }
        return artifact.relativeUrl
    }

    private fun normalizeComponentId(artifact: UrlBackedArtifactMetadata): ModuleComponentIdentifier {
        val rawId = artifact.getComponentId()
        if (rawId is MavenUniqueSnapshotComponentIdentifier) {
            // We cannot use a Maven unique snapshot id for the path part
            return rawId.getSnapshotComponent()
        }
        return rawId
    }

    private fun downloadByCoords(patternList: MutableList<ResourcePattern>, artifact: ModuleComponentArtifactMetadata, result: ResourceAwareResolveResult): LocallyAvailableExternalResource {
        for (resourcePattern in patternList) {
            if (isIncomplete(resourcePattern, artifact)) {
                continue
            }
            val location = resourcePattern.getLocation(artifact)
            result.attempted(location)
            LOGGER.debug("Loading {}", location)
            val localCandidates = locallyAvailableResourceFinder.findCandidates(artifact)
            try {
                val resource = resourceAccessor.getResource(location, null, getFileStore(artifact), localCandidates)
                if (resource != null) {
                    return resource
                }
            } catch (e: Exception) {
                throw ResourceExceptions.getFailed(location.getUri(), e)
            }
        }
        return null
    }

    private fun getFileStore(artifact: ModuleComponentArtifactMetadata): CacheAwareExternalResourceAccessor.ResourceFileStore {
        return object : CacheAwareExternalResourceAccessor.DefaultResourceFileStore<ModuleComponentArtifactIdentifier?>(fileStore) {
            override fun computeKey(): ModuleComponentArtifactIdentifier {
                return artifact.getId()!!
            }
        }
    }

    private fun isIncomplete(resourcePattern: ResourcePattern, artifact: ModuleComponentArtifactMetadata): Boolean {
        return !resourcePattern.isComplete(artifact)
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(DefaultExternalResourceArtifactResolver::class.java)
    }
}
