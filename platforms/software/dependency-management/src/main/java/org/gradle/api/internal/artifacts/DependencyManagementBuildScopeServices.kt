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
package org.gradle.api.internal.artifacts

import com.google.common.collect.Sets
import org.gradle.StartParameter
import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.artifacts.capability.CapabilitySelectorSerializer
import org.gradle.api.internal.artifacts.dsl.CapabilityNotationParser
import org.gradle.api.internal.artifacts.dsl.CapabilityNotationParserFactory
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyConstraintFactoryInternal
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactoryInternal
import org.gradle.api.internal.artifacts.dsl.dependencies.UnknownProjectFinder
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheLockingAccessCoordinator
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheMetadata
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCachesProvider
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ExternalModuleComponentResolverFactory
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResolverProviderFactories
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.StartParameterResolutionOverride
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.CachingVersionSelectorScheme
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionSelectorScheme
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionComparator
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.DependencyVerificationOverride
import org.gradle.api.internal.artifacts.ivyservice.modulecache.FileStoreAndIndexProvider
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleComponentResolveMetadataSerializer
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleMetadataSerializer
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleSourcesSerializer
import org.gradle.api.internal.artifacts.ivyservice.modulecache.SuppliedComponentMetadataSerializer
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSetResolver
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.AttributeContainerSerializer
import org.gradle.api.internal.artifacts.mvnsettings.DefaultLocalMavenRepositoryLocator
import org.gradle.api.internal.artifacts.mvnsettings.DefaultMavenFileLocations
import org.gradle.api.internal.artifacts.mvnsettings.DefaultMavenSettingsProvider
import org.gradle.api.internal.artifacts.mvnsettings.LocalMavenRepositoryLocator
import org.gradle.api.internal.artifacts.mvnsettings.MavenSettingsProvider
import org.gradle.api.internal.artifacts.repositories.metadata.IvyMutableModuleMetadataFactory
import org.gradle.api.internal.artifacts.repositories.metadata.MavenMutableModuleMetadataFactory
import org.gradle.api.internal.artifacts.repositories.resolver.DefaultExternalResourceAccessor
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceAccessor
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory
import org.gradle.api.internal.artifacts.transform.TransformExecutionListener
import org.gradle.api.internal.artifacts.transform.TransformStepNodeDependencyResolver
import org.gradle.api.internal.artifacts.verification.signatures.DefaultSignatureVerificationServiceFactory
import org.gradle.api.internal.artifacts.verification.signatures.SignatureVerificationServiceFactory
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.catalog.DefaultDependenciesAccessors
import org.gradle.api.internal.catalog.DependenciesAccessorsWorkspaceProvider
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.temp.TemporaryFileProvider
import org.gradle.api.internal.model.NamedObjectInstantiator
import org.gradle.api.internal.notations.DependencyConstraintNotationParser
import org.gradle.api.internal.notations.DependencyNotationParser
import org.gradle.api.internal.notations.ProjectDependencyFactory
import org.gradle.api.internal.project.ProjectStateLookup
import org.gradle.api.internal.properties.GradleProperties
import org.gradle.api.internal.resources.ApiTextResourceAdapter
import org.gradle.api.internal.runtimeshaded.RuntimeShadedJarFactory
import org.gradle.api.model.ObjectFactory
import org.gradle.api.problems.Problems
import org.gradle.authentication.Authentication
import org.gradle.cache.internal.CleaningInMemoryCacheDecoratorFactory
import org.gradle.cache.internal.InMemoryCacheController
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory
import org.gradle.cache.internal.ProducerGuard
import org.gradle.cache.scopes.BuildScopedCacheBuilderFactory
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory
import org.gradle.initialization.DependenciesAccessors
import org.gradle.internal.build.BuildModelLifecycleListener
import org.gradle.internal.buildoption.FeatureFlags
import org.gradle.internal.code.UserCodeApplicationContext
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.execution.ExecutionEngine
import org.gradle.internal.execution.InputFingerprinter
import org.gradle.internal.file.RelativeFilePathResolver
import org.gradle.internal.hash.ChecksumService
import org.gradle.internal.hash.FileHasher
import org.gradle.internal.management.DefaultDependencyResolutionManagement
import org.gradle.internal.management.DependencyResolutionManagementInternal
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.resolve.caching.ComponentMetadataRuleExecutor
import org.gradle.internal.resolve.caching.ComponentMetadataSupplierRuleExecutor
import org.gradle.internal.resolve.caching.DesugaringAttributeContainerSerializer
import org.gradle.internal.resource.ExternalResourceName
import org.gradle.internal.resource.TextUriResourceLoader
import org.gradle.internal.resource.connector.ResourceConnectorFactory
import org.gradle.internal.resource.local.FileResourceConnector
import org.gradle.internal.resource.local.FileResourceListener
import org.gradle.internal.resource.local.FileResourceRepository
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder
import org.gradle.internal.resource.local.ivy.LocallyAvailableResourceFinderFactory
import org.gradle.internal.resource.transfer.CachingTextUriResourceLoader
import org.gradle.internal.service.Provides
import org.gradle.internal.service.ServiceRegistration
import org.gradle.internal.service.ServiceRegistrationProvider
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.snapshot.ValueSnapshotter
import org.gradle.internal.verifier.HttpRedirectVerifier
import org.gradle.util.internal.BuildCommencedTimeProvider
import org.gradle.util.internal.SimpleMapInterner
import java.util.function.BiFunction
import java.util.function.Predicate

