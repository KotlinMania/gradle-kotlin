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

import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleDescriptorHashModuleSource
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.DescriptorParseContext
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser
import org.gradle.api.internal.artifacts.repositories.maven.MavenMetadataLoader
import org.gradle.api.internal.artifacts.repositories.maven.MavenVersionLister
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceArtifactResolver
import org.gradle.api.internal.artifacts.repositories.resolver.MavenResolver
import org.gradle.api.internal.artifacts.repositories.resolver.MavenUniqueSnapshotComponentIdentifier
import org.gradle.api.internal.artifacts.repositories.resolver.ResourcePattern
import org.gradle.api.internal.artifacts.repositories.resolver.VersionLister
import org.gradle.internal.component.external.model.maven.MutableMavenModuleResolveMetadata
import org.gradle.internal.component.model.ComponentOverrideMetadata
import org.gradle.internal.component.model.MutableModuleSources.add
import org.gradle.internal.hash.ChecksumService
import org.gradle.internal.resolve.result.BuildableModuleVersionListingResolveResult
import org.gradle.internal.resource.local.FileResourceRepository
import org.gradle.internal.resource.local.LocallyAvailableExternalResource
import javax.inject.Inject

open class DefaultMavenPomMetadataSource @Inject constructor(
    metadataArtifactProvider: MetadataArtifactProvider,
    private val pomParser: MetaDataParser<MutableMavenModuleResolveMetadata>,
    fileResourceRepository: FileResourceRepository,
    private val validator: MavenMetadataValidator,
    private val mavenMetadataLoader: MavenMetadataLoader,
    private val checksumService: ChecksumService
) : AbstractRepositoryMetadataSource<MutableMavenModuleResolveMetadata?>(
    metadataArtifactProvider, fileResourceRepository,
    checksumService
) {
    override fun parseMetaDataFromResource(
        moduleComponentIdentifier: ModuleComponentIdentifier,
        cachedResource: LocallyAvailableExternalResource,
        artifactResolver: ExternalResourceArtifactResolver,
        context: DescriptorParseContext,
        repoName: String
    ): MetaDataParser.ParseResult<MutableMavenModuleResolveMetadata> {
        val parseResult = pomParser.parseMetaData(context, cachedResource)
        val metaData = parseResult.result
        if (metaData != null) {
            if (moduleComponentIdentifier is MavenUniqueSnapshotComponentIdentifier) {
                // Snapshot POMs use -SNAPSHOT instead of the timestamp as version, so validate against the expected id
                val snapshotComponentIdentifier = moduleComponentIdentifier
                checkMetadataConsistency(snapshotComponentIdentifier.getSnapshotComponent(), metaData)

                metaData.id = snapshotComponentIdentifier
                metaData.snapshotTimestamp = snapshotComponentIdentifier.getTimestamp()
            } else {
                checkMetadataConsistency(moduleComponentIdentifier, metaData)
            }
            val result: MutableMavenModuleResolveMetadata = MavenResolver.Companion.processMetaData(metaData)
            result.sources.add(
                ModuleDescriptorHashModuleSource(
                    checksumService.md5(cachedResource.getFile()),
                    metaData.isChanging
                )
            )
            if (validator.isUsableModule(repoName, result, artifactResolver)) {
                return parseResult
            }
            return null
        }
        return parseResult
    }

    override fun listModuleVersions(
        selector: ModuleComponentSelector,
        overrideMetadata: ComponentOverrideMetadata,
        ivyPatterns: MutableList<ResourcePattern>,
        artifactPatterns: MutableList<ResourcePattern>,
        versionLister: VersionLister,
        result: BuildableModuleVersionListingResolveResult
    ) {
        MavenVersionLister(mavenMetadataLoader).listVersions(selector.getModuleIdentifier(), ivyPatterns, result)
    }

    /**
     * Checks if the POM looks valid to use as a metadata source.
     * In general this will true for all discovered POM files, but in `mavenLocal()` we ignore 'orphaned' POM files that
     * do not have a corresponding artifact.
     */
    interface MavenMetadataValidator {
        fun isUsableModule(repoName: String, metadata: MutableMavenModuleResolveMetadata, artifactResolver: ExternalResourceArtifactResolver): Boolean
    }
}
