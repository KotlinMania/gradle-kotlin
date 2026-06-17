/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.internal.filestore

import org.gradle.api.Namer
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheMetadata
import org.gradle.api.internal.file.temp.TemporaryFileProvider
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier
import org.gradle.internal.file.FileAccessTimeJournal
import org.gradle.internal.hash.ChecksumService
import org.gradle.internal.resource.local.GroupedAndNamedUniqueFileStore
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import java.io.File
import javax.inject.Inject

class DefaultArtifactIdentifierFileStore private constructor(
    baseDir: File?,
    temporaryFileProvider: TemporaryFileProvider?,
    fileAccessTimeJournal: FileAccessTimeJournal?,
    checksumService: ChecksumService?
) : GroupedAndNamedUniqueFileStore<ModuleComponentArtifactIdentifier?>(baseDir, temporaryFileProvider, fileAccessTimeJournal, GROUPER, NAMER, checksumService), ArtifactIdentifierFileStore {
    @ServiceScope([Scope.BuildTree::class, Scope.Project::class])
    class Factory @Inject constructor(
        private val temporaryFileProvider: TemporaryFileProvider?,
        private val fileAccessTimeJournal: FileAccessTimeJournal?,
        private val checksumService: ChecksumService?
    ) {
        fun create(artifactCacheMetadata: ArtifactCacheMetadata): DefaultArtifactIdentifierFileStore {
            return DefaultArtifactIdentifierFileStore(
                artifactCacheMetadata.getFileStoreDirectory(),
                temporaryFileProvider,
                fileAccessTimeJournal,
                checksumService
            )
        }
    }

    companion object {
        private const val NUMBER_OF_GROUPING_DIRS = 3
        val FILE_TREE_DEPTH_TO_TRACK_AND_CLEANUP: Int = NUMBER_OF_GROUPING_DIRS + NUMBER_OF_CHECKSUM_DIRS

        private val GROUPER: Grouper<ModuleComponentArtifactIdentifier?> = object : Grouper<ModuleComponentArtifactIdentifier?> {
            override fun determineGroup(artifactId: ModuleComponentArtifactIdentifier): String {
                return artifactId.getComponentIdentifier().getGroup() + '/' + artifactId.getComponentIdentifier().getModule() + '/' + artifactId.getComponentIdentifier().getVersion()
            }

            override fun getNumberOfGroupingDirs(): Int {
                return NUMBER_OF_GROUPING_DIRS
            }
        }

        private val NAMER: Namer<ModuleComponentArtifactIdentifier?> = ModuleComponentArtifactIdentifier::getFileName
    }
}