/**
 * The set of dependency management services that are created per build in the tree.
 */
internal class DependencyManagementBuildScopeServices : ServiceRegistrationProvider {
    fun configure(registration: ServiceRegistration) {
        registration.add(TransformStepNodeDependencyResolver::class.java)
        registration.add<FileResourceConnector?>(FileResourceRepository::class.java, FileResourceConnector::class.java)
        registration.add(ResolvedArtifactSetResolver::class.java)
        registration.add(ExternalModuleComponentResolverFactory::class.java)
        registration.add(ResolverProviderFactories::class.java)
        registration.add(DependencyManagementManagedTypesFactory::class.java)
        registration.add(RuntimeShadedJarFactory::class.java)
    }

    @Provides
    fun createSharedDependencyResolutionServices(
        instantiator: Instantiator,
        context: UserCodeApplicationContext,
        dependencyManagementServices: DependencyManagementServices?,
        objects: ObjectFactory?,
        collectionCallbackActionDecorator: CollectionCallbackActionDecorator?
    ): DependencyResolutionManagementInternal {
        return instantiator.newInstance<DefaultDependencyResolutionManagement>(
            DefaultDependencyResolutionManagement::class.java,
            context,
            dependencyManagementServices,
            objects,
            collectionCallbackActionDecorator
        )
    }

    @Provides
    fun createDependencyManagementServices(parent: ServiceRegistry?): DependencyManagementServices {
        return DefaultDependencyManagementServices(parent)
    }

    @Provides
    fun createVersionComparator(): VersionComparator {
        return DefaultVersionComparator()
    }

    @Provides
    fun createCapabilityNotationParser(): CapabilityNotationParser {
        return CapabilityNotationParserFactory(false).create()
    }

    @Provides
    fun createProjectDependencyFactory(
        instantiator: Instantiator?,
        capabilityNotationParser: CapabilityNotationParser?,
        objectFactory: ObjectFactory?,
        attributesFactory: AttributesFactory?,
        projectStateLookup: ProjectStateLookup?
    ): DefaultProjectDependencyFactory {
        return DefaultProjectDependencyFactory(
            instantiator,
            capabilityNotationParser,
            objectFactory,
            attributesFactory,
            projectStateLookup,
            UnknownProjectFinder("Project dependencies cannot be declared here.")
        )
    }

