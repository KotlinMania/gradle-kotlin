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
import org.gradle.api.Action
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.repositories.AuthenticationContainer
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.artifacts.repositories.IvyArtifactRepositoryMetaDataProvider
import org.gradle.api.artifacts.repositories.IvyPatternRepositoryLayout
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.ivyservice.IvyContextManager
import org.gradle.api.internal.artifacts.ivyservice.IvyContextualMetaDataParser
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ConfiguredModuleComponentRepository
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.GradleModuleMetadataParser
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.IvyModuleDescriptorConverter
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.IvyXmlModuleDescriptorParser
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser
import org.gradle.api.internal.artifacts.repositories.descriptor.IvyRepositoryDescriptor
import org.gradle.api.internal.artifacts.repositories.layout.AbstractRepositoryLayout
import org.gradle.api.internal.artifacts.repositories.layout.DefaultIvyPatternRepositoryLayout
import org.gradle.api.internal.artifacts.repositories.layout.GradleRepositoryLayout
import org.gradle.api.internal.artifacts.repositories.layout.IvyRepositoryLayout
import org.gradle.api.internal.artifacts.repositories.layout.MavenRepositoryLayout
import org.gradle.api.internal.artifacts.repositories.layout.ResolvedPattern
import org.gradle.api.internal.artifacts.repositories.metadata.DefaultArtifactMetadataSource
import org.gradle.api.internal.artifacts.repositories.metadata.DefaultGradleModuleMetadataSource
import org.gradle.api.internal.artifacts.repositories.metadata.DefaultImmutableMetadataSources
import org.gradle.api.internal.artifacts.repositories.metadata.DefaultIvyDescriptorMetadataSource
import org.gradle.api.internal.artifacts.repositories.metadata.ImmutableMetadataSources
import org.gradle.api.internal.artifacts.repositories.metadata.IvyMetadataArtifactProvider
import org.gradle.api.internal.artifacts.repositories.metadata.IvyMutableModuleMetadataFactory
import org.gradle.api.internal.artifacts.repositories.metadata.MetadataSource
import org.gradle.api.internal.artifacts.repositories.metadata.RedirectingGradleMetadataModuleMetadataSource
import org.gradle.api.internal.artifacts.repositories.resolver.IvyResolver
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ProviderFactory
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata
import org.gradle.internal.component.external.model.ivy.MutableIvyModuleResolveMetadata
import org.gradle.internal.hash.ChecksumService
import org.gradle.internal.instantiation.InstantiatorFactory
import org.gradle.internal.isolation.IsolatableFactory
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.resource.local.FileResourceRepository
import org.gradle.internal.resource.local.FileStore
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder
import java.net.URI
import java.util.Collections
import java.util.function.Supplier
import javax.inject.Inject
import kotlin.concurrent.Volatile

