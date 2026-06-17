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

import com.google.common.base.Joiner
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ComponentResolvers
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.DescriptorParseContext
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParseException
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceArtifactResolver
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceResolver
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceResolverDescriptorParseContext
import org.gradle.internal.SystemProperties
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata
import org.gradle.internal.component.model.ComponentOverrideMetadata
import org.gradle.internal.component.model.DefaultModuleDescriptorArtifactMetadata
import org.gradle.internal.component.model.ModuleDescriptorArtifactMetadata
import org.gradle.internal.component.model.MutableModuleSources
import org.gradle.internal.hash.ChecksumService
import org.gradle.internal.hash.HashCode
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult
import org.gradle.internal.resolve.result.ResourceAwareResolveResult
import org.gradle.internal.resource.local.FileResourceRepository
import org.gradle.internal.resource.local.LocallyAvailableExternalResource
import org.gradle.internal.resource.metadata.ExternalResourceMetaData
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

internal abstract class AbstractRepositoryMetadataSource<S : MutableModuleComponentResolveMetadata?> protected constructor(
    protected val metadataArtifactProvider: MetadataArtifactProvider,
    private val fileResourceRepository: FileResourceRepository,
    private val checksumService: ChecksumService
) : MetadataSource<S?> {
    override fun create(
        repositoryName: String,
        componentResolvers: ComponentResolvers,
        moduleVersionIdentifier: ModuleComponentIdentifier,
        prescribedMetaData: ComponentOverrideMetadata,
        artifactResolver: ExternalResourceArtifactResolver,
        result: BuildableModuleComponentMetaDataResolveResult<ModuleComponentResolveMetadata?>
    ): S? {
        val parsedMetadataFromRepository = parseMetaDataFromArtifact(repositoryName, componentResolvers, moduleVersionIdentifier, artifactResolver, result)
        if (parsedMetadataFromRepository != null) {
            LOGGER.debug("Metadata file found for module '{}' in repository '{}'.", moduleVersionIdentifier, repositoryName)
        }
        return parsedMetadataFromRepository
    }

    private fun parseMetaDataFromArtifact(
        repositoryName: String,
        componentResolvers: ComponentResolvers,
        moduleComponentIdentifier: ModuleComponentIdentifier,
        artifactResolver: ExternalResourceArtifactResolver,
        result: ResourceAwareResolveResult
    ): S? {
        val artifact: ModuleComponentArtifactMetadata = getMetaDataArtifactFor(moduleComponentIdentifier)
        val metadataArtifact = artifactResolver.resolveArtifact(artifact, result)
        if (metadataArtifact != null) {
            val context = ExternalResourceResolverDescriptorParseContext(componentResolvers, fileResourceRepository, checksumService)
            val parseResult: MetaDataParser.ParseResult<S?> = parseMetaDataFromResource(moduleComponentIdentifier, metadataArtifact, artifactResolver, context, repositoryName)
            if (parseResult != null) {
                if (parseResult.hasGradleMetadataRedirectionMarker()) {
                    if (result is BuildableModuleComponentMetaDataResolveResult<*>) {
                        result.redirectToGradleMetadata()
                    } else {
                        throw IllegalStateException("Unexpected Gradle metadata redirection answer")
                    }
                }
                val metadata = parseResult.result
                val metadataArtifactFile = metadataArtifact.getFile()
                val metaData = metadataArtifact.getMetaData()
                val sources: MutableModuleSources = metadata!!.sources
                sources.add(DefaultMetadataFileSource(artifact.getId()!!, metadataArtifactFile, findSha1(metaData!!, metadataArtifactFile)))
                context.appendSources(sources)
                return metadata
            }
        }
        return null
    }

    private fun findSha1(metaData: ExternalResourceMetaData, artifact: File): HashCode {
        var sha1 = metaData.getSha1()
        if (sha1 == null) {
            sha1 = checksumService.sha1(artifact)
        }
        return sha1
    }

    private fun getMetaDataArtifactFor(moduleComponentIdentifier: ModuleComponentIdentifier): ModuleDescriptorArtifactMetadata {
        val ivyArtifactName = metadataArtifactProvider.getMetaDataArtifactName(moduleComponentIdentifier.getModule())
        return DefaultModuleDescriptorArtifactMetadata(moduleComponentIdentifier, ivyArtifactName)
    }

    @Throws(MetaDataParseException::class)
    fun checkMetadataConsistency(expectedId: ModuleComponentIdentifier, metadata: MutableModuleComponentResolveMetadata) {
        checkModuleIdentifier(expectedId, metadata.moduleVersionId)
    }

    private fun checkModuleIdentifier(expectedId: ModuleComponentIdentifier, actualId: ModuleVersionIdentifier) {
        val errors: MutableList<String> = ArrayList<String>()
        checkEquals("group", expectedId.getGroup(), actualId.getGroup(), errors)
        checkEquals("module name", expectedId.getModule(), actualId.getName(), errors)
        checkEquals("version", expectedId.getVersion(), actualId.getVersion(), errors)
        if (errors.size > 0) {
            throw MetaDataParseException(
                String.format("inconsistent module metadata found. Descriptor: %s Errors: %s", actualId, joinLines(errors))
            )
        }
    }

    private fun joinLines(lines: MutableList<String>): String {
        return Joiner.on(SystemProperties.getInstance().getLineSeparator()).join(lines)
    }

    private fun checkEquals(label: String, expected: String, actual: String, errors: MutableList<String>) {
        if (expected != actual) {
            errors.add("bad " + label + ": expected='" + expected + "' found='" + actual + "'")
        }
    }

    protected abstract fun parseMetaDataFromResource(
        moduleComponentIdentifier: ModuleComponentIdentifier,
        cachedResource: LocallyAvailableExternalResource,
        artifactResolver: ExternalResourceArtifactResolver,
        context: DescriptorParseContext,
        repoName: String
    ): MetaDataParser.ParseResult<S?>

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(ExternalResourceResolver::class.java)
    }
}