    @Provides
    fun createDependencyFactory(
        instantiator: Instantiator?,
        factory: DefaultProjectDependencyFactory,
        classPathRegistry: ClassPathRegistry?,
        fileCollectionFactory: FileCollectionFactory?,
        runtimeShadedJarFactory: RuntimeShadedJarFactory?,
        attributesFactory: AttributesFactory?,
        stringInterner: SimpleMapInterner?,
        capabilityNotationParser: CapabilityNotationParser?,
        objectFactory: ObjectFactory?,
        problems: Problems?
    ): DependencyFactoryInternal {
        val projectDependencyFactory = ProjectDependencyFactory(factory)

        val dependencyNotationParser: DependencyNotationParser? = DependencyNotationParser.create(
            instantiator!!,
            factory,
            classPathRegistry,
            fileCollectionFactory,
            runtimeShadedJarFactory,
            stringInterner,
            problems
        )

        return DefaultDependencyFactory(
            instantiator,
            dependencyNotationParser,
            capabilityNotationParser,
            objectFactory,
            projectDependencyFactory,
            attributesFactory,
            null
        )
    }

    @Provides
    fun createDependencyConstraintFactory(
        instantiator: Instantiator?,
        objectFactory: ObjectFactory?,
        factory: DefaultProjectDependencyFactory?,
        attributesFactory: AttributesFactory?,
        stringInterner: SimpleMapInterner?,
        problems: Problems?
    ): DependencyConstraintFactoryInternal {
        return DefaultDependencyConstraintFactory(
            objectFactory,
            DependencyConstraintNotationParser.parser(instantiator!!, factory, stringInterner, attributesFactory!!, problems),
            attributesFactory
        )
    }

    @Provides
    fun createModuleExclusions(): ModuleExclusions {
        return ModuleExclusions()
    }

    @Provides
    fun createTextUrlResourceLoaderFactory(
        fileStoreAndIndexProvider: FileStoreAndIndexProvider,
        repositoryTransportFactory: RepositoryTransportFactory,
        resolver: RelativeFilePathResolver
    ): TextUriResourceLoader.Factory {
        val schemas = Sets.newHashSet<String?>("https", "http")
        return TextUriResourceLoader.Factory { redirectVerifier: HttpRedirectVerifier? ->
            val transport = repositoryTransportFactory.createTransport(schemas, "resources http", mutableListOf<Authentication?>(), redirectVerifier)
            val externalResourceAccessor: ExternalResourceAccessor = DefaultExternalResourceAccessor(fileStoreAndIndexProvider.getExternalResourceFileStore(), transport.resourceAccessor)
            CachingTextUriResourceLoader(externalResourceAccessor, schemas, resolver)
        }
    }

    @Provides
    protected fun createTextResourceAdapterFactory(textUriResourceLoaderFactory: TextUriResourceLoader.Factory, tempFileProvider: TemporaryFileProvider?): ApiTextResourceAdapter.Factory {
        return ApiTextResourceAdapter.Factory(textUriResourceLoaderFactory, tempFileProvider)
    }

    @Provides
    fun createMavenSettingsProvider(): MavenSettingsProvider {
        return DefaultMavenSettingsProvider(DefaultMavenFileLocations())
    }

    @Provides
    fun createLocalMavenRepositoryLocator(mavenSettingsProvider: MavenSettingsProvider?): LocalMavenRepositoryLocator {
        return DefaultLocalMavenRepositoryLocator(mavenSettingsProvider)
    }

    @Provides
    fun createArtifactRevisionIdLocallyAvailableResourceFinder(
        artifactCaches: ArtifactCachesProvider,
        localMavenRepositoryLocator: LocalMavenRepositoryLocator,
        fileStoreAndIndexProvider: FileStoreAndIndexProvider,
        checksumService: ChecksumService?
    ): LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata?>? {
        val finderFactory = LocallyAvailableResourceFinderFactory(
            artifactCaches,
            localMavenRepositoryLocator,
            fileStoreAndIndexProvider.getArtifactIdentifierFileStore(), checksumService
        )
        return finderFactory.create()
    }

