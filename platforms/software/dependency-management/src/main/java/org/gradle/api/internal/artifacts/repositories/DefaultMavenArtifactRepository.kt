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
package org.gradle.api.internal.artifacts.repositories

import com.google.common.collect.ImmutableList
import com.google.common.collect.Lists
import com.google.common.collect.Sets
import org.gradle.api.Action
import org.gradle.api.Transformer
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.repositories.AuthenticationContainer
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.artifacts.repositories.MavenRepositoryContentDescriptor
import org.gradle.api.artifacts.repositories.RepositoryContentDescriptor
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ComponentResolvers
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ConfiguredModuleComponentRepository
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.GradleModuleMetadataParser
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser
import org.gradle.api.internal.artifacts.repositories.descriptor.MavenRepositoryDescriptor
import org.gradle.api.internal.artifacts.repositories.maven.MavenMetadataLoader
import org.gradle.api.internal.artifacts.repositories.metadata.DefaultArtifactMetadataSource
import org.gradle.api.internal.artifacts.repositories.metadata.DefaultGradleModuleMetadataSource
import org.gradle.api.internal.artifacts.repositories.metadata.DefaultImmutableMetadataSources
import org.gradle.api.internal.artifacts.repositories.metadata.DefaultMavenPomMetadataSource
import org.gradle.api.internal.artifacts.repositories.metadata.ImmutableMetadataSources
import org.gradle.api.internal.artifacts.repositories.metadata.MavenMetadataArtifactProvider
import org.gradle.api.internal.artifacts.repositories.metadata.MavenMutableModuleMetadataFactory
import org.gradle.api.internal.artifacts.repositories.metadata.MetadataSource
import org.gradle.api.internal.artifacts.repositories.metadata.RedirectingGradleMetadataModuleMetadataSource
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceArtifactResolver
import org.gradle.api.internal.artifacts.repositories.resolver.MavenResolver
import org.gradle.api.internal.artifacts.repositories.resolver.ResourcePattern
import org.gradle.api.internal.artifacts.repositories.resolver.VersionLister
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ProviderFactory
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata
import org.gradle.internal.component.external.model.maven.MutableMavenModuleResolveMetadata
import org.gradle.internal.component.model.ComponentOverrideMetadata
import org.gradle.internal.deprecation.DeprecationLogger.deprecateMethod
import org.gradle.internal.hash.ChecksumService
import org.gradle.internal.instantiation.InstantiatorFactory
import org.gradle.internal.isolation.IsolatableFactory
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult
import org.gradle.internal.resolve.result.BuildableModuleVersionListingResolveResult
import org.gradle.internal.resource.local.FileResourceRepository
import org.gradle.internal.resource.local.FileStore
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder
import java.net.URI
import java.util.function.Supplier
import javax.inject.Inject

