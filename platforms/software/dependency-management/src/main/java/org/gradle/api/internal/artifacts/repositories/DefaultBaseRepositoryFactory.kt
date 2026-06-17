/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.api.Action
import org.gradle.api.Transformer
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.gradle.api.artifacts.repositories.AuthenticationContainer
import org.gradle.api.artifacts.repositories.FlatDirectoryArtifactRepository
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.artifacts.repositories.MavenRepositoryContentDescriptor
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.artifacts.BaseRepositoryFactory
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.ivyservice.IvyContextManager
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.GradleModuleMetadataParser
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser
import org.gradle.api.internal.artifacts.mvnsettings.LocalMavenRepositoryLocator
import org.gradle.api.internal.artifacts.repositories.metadata.IvyMutableModuleMetadataFactory
import org.gradle.api.internal.artifacts.repositories.metadata.MavenMutableModuleMetadataFactory
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ProviderFactory
import org.gradle.authentication.Authentication
import org.gradle.internal.authentication.AuthenticationSchemeRegistry
import org.gradle.internal.authentication.DefaultAuthenticationContainer
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata
import org.gradle.internal.component.external.model.maven.MutableMavenModuleResolveMetadata
import org.gradle.internal.hash.ChecksumService
import org.gradle.internal.instantiation.InstantiatorFactory
import org.gradle.internal.isolation.IsolatableFactory
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.resource.local.FileResourceRepository
import org.gradle.internal.resource.local.FileStore
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder

class DefaultBaseRepositoryFactory(
    private val localMavenRepositoryLocator: LocalMavenRepositoryLocator,
    private val fileResolver: FileResolver,
    private val fileCollectionFactory: FileCollectionFactory,
    private val transportFactory: RepositoryTransportFactory,
    private val locallyAvailableResourceFinder: LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata>,
    private val artifactFileStore: FileStore<ModuleComponentArtifactIdentifier>,
    private val externalResourcesFileStore: FileStore<String>,
    private val pomParser: MetaDataParser<MutableMavenModuleResolveMetadata>,
    private val metadataParser: GradleModuleMetadataParser,
    private val authenticationSchemeRegistry: AuthenticationSchemeRegistry,
    private val ivyContextManager: IvyContextManager,
    private val moduleIdentifierFactory: ImmutableModuleIdentifierFactory,
    private val instantiatorFactory: InstantiatorFactory,
    private val fileResourceRepository: FileResourceRepository,
    private val mavenMetadataFactory: MavenMutableModuleMetadataFactory,
    private val ivyMetadataFactory: IvyMutableModuleMetadataFactory,
    private val isolatableFactory: IsolatableFactory,
    private val objectFactory: ObjectFactory,
    private val callbackActionDecorator: CollectionCallbackActionDecorator,
    private val urlArtifactRepositoryFactory: DefaultUrlArtifactRepository.Factory,
    private val checksumService: ChecksumService,
    private val providerFactory: ProviderFactory,
    private val versionParser: VersionParser
) : BaseRepositoryFactory {
    private val instantiator: Instantiator

    init {
        this.instantiator = instantiatorFactory.decorateLenient()
    }

    override fun createFlatDirRepository(): FlatDirectoryArtifactRepository {
        return objectFactory.newInstance<DefaultFlatDirArtifactRepository>(
            DefaultFlatDirArtifactRepository::class.java,
            fileCollectionFactory,
            transportFactory,
            locallyAvailableResourceFinder,
            artifactFileStore,
            ivyMetadataFactory,
            instantiatorFactory,
            objectFactory,
            checksumService,
            versionParser
        )
    }

    override fun createGradlePluginPortal(): ArtifactRepository {
        val mavenRepository = createMavenRepository(NamedMavenRepositoryDescriber(BaseRepositoryFactory.PLUGIN_PORTAL_DEFAULT_URL))
        mavenRepository.setUrl(System.getProperty(BaseRepositoryFactory.PLUGIN_PORTAL_OVERRIDE_URL_PROPERTY, BaseRepositoryFactory.PLUGIN_PORTAL_DEFAULT_URL))
        mavenRepository.metadataSources(Action { MavenArtifactRepository.MetadataSources.mavenPom() })
        return mavenRepository
    }

    override fun createMavenLocalRepository(): MavenArtifactRepository {
        val mavenRepository: MavenArtifactRepository = objectFactory.newInstance<DefaultMavenLocalArtifactRepository>(
            DefaultMavenLocalArtifactRepository::class.java,
            fileResolver,
            transportFactory,
            locallyAvailableResourceFinder,
            instantiatorFactory,
            artifactFileStore,
            pomParser,
            metadataParser,
            createAuthenticationContainer(),
            fileResourceRepository,
            mavenMetadataFactory,
            isolatableFactory,
            objectFactory,
            urlArtifactRepositoryFactory,
            checksumService,
            versionParser
        )
        val localMavenRepository = localMavenRepositoryLocator.localMavenRepository
        mavenRepository.setUrl(localMavenRepository)
        return mavenRepository
    }

    override fun createMavenCentralRepository(): MavenArtifactRepository {
        val mavenRepository = createMavenRepository(NamedMavenRepositoryDescriber(RepositoryHandler.MAVEN_CENTRAL_URL))
        mavenRepository.setUrl(RepositoryHandler.MAVEN_CENTRAL_URL)
        mavenRepository.mavenContent(Action { obj: MavenRepositoryContentDescriptor? -> obj!!.releasesOnly() })
        return mavenRepository
    }

    override fun createGoogleRepository(): MavenArtifactRepository {
        val mavenRepository = createMavenRepository(NamedMavenRepositoryDescriber(RepositoryHandler.GOOGLE_URL))
        mavenRepository.setUrl(RepositoryHandler.GOOGLE_URL)
        return mavenRepository
    }

    override fun createIvyRepository(): IvyArtifactRepository {
        val repository: IvyArtifactRepository = objectFactory.newInstance<DefaultIvyArtifactRepository>(
            DefaultIvyArtifactRepository::class.java,
            fileResolver,
            transportFactory,
            locallyAvailableResourceFinder,
            artifactFileStore,
            externalResourcesFileStore,
            createAuthenticationContainer(),
            ivyContextManager,
            moduleIdentifierFactory,
            instantiatorFactory,
            fileResourceRepository,
            metadataParser,
            ivyMetadataFactory,
            isolatableFactory,
            objectFactory,
            urlArtifactRepositoryFactory,
            checksumService,
            providerFactory,
            versionParser
        )
        repository.getAllowInsecureContinueWhenDisabled().convention(false)
        return repository
    }

    override fun createMavenRepository(): MavenArtifactRepository {
        return createMavenRepository(DefaultMavenArtifactRepository.DefaultDescriber())
    }

    fun createMavenRepository(describer: Transformer<String, MavenArtifactRepository>): MavenArtifactRepository {
        val repository: MavenArtifactRepository = objectFactory.newInstance<DefaultMavenArtifactRepository>(
            DefaultMavenArtifactRepository::class.java,
            describer,
            fileResolver,
            transportFactory,
            locallyAvailableResourceFinder,
            instantiatorFactory,
            artifactFileStore,
            pomParser,
            metadataParser,
            createAuthenticationContainer(),
            externalResourcesFileStore,
            fileResourceRepository,
            mavenMetadataFactory,
            isolatableFactory,
            objectFactory,
            urlArtifactRepositoryFactory,
            checksumService,
            providerFactory,
            versionParser
        )
        repository.getAllowInsecureContinueWhenDisabled().convention(false)
        return repository
    }

    protected fun createAuthenticationContainer(): AuthenticationContainer {
        val container = objectFactory.newInstance<DefaultAuthenticationContainer>(DefaultAuthenticationContainer::class.java, instantiator, callbackActionDecorator)

        for (e in authenticationSchemeRegistry.getRegisteredSchemes<Authentication?>()!!.entries) {
            container.registerBinding<Authentication>(e.key, e.value!!)
        }

        return container
    }

    private class NamedMavenRepositoryDescriber(private val defaultUrl: String) : Transformer<String, MavenArtifactRepository> {
        override fun transform(repository: MavenArtifactRepository): String {
            val url = repository.getUrl()
            if (url == null || defaultUrl == url.toString()) {
                return repository.getName()
            }
            return repository.getName() + '(' + url + ')'
        }
    }
}