    @Provides
    fun createRepositoryTransportFactory(
        temporaryFileProvider: TemporaryFileProvider?,
        fileStoreAndIndexProvider: FileStoreAndIndexProvider,
        buildCommencedTimeProvider: BuildCommencedTimeProvider?,
        artifactCachesProvider: ArtifactCachesProvider,
        resourceConnectorFactories: MutableList<ResourceConnectorFactory?>?,
        buildOperationRunner: BuildOperationRunner?,
        producerGuard: ProducerGuard<ExternalResourceName?>?,
        fileResourceRepository: FileResourceRepository?,
        checksumService: ChecksumService?,
        startParameterResolutionOverride: StartParameterResolutionOverride?
    ): RepositoryTransportFactory {
        return artifactCachesProvider.withWritableCache<RepositoryTransportFactory>(BiFunction { md: ArtifactCacheMetadata?, manager: ArtifactCacheLockingAccessCoordinator? ->
            RepositoryTransportFactory(
                resourceConnectorFactories,
                temporaryFileProvider,
                fileStoreAndIndexProvider.getExternalResourceIndex(),
                buildCommencedTimeProvider,
                manager,
                buildOperationRunner,
                startParameterResolutionOverride,
                producerGuard,
                fileResourceRepository,
                checksumService
            )
        })
    }

    @Provides
    fun createDependencyVerificationOverride(
        startParameterResolutionOverride: StartParameterResolutionOverride,
        buildOperationExecutor: BuildOperationExecutor,
        checksumService: ChecksumService,
        signatureVerificationServiceFactory: SignatureVerificationServiceFactory,
        documentationRegistry: DocumentationRegistry,
        listenerManager: ListenerManager,
        timeProvider: BuildCommencedTimeProvider,
        serviceRegistry: ServiceRegistry,
        fileResourceListener: FileResourceListener
    ): DependencyVerificationOverride {
        val override = startParameterResolutionOverride.dependencyVerificationOverride(
            buildOperationExecutor,
            checksumService,
            signatureVerificationServiceFactory,
            documentationRegistry,
            timeProvider,
            org.gradle.internal.Factory { serviceRegistry.get<GradleProperties?>(GradleProperties::class.java) },
            fileResourceListener
        )
        registerBuildFinishedHooks(listenerManager, override)
        return override
    }

    @Provides
    fun createVersionSelectorScheme(versionComparator: VersionComparator?, versionParser: VersionParser?): VersionSelectorScheme {
        val delegate = DefaultVersionSelectorScheme(versionComparator, versionParser)
        return CachingVersionSelectorScheme(delegate)
    }

    @Provides
    fun createModuleComponentResolveMetadataSerializer(
        attributesFactory: AttributesFactory,
        mavenMetadataFactory: MavenMutableModuleMetadataFactory?,
        ivyMetadataFactory: IvyMutableModuleMetadataFactory?,
        moduleIdentifierFactory: ImmutableModuleIdentifierFactory?,
        instantiator: NamedObjectInstantiator?,
        moduleSourcesSerializer: ModuleSourcesSerializer?
    ): ModuleComponentResolveMetadataSerializer {
        val attributeContainerSerializer = DesugaringAttributeContainerSerializer(attributesFactory, instantiator)
        val capabilitySelectorSerializer = CapabilitySelectorSerializer()
        return ModuleComponentResolveMetadataSerializer(
            ModuleMetadataSerializer(
                attributeContainerSerializer,
                capabilitySelectorSerializer,
                mavenMetadataFactory,
                ivyMetadataFactory,
                moduleSourcesSerializer
            ), attributeContainerSerializer, capabilitySelectorSerializer, moduleIdentifierFactory
        )
    }

    @Provides
    fun createSuppliedComponentMetadataSerializer(
        moduleIdentifierFactory: ImmutableModuleIdentifierFactory,
        attributeContainerSerializer: AttributeContainerSerializer?
    ): SuppliedComponentMetadataSerializer {
        val moduleVersionIdentifierSerializer = ModuleVersionIdentifierSerializer(moduleIdentifierFactory)
        return SuppliedComponentMetadataSerializer(moduleVersionIdentifierSerializer, attributeContainerSerializer)
    }

