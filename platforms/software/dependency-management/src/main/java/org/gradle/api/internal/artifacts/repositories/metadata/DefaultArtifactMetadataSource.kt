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
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ComponentResolvers
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleDescriptorHashModuleSource
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceArtifactResolver
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceResolver
import org.gradle.api.internal.artifacts.repositories.resolver.ResourcePattern
import org.gradle.api.internal.artifacts.repositories.resolver.VersionLister
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactMetadata
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata
import org.gradle.internal.component.model.ComponentOverrideMetadata
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.component.model.IvyArtifactName
import org.gradle.internal.component.model.MutableModuleSources.add
import org.gradle.internal.hash.Hashing
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult
import org.gradle.internal.resolve.result.BuildableModuleVersionListingResolveResult
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * A metadata source which simply verifies the existence of a given artifact and does not
 * attempt to fetch any further metadata from other external sources.
 */
class DefaultArtifactMetadataSource(private val mutableModuleMetadataFactory: MutableModuleMetadataFactory<out MutableModuleComponentResolveMetadata>) :
    MetadataSource<MutableModuleComponentResolveMetadata?> {
    override fun create(
        repositoryName: String,
        componentResolvers: ComponentResolvers,
        moduleComponentIdentifier: ModuleComponentIdentifier,
        prescribedMetaData: ComponentOverrideMetadata,
        artifactResolver: ExternalResourceArtifactResolver,
        result: BuildableModuleComponentMetaDataResolveResult<ModuleComponentResolveMetadata?>
    ): MutableModuleComponentResolveMetadata {
        val artifact: IvyArtifactName = getPrimaryArtifact(moduleComponentIdentifier, prescribedMetaData)
        if (!artifactResolver.artifactExists(DefaultModuleComponentArtifactMetadata(moduleComponentIdentifier, artifact), result)) {
            return null
        }

        LOGGER.debug("Using default metadata for artifact in module '{}' and repository '{}'.", moduleComponentIdentifier, repositoryName)

        val metadata: MutableModuleComponentResolveMetadata = mutableModuleMetadataFactory.missing(moduleComponentIdentifier)

        // For empty metadata, we use a hash based on the identifier
        val descriptorHash = Hashing.md5().hashString(moduleComponentIdentifier.toString())
        metadata.sources.add(ModuleDescriptorHashModuleSource(descriptorHash, false))
        return metadata
    }

    override fun listModuleVersions(
        selector: ModuleComponentSelector,
        overrideMetadata: ComponentOverrideMetadata,
        ivyPatterns: MutableList<ResourcePattern>,
        artifactPatterns: MutableList<ResourcePattern>,
        versionLister: VersionLister,
        result: BuildableModuleVersionListingResolveResult
    ) {
        // List modules with missing metadata files
        val dependencyArtifact: IvyArtifactName = getPrimaryArtifact(selector, overrideMetadata)
        versionLister.listVersions(selector.getModuleIdentifier(), dependencyArtifact, artifactPatterns, result)
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(ExternalResourceResolver::class.java)
        private fun getPrimaryArtifact(moduleComponentIdentifier: ModuleComponentIdentifier, overrideMetadata: ComponentOverrideMetadata): IvyArtifactName {
            if (overrideMetadata.getArtifact() != null) {
                return overrideMetadata.getArtifact()!!
            }
            return DefaultIvyArtifactName(moduleComponentIdentifier.getModule(), "jar", "jar")
        }

        private fun getPrimaryArtifact(selector: ModuleComponentSelector, overrideMetadata: ComponentOverrideMetadata): IvyArtifactName {
            if (overrideMetadata.getArtifact() != null) {
                return overrideMetadata.getArtifact()!!
            }
            return DefaultIvyArtifactName(selector.getModule(), "jar", "jar")
        }
    }
}