abstract class DefaultMavenArtifactRepository @Inject constructor(
    private val describer: Transformer<String, MavenArtifactRepository>,
    private val fileResolver: FileResolver,
    private val transportFactory: RepositoryTransportFactory,
    protected val locallyAvailableResourceFinder: LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata>,
    instantiatorFactory: InstantiatorFactory,
    val artifactFileStore: FileStore<ModuleComponentArtifactIdentifier>,
    val pomParser: MetaDataParser<MutableMavenModuleResolveMetadata>,
    private val metadataParser: GradleModuleMetadataParser,
    authenticationContainer: AuthenticationContainer,
    val resourcesFileStore: FileStore<String>,
    private val fileResourceRepository: FileResourceRepository,
    private val metadataFactory: MavenMutableModuleMetadataFactory,
    private val isolatableFactory: IsolatableFactory,
    objectFactory: ObjectFactory,
    urlArtifactRepositoryFactory: DefaultUrlArtifactRepository.Factory,
    private val checksumService: ChecksumService,
    providerFactory: ProviderFactory,
    versionParser: VersionParser
) : AbstractAuthenticationSupportedRepository<MavenRepositoryDescriptor>(instantiatorFactory.decorateLenient(), authenticationContainer, objectFactory, providerFactory, versionParser),
    MavenArtifactRepository, ResolutionAwareRepository {
    private val urlArtifactRepository: DefaultUrlArtifactRepository
    private var additionalUrls: MutableList<Any> = ArrayList<Any>()
    private val metadataSources: MavenMetadataSources = DefaultMavenArtifactRepository.MavenMetadataSources()
    protected val instantiatorFactory: InstantiatorFactory

    init {
        this.urlArtifactRepository = urlArtifactRepositoryFactory.create("Maven", Supplier { this.getDisplayName() })
        this.metadataSources.setDefaults()
        this.instantiatorFactory = instantiatorFactory
    }

    override fun getDisplayName(): String {
        return describer.transform(this)
    }

    override fun getUrl(): URI {
        return urlArtifactRepository.getUrl()
    }

    override fun setUrl(url: URI) {
        invalidateDescriptor()
        urlArtifactRepository.setUrl(url)
    }

    override fun setUrl(url: Any) {
        invalidateDescriptor()
        urlArtifactRepository.setUrl(url)
    }

    override fun setAllowInsecureProtocol(allowInsecureProtocol: Boolean) {
        invalidateDescriptor()
        urlArtifactRepository.setAllowInsecureProtocol(allowInsecureProtocol)
    }

    override fun isAllowInsecureProtocol(): Boolean {
        return urlArtifactRepository.isAllowInsecureProtocol()
    }

    @Deprecated("")
    override fun getArtifactUrls(): MutableSet<URI> {
        nagAboutArtifactUrlsDeprecation("getArtifactUrls()")
        return this.artifactUrlsInternal
    }

    private var artifactUrlsInternal: MutableSet<URI>
        get() {
            val result: MutableSet<URI> = LinkedHashSet<URI>()
            for (additionalUrl in additionalUrls) {
                result.add(fileResolver.resolveUri(additionalUrl))
            }
            return result
        }
        private set(urls) {
            additionalUrls = Lists.newArrayList<Any>(urls)
        }

    @Deprecated("")
    override fun artifactUrls(vararg urls: Any) {
        nagAboutArtifactUrlsDeprecation("artifactUrls(Object...)")
        invalidateDescriptor()
        additionalUrls.addAll(ImmutableList.copyOf<Any>(urls))
    }

    @Deprecated("")
    override fun setArtifactUrls(urls: MutableSet<URI>) {
        nagAboutArtifactUrlsDeprecation("setArtifactUrls(Set)")
        invalidateDescriptor()
        this.artifactUrlsInternal = urls
    }

    @Deprecated("")
    override fun setArtifactUrls(urls: Iterable<*>) {
        nagAboutArtifactUrlsDeprecation("setArtifactUrls(Iterable)")
        invalidateDescriptor()
        this.artifactUrlsInternal = urls
    }

    override fun createDescriptor(): MavenRepositoryDescriptor {
        val rootUri = validateUrl()
        return MavenRepositoryDescriptor.Builder(getName(), rootUri)
            .setAuthenticated(usesCredentials())
            .setAuthenticationSchemes(getAuthenticationSchemes())
            .setMetadataSources(metadataSources.asList())
            .setArtifactUrls(Sets.newHashSet<URI>(this.artifactUrlsInternal))
            .create()
    }

    override fun getRepositoryUrls(): MutableCollection<URI> {
        // In a similar way to Ivy, Maven may use other hosts for additional artifacts, but not POMs
        val builder = ImmutableList.builder<URI>()
        val root = getUrl()
        if (root != null) {
            builder.add(root)
        }
        builder.addAll(this.artifactUrlsInternal)
        return builder.build()
    }

    protected fun validateUrl(): URI {
        return urlArtifactRepository.validateUrl()
    }

    override fun createResolver(): ConfiguredModuleComponentRepository {
        val rootUrl = validateUrl()
        return createResolver(rootUrl)
    }

    private fun createResolver(rootUri: URI): MavenResolver {
        val transport = getTransport(rootUri.getScheme())
        val mavenMetadataLoader = MavenMetadataLoader(transport.getResourceAccessor(), resourcesFileStore)
        val metadataSources = createMetadataSources(mavenMetadataLoader)
        val injector: Instantiator = createInjectorForMetadataSuppliers(transport, instantiatorFactory, getUrl(), resourcesFileStore)
        val supplier = createComponentMetadataSupplierFactory(injector, isolatableFactory)
        val lister = createComponentMetadataVersionLister(injector, isolatableFactory)
        return MavenResolver(
            getDescriptor(),
            rootUri,
            transport,
            locallyAvailableResourceFinder,
            artifactFileStore,
            metadataSources,
            MavenMetadataArtifactProvider.Companion.INSTANCE,
            mavenMetadataLoader,
            supplier,
            lister,
            injector,
            checksumService,
            getAllowInsecureContinueWhenDisabled().get()
        )
    }

    override fun metadataSources(configureAction: Action<in MavenArtifactRepository.MetadataSources>) {
        invalidateDescriptor()
        metadataSources.reset()
        configureAction.execute(metadataSources)
    }

    override fun getMetadataSources(): MavenArtifactRepository.MetadataSources {
        return metadataSources
    }

    override fun mavenContent(configureAction: Action<in MavenRepositoryContentDescriptor>) {
        content(uncheckedCast<Action<in RepositoryContentDescriptor>?>(configureAction)!!)
    }

    fun createMetadataSources(mavenMetadataLoader: MavenMetadataLoader): ImmutableMetadataSources {
        val sources = ImmutableList.builder<MetadataSource<*>>()
        // Don't list versions for gradleMetadata if maven-metadata.xml will be checked.
        val listVersionsForGradleMetadata: Boolean = !metadataSources.mavenPom
        val gradleModuleMetadataSource: MetadataSource<MutableModuleComponentResolveMetadata> =
            MavenSnapshotDecoratingSource(
                DefaultGradleModuleMetadataSource(this.metadataParser, metadataFactory, listVersionsForGradleMetadata, checksumService)
            )
        if (metadataSources.gradleMetadata) {
            sources.add(gradleModuleMetadataSource)
        }
        if (metadataSources.mavenPom) {
            val pomMetadataSource = createPomMetadataSource(mavenMetadataLoader, fileResourceRepository)
            if (metadataSources.ignoreGradleMetadataRedirection) {
                sources.add(pomMetadataSource)
            } else {
                sources.add(RedirectingGradleMetadataModuleMetadataSource(pomMetadataSource, gradleModuleMetadataSource))
            }
        }
        if (metadataSources.artifact) {
            sources.add(DefaultArtifactMetadataSource(metadataFactory))
        }
        return DefaultImmutableMetadataSources(sources.build())
    }

    protected open fun createPomMetadataSource(mavenMetadataLoader: MavenMetadataLoader, fileResourceRepository: FileResourceRepository): DefaultMavenPomMetadataSource {
        return DefaultMavenPomMetadataSource(
            MavenMetadataArtifactProvider.Companion.INSTANCE,
            this.pomParser, fileResourceRepository,
            this.metadataValidationServices, mavenMetadataLoader, checksumService
        )
    }

    fun getTransport(scheme: String): RepositoryTransport {
        return transportFactory.createTransport(scheme, getName(), getConfiguredAuthentication(), urlArtifactRepository.createRedirectVerifier())
    }

    override fun createRepositoryDescriptor(versionParser: VersionParser): RepositoryContentDescriptorInternal {
        return DefaultMavenRepositoryContentDescriptor(Supplier { this.getDisplayName() }, versionParser)
    }

    internal class DefaultDescriber : Transformer<String, MavenArtifactRepository> {
        override fun transform(repository: MavenArtifactRepository): String {
            val url = repository.getUrl()
            if (url == null) {
                return repository.getName()
            }
            return repository.getName() + '(' + url + ')'
        }
    }

    private inner class MavenMetadataSources : MavenArtifactRepository.MetadataSources {
        var gradleMetadata: Boolean = false
        var mavenPom: Boolean = false
        var artifact: Boolean = false
        var ignoreGradleMetadataRedirection: Boolean = false

        fun setDefaults() {
            mavenPom()
            ignoreGradleMetadataRedirection = false
        }

        fun reset() {
            gradleMetadata = false
            mavenPom = false
            artifact = false
            ignoreGradleMetadataRedirection = false
        }

        /**
         * This is used to generate the repository id and for reporting purposes on a Build Scan.
         * Changing this means a change of repository.
         *
         * @return a list of implemented metadata sources, as strings.
         */
        fun asList(): MutableList<String> {
            val list: MutableList<String> = ArrayList<String>()
            if (gradleMetadata) {
                list.add("gradleMetadata")
            }
            if (mavenPom) {
                list.add("mavenPom")
            }
            if (artifact) {
                list.add("artifact")
            }
            if (ignoreGradleMetadataRedirection) {
                list.add("ignoreGradleMetadataRedirection")
            }
            return list
        }

        override fun gradleMetadata() {
            invalidateDescriptor()
            gradleMetadata = true
        }

        override fun mavenPom() {
            invalidateDescriptor()
            mavenPom = true
        }

        override fun artifact() {
            invalidateDescriptor()
            artifact = true
        }

        override fun ignoreGradleMetadataRedirection() {
            invalidateDescriptor()
            ignoreGradleMetadataRedirection = true
        }

        override fun isGradleMetadataEnabled(): Boolean {
            return gradleMetadata
        }

        override fun isMavenPomEnabled(): Boolean {
            return mavenPom
        }

        override fun isArtifactEnabled(): Boolean {
            return artifact
        }

        override fun isIgnoreGradleMetadataRedirectionEnabled(): Boolean {
            return ignoreGradleMetadataRedirection
        }
    }

    private class MavenSnapshotDecoratingSource(private val delegate: MetadataSource<MutableModuleComponentResolveMetadata>) : MetadataSource<MutableModuleComponentResolveMetadata> {
        override fun create(
            repositoryName: String,
            componentResolvers: ComponentResolvers,
            moduleComponentIdentifier: ModuleComponentIdentifier,
            prescribedMetaData: ComponentOverrideMetadata,
            artifactResolver: ExternalResourceArtifactResolver,
            result: BuildableModuleComponentMetaDataResolveResult<ModuleComponentResolveMetadata?>
        ): MutableModuleComponentResolveMetadata {
            val metadata = delegate.create(repositoryName, componentResolvers, moduleComponentIdentifier, prescribedMetaData, artifactResolver, result)
            if (metadata != null) {
                return MavenResolver.Companion.processMetaData(metadata as MutableMavenModuleResolveMetadata)
            }
            return null
        }

        override fun listModuleVersions(
            selector: ModuleComponentSelector,
            overrideMetadata: ComponentOverrideMetadata,
            ivyPatterns: MutableList<ResourcePattern>,
            artifactPatterns: MutableList<ResourcePattern>,
            versionLister: VersionLister,
            result: BuildableModuleVersionListingResolveResult
        ) {
            delegate.listModuleVersions(selector, overrideMetadata, ivyPatterns, artifactPatterns, versionLister, result)
        }
    }

    companion object {
        protected open val metadataValidationServices: DefaultMavenPomMetadataSource.MavenMetadataValidator =
            DefaultMavenPomMetadataSource.MavenMetadataValidator { repoName: String?, metadata: MutableMavenModuleResolveMetadata?, artifactResolver: ExternalResourceArtifactResolver? -> true }
            get() = Companion.field

        private fun nagAboutArtifactUrlsDeprecation(methodWithParams: String) {
            deprecateMethod(MavenArtifactRepository::class.java, methodWithParams)
                .willBeRemovedInGradle10()
                .withUpgradeGuideSection(9, "deprecated_maven_artifact_urls")!!
                .nagUser()
        }
    }
}
