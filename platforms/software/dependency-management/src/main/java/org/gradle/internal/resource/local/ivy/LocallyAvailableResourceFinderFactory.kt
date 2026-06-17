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
package org.gradle.internal.resource.local.ivy

import com.google.common.collect.ImmutableList
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheLockingAccessCoordinator
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheMetadata
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCachesProvider
import org.gradle.api.internal.artifacts.mvnsettings.CannotLocateLocalMavenRepositoryException
import org.gradle.api.internal.artifacts.mvnsettings.LocalMavenRepositoryLocator
import org.gradle.api.internal.artifacts.repositories.resolver.IvyResourcePattern
import org.gradle.api.internal.artifacts.repositories.resolver.M2ResourcePattern
import org.gradle.api.internal.artifacts.repositories.resolver.ResourcePattern
import org.gradle.internal.Factory
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata
import org.gradle.internal.hash.ChecksumService
import org.gradle.internal.hash.HashCode
import org.gradle.internal.resource.local.CompositeLocallyAvailableResourceFinder
import org.gradle.internal.resource.local.FileStoreSearcher
import org.gradle.internal.resource.local.LocallyAvailableResource
import org.gradle.internal.resource.local.LocallyAvailableResourceCandidates
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder
import org.gradle.internal.resource.local.LocallyAvailableResourceFinderSearchableFileStoreAdapter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.util.LinkedList
import java.util.function.BiFunction
import kotlin.math.min

