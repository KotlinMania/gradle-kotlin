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
package org.gradle.api.internal.artifacts.repositories

import org.gradle.api.artifacts.repositories.AuthenticationContainer
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.GradleModuleMetadataParser
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser
import org.gradle.api.internal.artifacts.repositories.maven.MavenMetadataLoader
import org.gradle.api.internal.artifacts.repositories.metadata.DefaultMavenPomMetadataSource
import org.gradle.api.internal.artifacts.repositories.metadata.MavenLocalPomMetadataSource
import org.gradle.api.internal.artifacts.repositories.metadata.MavenMetadataArtifactProvider
import org.gradle.api.internal.artifacts.repositories.metadata.MavenMutableModuleMetadataFactory
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceArtifactResolver
import org.gradle.api.internal.artifacts.repositories.resolver.MavenResolver
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.model.ObjectFactory
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata
import org.gradle.internal.component.external.model.maven.MutableMavenModuleResolveMetadata
import org.gradle.internal.hash.ChecksumService
import org.gradle.internal.instantiation.InstantiatorFactory
import org.gradle.internal.isolation.IsolatableFactory
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.resolve.result.DefaultResourceAwareResolveResult
import org.gradle.internal.resource.local.FileResourceRepository
import org.gradle.internal.resource.local.FileStore
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.inject.Inject

abstract class DefaultMavenLocalArtifactRepository @Inject constructor(
    fileResolver: FileResolver, transportFactory: RepositoryTransportFactory,
    locallyAvailableResourceFinder: LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata>, instantiatorFactory: InstantiatorFactory,
    artifactFileStore: FileStore<ModuleComponentArtifactIdentifier>,
    pomParser: MetaDataParser<MutableMavenModuleResolveMetadata>,
    metadataParser: GradleModuleMetadataParser,
    authenticationContainer: AuthenticationContainer,
    fileResourceRepository: FileResourceRepository,
    metadataFactory: MavenMutableModuleMetadataFactory,
    isolatableFactory: IsolatableFactory,
    objectFactory: ObjectFactory,
    urlArtifactRepositoryFactory: DefaultUrlArtifactRepository.Factory,
    private val checksumService: ChecksumService,
    versionParser: VersionParser
) : DefaultMavenArtifactRepository(
    DefaultDescriber(),
    fileResolver,
    transportFactory,
    locallyAvailableResourceFinder,
    instantiatorFactory,
    artifactFileStore,
    pomParser,
    metadataParser,
    authenticationContainer,
    null,
    fileResourceRepository,
    metadataFactory,
    isolatableFactory,
    objectFactory,
    urlArtifactRepositoryFactory,
    checksumService,
    null,
    versionParser
), MavenArtifactRepository {
    override fun createResolver(): MavenResolver {
        val rootUri = validateUrl()

        val transport = getTransport(rootUri.getScheme())
        val mavenMetadataLoader = MavenMetadataLoader(transport.getResourceAccessor(), getResourcesFileStore())
        val injector: Instantiator = createInjectorForMetadataSuppliers(transport, getInstantiatorFactory(), rootUri, getResourcesFileStore())
        return MavenResolver(
            getDescriptor(),
            rootUri,
            transport,
            getLocallyAvailableResourceFinder(),
            getArtifactFileStore(),
            createMetadataSources(mavenMetadataLoader),
            MavenMetadataArtifactProvider.Companion.INSTANCE,
            mavenMetadataLoader,
            null,
            null,
            injector,
            checksumService,
            false
        )
    }

    override fun createPomMetadataSource(mavenMetadataLoader: MavenMetadataLoader, fileResourceRepository: FileResourceRepository): DefaultMavenPomMetadataSource {
        return MavenLocalPomMetadataSource(
            MavenMetadataArtifactProvider.Companion.INSTANCE,
            getPomParser(),
            fileResourceRepository,
            getMetadataValidationServices(),
            mavenMetadataLoader,
            checksumService
        )
    }

    override fun getMetadataValidationServices(): DefaultMavenPomMetadataSource.MavenMetadataValidator {
        return MavenLocalMetadataValidator()
    }

    /**
     * It is common for a local m2 repo to have POM files with no respective artifacts. Ignore these POM files.
     */
    private class MavenLocalMetadataValidator : DefaultMavenPomMetadataSource.MavenMetadataValidator {
        override fun isUsableModule(repoName: String, metaData: MutableMavenModuleResolveMetadata, artifactResolver: ExternalResourceArtifactResolver): Boolean {
            if (metaData.isPomPackaging) {
                return true
            }

            // check custom packaging
            val artifact: ModuleComponentArtifactMetadata?
            if (metaData.isKnownJarPackaging) {
                artifact = metaData.artifact("jar", "jar", null)
            } else {
                artifact = metaData.artifact(metaData.packaging, metaData.packaging, null)
            }

            if (artifactResolver.artifactExists(artifact!!, DefaultResourceAwareResolveResult())) {
                return true
            }

            LOGGER.debug("POM file found for module '{}' in repository '{}' but no artifact found. Ignoring.", metaData.moduleVersionId, repoName)
            return false
        }
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(MavenResolver::class.java)
    }
}