abstract class DefaultIvyArtifactRepository @Inject constructor(
    private val fileResolver: FileResolver,
    private val transportFactory: RepositoryTransportFactory,
    private val locallyAvailableResourceFinder: LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata>,
    private val artifactFileStore: FileStore<ModuleComponentArtifactIdentifier>,
    private val externalResourcesFileStore: FileStore<String>,
    authenticationContainer: AuthenticationContainer,
    private val ivyContextManager: IvyContextManager,
    private val moduleIdentifierFactory: ImmutableModuleIdentifierFactory,
    private val instantiatorFactory: InstantiatorFactory,
    private val fileResourceRepository: FileResourceRepository,
    private val moduleMetadataParser: GradleModuleMetadataParser,
    private val metadataFactory: IvyMutableModuleMetadataFactory,
    private val isolatableFactory: IsolatableFactory,
    objectFactory: ObjectFactory,
    urlArtifactRepositoryFactory: DefaultUrlArtifactRepository.Factory,
    private val checksumService: ChecksumService,
    providerFactory: ProviderFactory,
    versionParser: VersionParser
) : AbstractAuthenticationSupportedRepository<IvyRepositoryDescriptor>(instantiatorFactory.decorateLenient(), authenticationContainer, objectFactory, providerFactory, versionParser),
    IvyArtifactRepository, ResolutionAwareRepository {
    @Volatile
    private var schemes: MutableSet<String>? = null
    private var layout: AbstractRepositoryLayout
    private val urlArtifactRepository: DefaultUrlArtifactRepository
    private val additionalPatternsLayout: AdditionalPatternsRepositoryLayout
    private val metaDataProvider: MetaDataProvider
    private val instantiator: Instantiator
    private val metadataSources: IvyMetadataSources = DefaultIvyArtifactRepository.IvyMetadataSources()

    init {
        this.urlArtifactRepository = urlArtifactRepositoryFactory.create("Ivy", Supplier { this.getDisplayName() })
        this.additionalPatternsLayout = AdditionalPatternsRepositoryLayout(fileResolver)
        this.layout = GradleRepositoryLayout()
        this.metaDataProvider = MetaDataProvider()
        this.instantiator = instantiatorFactory.decorateLenient()
        this.metadataSources.setDefaults()
    }

    override fun getDisplayName(): String {
        val url = getUrl()
        if (url == null) {
            return super.getDisplayName()
        }
        return super.getDisplayName() + '(' + url + ')'
    }

    fun createPublisher(): IvyResolver {
        return createRealResolver()
    }

    override fun createResolver(): ConfiguredModuleComponentRepository {
        return createRealResolver()
    }

    override fun createDescriptor(): IvyRepositoryDescriptor {
        val schemes = getSchemes()
        validate(schemes)

        val url = urlArtifactRepository.getUrl()
        val builder = IvyRepositoryDescriptor.Builder(getName(), url)
            .setAuthenticated(usesCredentials())
            .setAuthenticationSchemes(getAuthenticationSchemes())
            .setMetadataSources(metadataSources.asList())
        layout.apply(url, builder)
        additionalPatternsLayout.apply(url, builder)
        return builder.create()
    }

    private fun createRealResolver(): IvyResolver {
        val schemes = getSchemes()
        validate(schemes)
        return createResolver(schemes)
    }

    private fun createResolver(schemes: MutableSet<String>): IvyResolver {
        return createResolver(transportFactory.createTransport(schemes, getName(), getConfiguredAuthentication(), urlArtifactRepository.createRedirectVerifier()))
    }

    private fun validate(schemes: MutableSet<String>) {
        if (schemes.isEmpty()) {
            throw InvalidUserDataException("You must specify a base url or at least one artifact pattern for the Ivy repository '" + getDisplayName() + "'.")
        }
    }

    private fun getSchemes(): MutableSet<String> {
        if (schemes == null) {
            val uri = getUrl()
            // use a local variable to prepare the set,
            // so that other threads do not see the half-initialized
            // list of schemes and fail in strange ways
            val result: MutableSet<String> = LinkedHashSet<String>()
            layout.addSchemes(uri, result)
            additionalPatternsLayout.addSchemes(uri, result)
            schemes = Collections.unmodifiableSet<String>(result)
        }
        return schemes!!
    }

    private fun createResolver(transport: RepositoryTransport): IvyResolver {
        val injector: Instantiator = createInjectorForMetadataSuppliers(transport, instantiatorFactory, getUrl(), externalResourcesFileStore)
        val supplierFactory = createComponentMetadataSupplierFactory(injector, isolatableFactory)
        val listerFactory = createComponentMetadataVersionLister(injector, isolatableFactory)
        return IvyResolver(
            getDescriptor(),
            transport,
            locallyAvailableResourceFinder,
            metaDataProvider.dynamicResolve,
            artifactFileStore,
            supplierFactory,
            listerFactory,
            createMetadataSources(),
            IvyMetadataArtifactProvider.Companion.INSTANCE,
            injector,
            checksumService,
            getAllowInsecureContinueWhenDisabled().get()
        )
    }

    override fun metadataSources(configureAction: Action<in IvyArtifactRepository.MetadataSources>) {
        invalidateDescriptor()
        metadataSources.reset()
        configureAction.execute(metadataSources)
    }

    override fun getMetadataSources(): IvyArtifactRepository.MetadataSources {
        return metadataSources
    }

    private fun createMetadataSources(): ImmutableMetadataSources {
        val sources = ImmutableList.builder<MetadataSource<*>>()
        val gradleModuleMetadataSource = DefaultGradleModuleMetadataSource(moduleMetadataParser, metadataFactory, true, checksumService)
        if (metadataSources.gradleMetadata) {
            sources.add(gradleModuleMetadataSource)
        }
        if (metadataSources.ivyDescriptor) {
            val ivyDescriptorMetadataSource = DefaultIvyDescriptorMetadataSource(IvyMetadataArtifactProvider.Companion.INSTANCE, createIvyDescriptorParser(), fileResourceRepository, checksumService)
            if (metadataSources.ignoreGradleMetadataRedirection) {
                sources.add(ivyDescriptorMetadataSource)
            } else {
                sources.add(RedirectingGradleMetadataModuleMetadataSource(ivyDescriptorMetadataSource, gradleModuleMetadataSource))
            }
        }
        if (metadataSources.artifact) {
            sources.add(DefaultArtifactMetadataSource(metadataFactory))
        }
        return DefaultImmutableMetadataSources(sources.build())
    }

    private fun createIvyDescriptorParser(): MetaDataParser<MutableIvyModuleResolveMetadata> {
        return IvyContextualMetaDataParser<MutableIvyModuleResolveMetadata>(
            ivyContextManager,
            IvyXmlModuleDescriptorParser(IvyModuleDescriptorConverter(moduleIdentifierFactory), moduleIdentifierFactory, fileResourceRepository, metadataFactory)
        )
    }

    override fun getUrl(): URI {
        return urlArtifactRepository.getUrl()
    }


    override fun getRepositoryUrls(): MutableCollection<URI> {
        // Ivy can resolve files from multiple hosts, so we need to look at all
        // of the possible URLs used by the Ivy resolver to identify all of the repositories
        val builder = ImmutableList.builder<URI>()
        val root = getUrl()
        if (root != null) {
            builder.add(root)
        }
        for (pattern in additionalPatternsLayout.artifactPatterns) {
            val baseUri = ResolvedPattern(pattern, fileResolver).baseUri
            if (baseUri != null) {
                builder.add(baseUri)
            }
        }
        for (pattern in additionalPatternsLayout.ivyPatterns) {
            val baseUri = ResolvedPattern(pattern, fileResolver).baseUri
            if (baseUri != null) {
                builder.add(baseUri)
            }
        }
        return builder.build()
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

    override fun artifactPattern(pattern: String) {
        invalidateDescriptor()
        additionalPatternsLayout.artifactPatterns.add(pattern)
    }

    override fun ivyPattern(pattern: String) {
        invalidateDescriptor()
        additionalPatternsLayout.ivyPatterns.add(pattern)
    }

    fun additionalArtifactPatterns(): MutableSet<String> {
        return additionalPatternsLayout.artifactPatterns
    }

    fun additionalIvyPatterns(): MutableSet<String> {
        return additionalPatternsLayout.ivyPatterns
    }

    override fun layout(layoutName: String) {
        invalidateDescriptor()
        when (layoutName) {
            "ivy" -> layout = instantiator.newInstance<IvyRepositoryLayout>(IvyRepositoryLayout::class.java)
            "maven" -> layout = instantiator.newInstance<MavenRepositoryLayout>(MavenRepositoryLayout::class.java)
            "pattern" -> layout = instantiator.newInstance<DefaultIvyPatternRepositoryLayout>(DefaultIvyPatternRepositoryLayout::class.java)
            else -> layout = instantiator.newInstance<GradleRepositoryLayout>(GradleRepositoryLayout::class.java)
        }
    }

    override fun patternLayout(config: Action<in IvyPatternRepositoryLayout>) {
        invalidateDescriptor()
        val layout = instantiator.newInstance<DefaultIvyPatternRepositoryLayout>(DefaultIvyPatternRepositoryLayout::class.java)
        this.layout = layout
        config.execute(layout)
    }

    var repositoryLayout: AbstractRepositoryLayout
        get() = layout
        set(layout) {
            invalidateDescriptor()
            this.layout = layout
        }

    override fun getResolve(): IvyArtifactRepositoryMetaDataProvider {
        return metaDataProvider
    }

    override fun invalidateDescriptor() {
        super.invalidateDescriptor()
        schemes = null
    }

    fun hasStandardPattern(): Boolean {
        // This is wasteful because we create a descriptor and throw it away immediately.
        val descriptor = createDescriptor()
        val artifactPatterns = descriptor.getArtifactPatterns()
        if (artifactPatterns.size == 1) {
            return artifactPatterns.get(0) == IvyArtifactRepository.GRADLE_ARTIFACT_PATTERN
        } else {
            return false
        }
    }

    /**
     * Layout for applying additional patterns added via [.artifactPatterns] and [.ivyPatterns].
     */
    private class AdditionalPatternsRepositoryLayout(private val fileResolver: FileResolver) : AbstractRepositoryLayout() {
        private val artifactPatterns: MutableSet<String> = LinkedHashSet<String>()
        private val ivyPatterns: MutableSet<String> = LinkedHashSet<String>()

        override fun apply(baseUri: URI?, builder: IvyRepositoryDescriptor.Builder) {
            for (artifactPattern in artifactPatterns) {
                val resolvedPattern = ResolvedPattern(artifactPattern, fileResolver)
                builder.addArtifactPattern(artifactPattern)
                builder.addArtifactResource(resolvedPattern.baseUri, resolvedPattern.pattern)
            }

            for (ivyPattern in ivyPatterns) {
                builder.addIvyPattern(ivyPattern)
            }
            val effectiveIvyPatterns = if (ivyPatterns.isEmpty()) artifactPatterns else ivyPatterns
            for (ivyPattern in effectiveIvyPatterns) {
                val resolvedPattern = ResolvedPattern(ivyPattern, fileResolver)
                builder.addIvyResource(resolvedPattern.baseUri, resolvedPattern.pattern)
            }
        }

        override fun addSchemes(baseUri: URI, schemes: MutableSet<String>) {
            for (pattern in artifactPatterns) {
                schemes.add(ResolvedPattern(pattern, fileResolver).scheme)
            }
            for (pattern in ivyPatterns) {
                schemes.add(ResolvedPattern(pattern, fileResolver).scheme)
            }
        }
    }

    private class MetaDataProvider : IvyArtifactRepositoryMetaDataProvider {
        var dynamicResolve: Boolean = false

        override fun isDynamicMode(): Boolean {
            return dynamicResolve
        }

        override fun setDynamicMode(mode: Boolean) {
            this.dynamicResolve = mode
        }
    }

    private inner class IvyMetadataSources : IvyArtifactRepository.MetadataSources {
        var gradleMetadata: Boolean = false
        var ivyDescriptor: Boolean = false
        var artifact: Boolean = false
        var ignoreGradleMetadataRedirection: Boolean = false

        fun setDefaults() {
            ivyDescriptor()
            ignoreGradleMetadataRedirection = false
        }

        fun reset() {
            gradleMetadata = false
            ivyDescriptor = false
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
            if (ivyDescriptor) {
                list.add("ivyDescriptor")
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

        override fun ivyDescriptor() {
            invalidateDescriptor()
            ivyDescriptor = true
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

        override fun isIvyDescriptorEnabled(): Boolean {
            return ivyDescriptor
        }

        override fun isArtifactEnabled(): Boolean {
            return artifact
        }

        override fun isIgnoreGradleMetadataRedirectionEnabled(): Boolean {
            return ignoreGradleMetadataRedirection
        }
    }
}