class LocallyAvailableResourceFinderFactory(
    artifactCaches: ArtifactCachesProvider,
    localMavenRepositoryLocator: LocalMavenRepositoryLocator,
    fileStore: FileStoreSearcher<ModuleComponentArtifactIdentifier?>,
    checksumService: ChecksumService?
) : Factory<LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata?>?> {
    private val rootCachesDirectories: MutableList<File?>
    private val localMavenRepositoryLocator: LocalMavenRepositoryLocator
    private val fileStore: FileStoreSearcher<ModuleComponentArtifactIdentifier?>
    private val checksumService: ChecksumService?

    init {
        this.rootCachesDirectories = buildRootCachesDirectories(artifactCaches)
        this.localMavenRepositoryLocator = localMavenRepositoryLocator
        this.fileStore = fileStore
        this.checksumService = checksumService
    }

    private fun buildRootCachesDirectories(artifactCaches: ArtifactCachesProvider): MutableList<File?> {
        val writableRootDir = buildRootDir(artifactCaches.getWritableCacheMetadata())
        return artifactCaches.withReadOnlyCache<ImmutableList<File?>>(BiFunction { md: ArtifactCacheMetadata?, manager: ArtifactCacheLockingAccessCoordinator? ->
            ImmutableList.of<File?>(
                buildRootDir(
                    md!!
                ), writableRootDir
            )
        })
            .orElse(ImmutableList.of<File?>(writableRootDir))
    }

    private fun buildRootDir(md: ArtifactCacheMetadata): File {
        return md.getCacheDir().getParentFile()
    }

    override fun create(): LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata?>? {
        val finders: MutableList<LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata?>?> = LinkedList<LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata?>?>()

        // Order is important here, because they will be searched in that order

        // The current filestore
        finders.add(
            LocallyAvailableResourceFinderSearchableFileStoreAdapter<ModuleComponentArtifactMetadata?>(
                FileStoreSearcher { key: ModuleComponentArtifactMetadata? -> fileStore.search(key!!.getId()) },
                checksumService
            )
        )

        // 1.8
        addForPattern(finders, "artifacts-26/filestore/[organisation]/[module](/[branch])/[revision]/[type]/*/[artifact]-[revision](-[classifier])(.[ext])")

        // 1.5
        addForPattern(finders, "artifacts-24/filestore/[organisation]/[module](/[branch])/[revision]/[type]/*/[artifact]-[revision](-[classifier])(.[ext])")

        // 1.4
        addForPattern(finders, "artifacts-23/filestore/[organisation]/[module](/[branch])/[revision]/[type]/*/[artifact]-[revision](-[classifier])(.[ext])")

        // 1.3
        addForPattern(finders, "artifacts-15/filestore/[organisation]/[module](/[branch])/[revision]/[type]/*/[artifact]-[revision](-[classifier])(.[ext])")

        // 1.1, 1.2
        addForPattern(finders, "artifacts-14/filestore/[organisation]/[module](/[branch])/[revision]/[type]/*/[artifact]-[revision](-[classifier])(.[ext])")

        // rc-1, 1.0
        addForPattern(finders, "artifacts-13/filestore/[organisation]/[module](/[branch])/[revision]/[type]/*/[artifact]-[revision](-[classifier])(.[ext])")

        // Milestone 8 and 9
        addForPattern(finders, "artifacts-8/filestore/[organisation]/[module](/[branch])/[revision]/[type]/*/[artifact]-[revision](-[classifier])(.[ext])")

        // Milestone 7
        addForPattern(finders, "artifacts-7/artifacts/*/[organisation]/[module](/[branch])/[revision]/[type]/[artifact]-[revision](-[classifier])(.[ext])")

        // Milestone 6
        addForPattern(finders, "artifacts-4/[organisation]/[module](/[branch])/*/[type]s/[artifact]-[revision](-[classifier])(.[ext])")
        addForPattern(finders, "artifacts-4/[organisation]/[module](/[branch])/*/pom.originals/[artifact]-[revision](-[classifier])(.[ext])")

        // Milestone 3
        addForPattern(finders, "../cache/[organisation]/[module](/[branch])/[type]s/[artifact]-[revision](-[classifier])(.[ext])")

        // Maven local
        try {
            val localMavenRepository = localMavenRepositoryLocator.localMavenRepository
            if (localMavenRepository.exists()) {
                addForPattern(finders, localMavenRepository, M2ResourcePattern("[organisation]/[module]/[revision]/[artifact]-[revision](-[classifier])(.[ext])"))
            }
        } catch (ex: CannotLocateLocalMavenRepositoryException) {
            finders.add(NoMavenLocalRepositoryResourceFinder(ex))
        }
        return CompositeLocallyAvailableResourceFinder<ModuleComponentArtifactMetadata?>(finders)
    }

    private fun addForPattern(finders: MutableList<LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata?>?>, pattern: String) {
        val wildcardPos = pattern.indexOf("/*/")
        val patternPos = pattern.indexOf("/[")
        require(!(wildcardPos < 0 && patternPos < 0)) { String.format("Unsupported pattern '%s'", pattern) }
        val chopAt: Int
        if (wildcardPos >= 0 && patternPos >= 0) {
            chopAt = min(wildcardPos, patternPos)
        } else if (wildcardPos >= 0) {
            chopAt = wildcardPos
        } else {
            chopAt = patternPos
        }
        val pathPart = pattern.substring(0, chopAt)
        val patternPart = pattern.substring(chopAt + 1)
        for (rootCachesDirectory in rootCachesDirectories) {
            addForPattern(finders, File(rootCachesDirectory, pathPart), IvyResourcePattern(patternPart))
        }
    }

    private fun addForPattern(finders: MutableList<LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata?>?>, baseDir: File, pattern: ResourcePattern?) {
        if (baseDir.exists()) {
            finders.add(PatternBasedLocallyAvailableResourceFinder(baseDir, pattern, checksumService))
        }
    }

    private class NoMavenLocalRepositoryResourceFinder(private val ex: CannotLocateLocalMavenRepositoryException?) : LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata?> {
        private var logged = false

        override fun findCandidates(criterion: ModuleComponentArtifactMetadata?): LocallyAvailableResourceCandidates {
            if (!logged) {
                LOGGER.warn("Unable to locate local Maven repository.")
                LOGGER.debug("Problems while locating local Maven repository.", ex)
                logged = true
            }
            return object : LocallyAvailableResourceCandidates {
                override fun isNone(): Boolean {
                    return true
                }

                override fun findByHashValue(hashValue: HashCode?): LocallyAvailableResource? {
                    return null
                }
            }
        }
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(LocallyAvailableResourceFinderFactory::class.java)
    }
}
