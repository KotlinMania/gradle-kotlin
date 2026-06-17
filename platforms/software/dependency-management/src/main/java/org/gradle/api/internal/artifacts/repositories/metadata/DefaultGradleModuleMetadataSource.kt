/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.internal.artifacts.repositories.metadata

import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ComponentResolvers
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleDescriptorHashModuleSource
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.GradleModuleMetadataParser
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceArtifactResolver
import org.gradle.api.internal.artifacts.repositories.resolver.MavenUniqueSnapshotComponentIdentifier
import org.gradle.api.internal.artifacts.repositories.resolver.ResourcePattern
import org.gradle.api.internal.artifacts.repositories.resolver.VersionLister
import org.gradle.internal.component.external.model.ComponentVariant
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactMetadata
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata
import org.gradle.internal.component.model.ComponentOverrideMetadata
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.component.model.IvyArtifactName
import org.gradle.internal.component.model.MutableModuleSources
import org.gradle.internal.hash.ChecksumService
import org.gradle.internal.hash.HashCode
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult
import org.gradle.internal.resolve.result.BuildableModuleVersionListingResolveResult
import org.gradle.internal.resource.local.LocallyAvailableExternalResource
import org.gradle.internal.resource.metadata.ExternalResourceMetaData
import java.io.File
import javax.inject.Inject

/**
 * TODO: This class sources Gradle metadata files, but there's no corresponding ModuleComponentResolveMetadata for this metadata yet.
 * Because of this, we will generate an empty instance (either a Ivy or Maven) based on the repository type.
 */
class DefaultGradleModuleMetadataSource @Inject constructor(
    private val metadataParser: GradleModuleMetadataParser,
    private val mutableModuleMetadataFactory: MutableModuleMetadataFactory<out MutableModuleComponentResolveMetadata>,
    private val listVersions: Boolean,
    private val checksumService: ChecksumService
) : MetadataSource<MutableModuleComponentResolveMetadata?> {
    override fun create(
        repositoryName: String,
        componentResolvers: ComponentResolvers,
        moduleComponentIdentifier: ModuleComponentIdentifier,
        prescribedMetaData: ComponentOverrideMetadata,
        artifactResolver: ExternalResourceArtifactResolver,
        result: BuildableModuleComponentMetaDataResolveResult<ModuleComponentResolveMetadata?>
    ): MutableModuleComponentResolveMetadata {
        val moduleMetadataArtifact = DefaultIvyArtifactName(moduleComponentIdentifier.getModule(), "module", "module")
        val artifactId = DefaultModuleComponentArtifactMetadata(moduleComponentIdentifier, moduleMetadataArtifact)
        val gradleMetadataArtifact = artifactResolver.resolveArtifact(artifactId, result)
        if (gradleMetadataArtifact != null) {
            val metaDataFromResource: MutableModuleComponentResolveMetadata = mutableModuleMetadataFactory.createForGradleModuleMetadata(moduleComponentIdentifier)
            metadataParser.parse(gradleMetadataArtifact, metaDataFromResource)
            validateGradleMetadata(metaDataFromResource)
            createModuleSources(artifactId, gradleMetadataArtifact, metaDataFromResource)
            handleMavenSnapshotCompatibility(metaDataFromResource)
            return metaDataFromResource
        }
        return null
    }

    private fun createModuleSources(
        artifactId: DefaultModuleComponentArtifactMetadata,
        gradleMetadataArtifact: LocallyAvailableExternalResource,
        metaDataFromResource: MutableModuleComponentResolveMetadata
    ) {
        val sources: MutableModuleSources = metaDataFromResource.sources
        val file = gradleMetadataArtifact.getFile()
        sources.add(ModuleDescriptorHashModuleSource(checksumService.md5(file), metaDataFromResource.isChanging))
        sources.add(DefaultMetadataFileSource(artifactId.getId(), file, findSha1(gradleMetadataArtifact.getMetaData()!!, file)))
    }

    private fun findSha1(metaData: ExternalResourceMetaData, artifact: File): HashCode {
        var sha1 = metaData.getSha1()
        if (sha1 == null) {
            sha1 = checksumService.sha1(artifact)
        }
        return sha1
    }

    override fun listModuleVersions(
        selector: ModuleComponentSelector,
        overrideMetadata: ComponentOverrideMetadata,
        ivyPatterns: MutableList<ResourcePattern>,
        artifactPatterns: MutableList<ResourcePattern>,
        versionLister: VersionLister,
        result: BuildableModuleVersionListingResolveResult
    ) {
        if (listVersions) {
            // List modules based on metadata files, but only if we won't check for maven-metadata (which is preferred)
            val module = selector.getModuleIdentifier()
            val metaDataArtifact: IvyArtifactName = DefaultIvyArtifactName(module.getName(), "module", "module")
            versionLister.listVersions(module, metaDataArtifact, ivyPatterns, result)
        }
    }

    companion object {
        private fun validateGradleMetadata(metaDataFromResource: MutableModuleComponentResolveMetadata) {
            val mutableVariants = metaDataFromResource.mutableVariants
            if (mutableVariants == null || mutableVariants.isEmpty()) {
                throw InvalidUserDataException("Gradle Module Metadata for module " + metaDataFromResource.moduleVersionId + " is invalid because it doesn't declare any variant")
            }
        }

        fun handleMavenSnapshotCompatibility(metaDataFromResource: MutableModuleComponentResolveMetadata) {
            if (metaDataFromResource.id is MavenUniqueSnapshotComponentIdentifier) {
                // Action needed only for Maven unique snapshots
                // Verify that the URL of the artifacts properly references the unique version and not -SNAPSHOT
                val uniqueIdentifier = metaDataFromResource.id as MavenUniqueSnapshotComponentIdentifier
                for (mutableVariant in metaDataFromResource.mutableVariants!!) {
                    var invalidFiles: MutableList<ComponentVariant.File>? = null
                    for (file in mutableVariant.files!!) {
                        if (file.uri!!.contains("SNAPSHOT")) {
                            if (invalidFiles == null) {
                                invalidFiles = ArrayList<ComponentVariant.File>(2)
                            }
                            invalidFiles.add(file)
                        }
                    }
                    if (invalidFiles != null) {
                        for (invalidFile in invalidFiles) {
                            mutableVariant.removeFile(invalidFile)
                            mutableVariant.addFile(invalidFile.name!!, invalidFile.uri!!.replace("SNAPSHOT", uniqueIdentifier.getTimestamp()))
                        }
                    }
                }
            }
        }
    }
}