    @Provides
    fun createComponentMetadataRuleExecutor(
        valueSnapshotter: ValueSnapshotter?,
        cacheBuilderFactory: GlobalScopedCacheBuilderFactory,
        cacheDecoratorFactory: InMemoryCacheDecoratorFactory?,
        timeProvider: BuildCommencedTimeProvider,
        serializer: ModuleComponentResolveMetadataSerializer?
    ): ComponentMetadataRuleExecutor {
        return ComponentMetadataRuleExecutor(cacheBuilderFactory, cacheDecoratorFactory, valueSnapshotter, timeProvider, serializer)
    }

    @Provides
    fun createComponentMetadataSupplierRuleExecutor(
        snapshotter: ValueSnapshotter?,
        cacheBuilderFactory: GlobalScopedCacheBuilderFactory,
        cacheDecoratorFactory: InMemoryCacheDecoratorFactory?,
        timeProvider: BuildCommencedTimeProvider,
        suppliedComponentMetadataSerializer: SuppliedComponentMetadataSerializer?,
        listenerManager: ListenerManager
    ): ComponentMetadataSupplierRuleExecutor {
        if (cacheDecoratorFactory is CleaningInMemoryCacheDecoratorFactory) {
            listenerManager.addListener(object : BuildModelLifecycleListener {
                override fun beforeModelDiscarded(model: GradleInternal, buildFailed: Boolean) {
                    cacheDecoratorFactory.clearCaches(Predicate { obj: InMemoryCacheController? -> ComponentMetadataRuleExecutor.Companion.isMetadataRuleExecutorCache() })
                }
            })
        }
        return ComponentMetadataSupplierRuleExecutor(cacheBuilderFactory, cacheDecoratorFactory, snapshotter, timeProvider, suppliedComponentMetadataSerializer)
    }

    @Provides
    fun createSignatureVerificationServiceFactory(
        cacheBuilderFactory: GlobalScopedCacheBuilderFactory?,
        decoratorFactory: InMemoryCacheDecoratorFactory?,
        transportFactory: RepositoryTransportFactory?,
        buildOperationRunner: BuildOperationRunner?,
        timeProvider: BuildCommencedTimeProvider?,
        buildScopedCacheBuilderFactory: BuildScopedCacheBuilderFactory?,
        fileHasher: FileHasher?,
        startParameter: StartParameter,
        fileResourceListener: FileResourceListener?
    ): SignatureVerificationServiceFactory {
        return DefaultSignatureVerificationServiceFactory(
            transportFactory,
            cacheBuilderFactory,
            decoratorFactory,
            buildOperationRunner,
            fileHasher,
            buildScopedCacheBuilderFactory,
            timeProvider,
            startParameter.isRefreshKeys(),
            fileResourceListener
        )
    }

    @Provides
    fun createDependenciesAccessorGenerator(
        objectFactory: ObjectFactory,
        registry: ClassPathRegistry,
        workspace: DependenciesAccessorsWorkspaceProvider?,
        factory: DefaultProjectDependencyFactory?,
        executionEngine: ExecutionEngine?,
        featureFlags: FeatureFlags?,
        fileCollectionFactory: FileCollectionFactory?,
        attributesFactory: AttributesFactory?,
        capabilityNotationParser: CapabilityNotationParser?,
        inputFingerprinter: InputFingerprinter?
    ): DependenciesAccessors {
        return objectFactory.newInstance<DefaultDependenciesAccessors>(
            DefaultDependenciesAccessors::class.java,
            registry,
            workspace!!,
            factory!!,
            featureFlags!!,
            executionEngine!!,
            fileCollectionFactory!!,
            inputFingerprinter!!,
            attributesFactory!!,
            capabilityNotationParser!!
        )
    }

    @Provides
    fun createTransformExecutionListener(listenerManager: ListenerManager): TransformExecutionListener? {
        return listenerManager.getBroadcaster<TransformExecutionListener?>(TransformExecutionListener::class.java)
    }

    companion object {
        private fun registerBuildFinishedHooks(listenerManager: ListenerManager, dependencyVerificationOverride: DependencyVerificationOverride) {
            listenerManager.addListener(object : BuildModelLifecycleListener {
                override fun beforeModelDiscarded(model: GradleInternal, buildFailed: Boolean) {
                    dependencyVerificationOverride.buildFinished(model)
                }
            })
        }
    }
}
