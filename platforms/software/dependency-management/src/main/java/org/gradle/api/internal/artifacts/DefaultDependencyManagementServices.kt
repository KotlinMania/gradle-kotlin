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
package org.gradle.api.internal.artifacts

import org.gradle.StartParameter
import org.gradle.api.Action
import org.gradle.api.Describable
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.Transformer
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.dsl.ArtifactHandler
import org.gradle.api.artifacts.dsl.ComponentMetadataHandler
import org.gradle.api.artifacts.dsl.ComponentModuleMetadataHandler
import org.gradle.api.artifacts.dsl.DependencyConstraintHandler
import org.gradle.api.artifacts.dsl.DependencyFactory
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.dsl.DependencyLockingHandler
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.gradle.api.attributes.AttributesSchema
import org.gradle.api.file.Directory
import org.gradle.api.file.ProjectLayout
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.ConfigurationServicesBundle
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.DomainObjectContext
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.artifacts.configurations.ConfigurationContainerInternal
import org.gradle.api.internal.artifacts.configurations.DefaultConfigurationContainer
import org.gradle.api.internal.artifacts.configurations.DefaultConfigurationFactory
import org.gradle.api.internal.artifacts.configurations.DefaultConfigurationServicesBundle
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyFactory
import org.gradle.api.internal.artifacts.dsl.ComponentMetadataHandlerInternal
import org.gradle.api.internal.artifacts.dsl.DefaultArtifactHandler
import org.gradle.api.internal.artifacts.dsl.DefaultComponentMetadataHandler
import org.gradle.api.internal.artifacts.dsl.DefaultComponentModuleMetadataHandler
import org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandler
import org.gradle.api.internal.artifacts.dsl.PublishArtifactNotationParserFactory
import org.gradle.api.internal.artifacts.dsl.dependencies.DefaultDependencyConstraintHandler
import org.gradle.api.internal.artifacts.dsl.dependencies.DefaultDependencyHandler
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyConstraintFactoryInternal
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactoryInternal
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingProvider
import org.gradle.api.internal.artifacts.dsl.dependencies.GradlePluginVariantsSupport
import org.gradle.api.internal.artifacts.dsl.dependencies.PlatformSupport
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder
import org.gradle.api.internal.artifacts.dsl.dependencies.UnknownProjectFinder
import org.gradle.api.internal.artifacts.ivyservice.DefaultConfigurationResolver
import org.gradle.api.internal.artifacts.ivyservice.IvyContextManager
import org.gradle.api.internal.artifacts.ivyservice.ResolutionExecutor
import org.gradle.api.internal.artifacts.ivyservice.ShortCircuitingResolutionExecutor
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.GradleModuleMetadataParser
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.GradlePomModuleDescriptorParser
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme
import org.gradle.api.internal.artifacts.ivyservice.modulecache.FileStoreAndIndexProvider
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.DefaultLocalComponentRegistry
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentRegistry
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyResolver
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.DependencyGraphResolver
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.DependencyGraphBuilder
import org.gradle.api.internal.artifacts.mvnsettings.LocalMavenRepositoryLocator
import org.gradle.api.internal.artifacts.query.ArtifactResolutionQueryFactory
import org.gradle.api.internal.artifacts.query.DefaultArtifactResolutionQueryFactory
import org.gradle.api.internal.artifacts.repositories.DefaultBaseRepositoryFactory
import org.gradle.api.internal.artifacts.repositories.DefaultUrlArtifactRepository
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository
import org.gradle.api.internal.artifacts.repositories.metadata.IvyMutableModuleMetadataFactory
import org.gradle.api.internal.artifacts.repositories.metadata.MavenMutableModuleMetadataFactory
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory
import org.gradle.api.internal.artifacts.transform.ConsumerProvidedVariantFinder
import org.gradle.api.internal.artifacts.transform.DefaultTransformInvocationFactory
import org.gradle.api.internal.artifacts.transform.DefaultTransformRegistrationFactory
import org.gradle.api.internal.artifacts.transform.DefaultTransformedVariantFactory
import org.gradle.api.internal.artifacts.transform.DefaultVariantTransformRegistry
import org.gradle.api.internal.artifacts.transform.ImmutableTransformWorkspaceServices
import org.gradle.api.internal.artifacts.transform.MutableTransformWorkspaceServices
import org.gradle.api.internal.artifacts.transform.TransformActionScheme
import org.gradle.api.internal.artifacts.transform.TransformExecutionListener
import org.gradle.api.internal.artifacts.transform.TransformExecutionResult
import org.gradle.api.internal.artifacts.transform.TransformInvocationFactory
import org.gradle.api.internal.artifacts.transform.TransformParameterScheme
import org.gradle.api.internal.artifacts.transform.TransformRegistrationFactory
import org.gradle.api.internal.artifacts.transform.TransformedVariantFactory
import org.gradle.api.internal.artifacts.type.ArtifactTypeRegistry
import org.gradle.api.internal.attributes.AttributeDescriberRegistry
import org.gradle.api.internal.attributes.AttributeDesugaring
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.attributes.AttributesSchemaInternal
import org.gradle.api.internal.attributes.DefaultAttributesSchema
import org.gradle.api.internal.collections.DomainObjectCollectionFactory
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.FileLookup
import org.gradle.api.internal.file.FilePropertyFactory
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.model.NamedObjectInstantiator
import org.gradle.api.internal.project.ProjectStateRegistry
import org.gradle.api.internal.provider.PropertyFactory
import org.gradle.api.internal.tasks.TaskDependencyFactory
import org.gradle.api.model.ObjectFactory
import org.gradle.api.problems.Problems
import org.gradle.api.problems.internal.ProblemsInternal
import org.gradle.api.provider.ProviderFactory
import org.gradle.cache.Cache
import org.gradle.cache.ManualEvictionInMemoryCache
import org.gradle.internal.authentication.AuthenticationSchemeRegistry
import org.gradle.internal.build.BuildModelLifecycleListener
import org.gradle.internal.buildoption.InternalOptions
import org.gradle.internal.component.external.model.JavaEcosystemVariantDerivationStrategy
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata
import org.gradle.internal.component.model.GraphVariantSelector
import org.gradle.internal.component.resolution.failure.ResolutionFailureHandler
import org.gradle.internal.component.resolution.failure.transform.TransformedVariantConverter
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.execution.DeferredResult
import org.gradle.internal.execution.ExecutionEngine
import org.gradle.internal.execution.Identity
import org.gradle.internal.execution.InputFingerprinter
import org.gradle.internal.execution.history.ExecutionHistoryStore
import org.gradle.internal.execution.workspace.MutableWorkspaceProvider
import org.gradle.internal.execution.workspace.impl.NonLockingMutableWorkspaceProvider
import org.gradle.internal.hash.ChecksumService
import org.gradle.internal.hash.ClassLoaderHierarchyHasher
import org.gradle.internal.instantiation.InstantiatorFactory
import org.gradle.internal.isolation.IsolatableFactory
import org.gradle.internal.locking.DefaultDependencyLockingHandler
import org.gradle.internal.locking.DefaultDependencyLockingProvider
import org.gradle.internal.locking.NoOpDependencyLockingProvider.Companion.instance
import org.gradle.internal.management.DependencyResolutionManagementInternal
import org.gradle.internal.model.CalculatedValueContainerFactory
import org.gradle.internal.operations.BuildOperationProgressEventEmitter
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.resolve.caching.ComponentMetadataRuleExecutor
import org.gradle.internal.resource.local.FileResourceListener
import org.gradle.internal.resource.local.FileResourceRepository
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder
import org.gradle.internal.serialize.DefaultSerializerRegistry.build
import org.gradle.internal.service.Provides
import org.gradle.internal.service.ServiceRegistration
import org.gradle.internal.service.ServiceRegistration.add
import org.gradle.internal.service.ServiceRegistrationProvider
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.service.ServiceRegistryBuilder.Companion.builder
import org.gradle.internal.service.ServiceRegistryBuilder.build
import org.gradle.internal.service.ServiceRegistryBuilder.provider
import org.gradle.util.internal.SimpleMapInterner
import java.io.File
import java.util.function.Supplier
import java.util.stream.Collectors

class DefaultDependencyManagementServices(private val parent: ServiceRegistry) : DependencyManagementServices {
    override fun newDetachedResolver(owner: DomainObjectContext): DependencyResolutionServices {
        return newDetachedResolver(
            parent.get<FileResolver?>(FileResolver::class.java),
            parent.get<FileCollectionFactory?>(FileCollectionFactory::class.java),
            owner
        )
    }

    override fun newDetachedResolver(
        resolver: FileResolver?,
        fileCollectionFactory: FileCollectionFactory?,
        owner: DomainObjectContext
    ): DependencyResolutionServices {
        val services: ServiceRegistry = builder()
            .parent(parent)
            .provider(org.gradle.internal.service.ServiceRegistrationAction { registration: ServiceRegistration? ->
                registration.this!!.add<FileResolver?>(FileResolver::class.java, resolver)
                registration.this!!.add<FileCollectionFactory?>(FileCollectionFactory::class.java, fileCollectionFactory)
                registration!!.add<DependencyMetaDataProvider?>(DependencyMetaDataProvider::class.java, DependencyMetaDataProvider? { AnonymousModule() })
                registration.this!!.add<ProjectFinder?>(ProjectFinder::class.java, UnknownProjectFinder("Project dependencies cannot be declared here."))
                registration.this!!.add<DomainObjectContext?>(DomainObjectContext::class.java, owner)
            })
            .provider(TransformGradleUserHomeServices())
            .provider(DependencyResolutionScopeServices(owner))
            .build()

        val dms = services.get<DependencyResolutionServices?>(DependencyResolutionServices::class.java)

        // We restrict this so that detached resolvers only represent adhoc root components that do not expose variants.
        dms!!.getConfigurationContainer().configureEach(Action { configuration: Configuration? ->
            if (configuration!!.isCanBeConsumed()) {
                throw InvalidUserCodeException("Cannot create consumable configurations in detached resolvers")
            }
        })

        return dms
    }

    override fun addDslServices(registration: ServiceRegistration, domainObjectContext: DomainObjectContext) {
        registration.addProvider(DependencyResolutionScopeServices(domainObjectContext))
    }

    private class TransformGradleUserHomeServices : ServiceRegistrationProvider {
        @Provides
        fun createTransformExecutionListener(): TransformExecutionListener {
            return object : TransformExecutionListener {
                override fun beforeTransformExecution(transform: Describable, subject: Describable) {
                }

                override fun afterTransformExecution(transform: Describable, subject: Describable) {
                }
            }
        }
    }

    private class DependencyResolutionScopeServices(private val domainObjectContext: DomainObjectContext) : ServiceRegistrationProvider {
        @Suppress("unused")
        fun configure(registration: ServiceRegistration) {
            registration.add<DefaultTransformedVariantFactory?>(TransformedVariantFactory::class.java, DefaultTransformedVariantFactory::class.java)
            registration.add(ResolveExceptionMapper::class.java)
            registration.add(ResolutionStrategyFactory::class.java)
            registration.add<DefaultLocalComponentRegistry?>(LocalComponentRegistry::class.java, DefaultLocalComponentRegistry::class.java)
            registration.add(ProjectDependencyResolver::class.java)
            registration.add(ConsumerProvidedVariantFinder::class.java)
            registration.add(DefaultConfigurationFactory::class.java)
            registration.add<DefaultComponentSelectorConverter?>(ComponentSelectorConverter::class.java, DefaultComponentSelectorConverter::class.java)
            registration.add<DefaultArtifactResolutionQueryFactory?>(ArtifactResolutionQueryFactory::class.java, DefaultArtifactResolutionQueryFactory::class.java)
            registration.add(DependencyGraphResolver::class.java)
            registration.add(DependencyGraphBuilder::class.java)
            registration.add(AttributeDescriberRegistry::class.java)
            registration.add(GraphVariantSelector::class.java)
            registration.add(TransformedVariantConverter::class.java)
            registration.add(ResolutionExecutor::class.java)
            registration.add(ShortCircuitingResolutionExecutor::class.java)
            registration.add<DefaultConfigurationResolver.Factory?>(ConfigurationResolver.Factory::class.java, DefaultConfigurationResolver.Factory::class.java)
            registration.add(ArtifactTypeRegistry::class.java)
            registration.add(GlobalDependencyResolutionRules::class.java)
            registration.add(PublishArtifactNotationParserFactory::class.java)
        }

        @Provides
        fun createConfigurationAttributesSchema(instantiatorFactory: InstantiatorFactory, isolatableFactory: IsolatableFactory?, platformSupport: PlatformSupport): AttributesSchemaInternal {
            val attributesSchema = instantiatorFactory.decorateLenient().newInstance<DefaultAttributesSchema>(DefaultAttributesSchema::class.java, instantiatorFactory, isolatableFactory)
            platformSupport.configureSchema(attributesSchema)
            GradlePluginVariantsSupport.configureSchema(attributesSchema)
            return attributesSchema
        }

        @Provides
        fun createTransformWorkspaceServices(projectLayout: ProjectLayout, executionHistoryStore: ExecutionHistoryStore): MutableTransformWorkspaceServices {
            val baseDirectory = Supplier { projectLayout.getBuildDirectory().dir(".transforms").map<File?>(Transformer { obj: Directory? -> obj!!.getAsFile() }).get() }
            val identityCache: Cache<Identity?, DeferredResult<TransformExecutionResult.TransformWorkspaceResult?>?> =
                ManualEvictionInMemoryCache<Identity?, DeferredResult<TransformExecutionResult.TransformWorkspaceResult?>?>()
            return object : MutableTransformWorkspaceServices {
                override fun getWorkspaceProvider(): MutableWorkspaceProvider {
                    return NonLockingMutableWorkspaceProvider(baseDirectory.get()!!)
                }

                override fun getExecutionHistoryStore(): ExecutionHistoryStore {
                    return executionHistoryStore
                }

                override fun getIdentityCache(): Cache<Identity?, DeferredResult<TransformExecutionResult.TransformWorkspaceResult?>?> {
                    return identityCache
                }

                override fun getReservedFileSystemLocation(): Supplier<File?> {
                    return baseDirectory
                }
            }
        }

        @Provides
        fun createTransformInvocationFactory(
            executionEngine: ExecutionEngine,
            internalOptions: InternalOptions,
            transformWorkspaceServices: ImmutableTransformWorkspaceServices,
            transformExecutionListener: TransformExecutionListener,
            fileCollectionFactory: FileCollectionFactory,
            projectStateRegistry: ProjectStateRegistry,
            buildOperationRunner: BuildOperationRunner,
            progressEventEmitter: BuildOperationProgressEventEmitter
        ): TransformInvocationFactory {
            return DefaultTransformInvocationFactory(
                executionEngine,
                internalOptions,
                transformExecutionListener,
                transformWorkspaceServices,
                fileCollectionFactory,
                projectStateRegistry,
                buildOperationRunner,
                progressEventEmitter
            )
        }

        @Provides
        fun createTransformRegistrationFactory(
            buildOperationRunner: BuildOperationRunner,
            isolatableFactory: IsolatableFactory,
            classLoaderHierarchyHasher: ClassLoaderHierarchyHasher,
            transformInvocationFactory: TransformInvocationFactory,
            domainObjectContext: DomainObjectContext,
            parameterScheme: TransformParameterScheme,
            actionScheme: TransformActionScheme,
            inputFingerprinter: InputFingerprinter,
            calculatedValueContainerFactory: CalculatedValueContainerFactory,
            fileCollectionFactory: FileCollectionFactory,
            fileLookup: FileLookup,
            internalServices: ServiceRegistry
        ): TransformRegistrationFactory {
            return DefaultTransformRegistrationFactory(
                buildOperationRunner,
                isolatableFactory,
                classLoaderHierarchyHasher,
                transformInvocationFactory,
                fileCollectionFactory,
                fileLookup,
                inputFingerprinter,
                calculatedValueContainerFactory,
                domainObjectContext,
                parameterScheme,
                actionScheme,
                internalServices
            )
        }

        @Provides
        fun createVariantTransformRegistry(
            instantiatorFactory: InstantiatorFactory,
            attributesFactory: AttributesFactory,
            services: ServiceRegistry,
            transformRegistrationFactory: TransformRegistrationFactory,
            parameterScheme: TransformParameterScheme,
            documentationRegistry: DocumentationRegistry
        ): VariantTransformRegistry {
            return DefaultVariantTransformRegistry(instantiatorFactory, attributesFactory, services, transformRegistrationFactory, parameterScheme.getInstantiationScheme(), documentationRegistry)
        }

        @Provides
        fun createDefaultUrlArtifactRepositoryFactory(fileResolver: FileResolver): DefaultUrlArtifactRepository.Factory {
            return DefaultUrlArtifactRepository.Factory(fileResolver)
        }

        @Provides
        fun createBaseRepositoryFactory(
            localMavenRepositoryLocator: LocalMavenRepositoryLocator,
            fileResolver: FileResolver,
            fileCollectionFactory: FileCollectionFactory,
            repositoryTransportFactory: RepositoryTransportFactory,
            locallyAvailableResourceFinder: LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata?>,
            fileStoreAndIndexProvider: FileStoreAndIndexProvider,
            versionSelectorScheme: VersionSelectorScheme?,
            authenticationSchemeRegistry: AuthenticationSchemeRegistry,
            ivyContextManager: IvyContextManager,
            attributesFactory: AttributesFactory?,
            moduleIdentifierFactory: ImmutableModuleIdentifierFactory,
            instantiatorFactory: InstantiatorFactory,
            fileResourceRepository: FileResourceRepository,
            metadataFactory: MavenMutableModuleMetadataFactory,
            ivyMetadataFactory: IvyMutableModuleMetadataFactory,
            isolatableFactory: IsolatableFactory,
            objectFactory: ObjectFactory,
            callbackDecorator: CollectionCallbackActionDecorator,
            instantiator: NamedObjectInstantiator?,
            urlArtifactRepositoryFactory: DefaultUrlArtifactRepository.Factory,
            checksumService: ChecksumService,
            providerFactory: ProviderFactory,
            versionParser: VersionParser
        ): BaseRepositoryFactory {
            return DefaultBaseRepositoryFactory(
                localMavenRepositoryLocator,
                fileResolver,
                fileCollectionFactory,
                repositoryTransportFactory,
                locallyAvailableResourceFinder,
                fileStoreAndIndexProvider.getArtifactIdentifierFileStore(),
                fileStoreAndIndexProvider.getExternalResourceFileStore(),
                GradlePomModuleDescriptorParser(versionSelectorScheme, moduleIdentifierFactory, fileResourceRepository, metadataFactory),
                GradleModuleMetadataParser(attributesFactory, moduleIdentifierFactory, instantiator),
                authenticationSchemeRegistry,
                ivyContextManager,
                moduleIdentifierFactory,
                instantiatorFactory,
                fileResourceRepository,
                metadataFactory,
                ivyMetadataFactory,
                isolatableFactory,
                objectFactory,
                callbackDecorator,
                urlArtifactRepositoryFactory,
                checksumService,
                providerFactory,
                versionParser
            )
        }

        @Provides
        fun createRepositoryHandler(instantiator: Instantiator, baseRepositoryFactory: BaseRepositoryFactory, callbackDecorator: CollectionCallbackActionDecorator?): RepositoryHandler {
            return instantiator.newInstance<DefaultRepositoryHandler>(DefaultRepositoryHandler::class.java, baseRepositoryFactory, instantiator, callbackDecorator)
        }

        @Provides
        fun createConfigurationContainer(
            instantiator: Instantiator,
            callbackDecorator: CollectionCallbackActionDecorator?,
            domainObjectContext: DomainObjectContext?,
            defaultConfigurationFactory: DefaultConfigurationFactory?,
            resolutionStrategyFactory: ResolutionStrategyFactory?,
            problemsService: ProblemsInternal?,
            resolverFactory: ConfigurationResolver.Factory?,
            attributesSchema: AttributesSchemaInternal?
        ): ConfigurationContainerInternal {
            return instantiator.newInstance<DefaultConfigurationContainer>(
                DefaultConfigurationContainer::class.java,
                instantiator,
                callbackDecorator,
                domainObjectContext,
                defaultConfigurationFactory,
                resolutionStrategyFactory,
                problemsService,
                resolverFactory,
                attributesSchema
            )
        }

        @Provides
        fun createDependencyHandler(
            instantiator: Instantiator,
            configurationContainer: ConfigurationContainerInternal,
            dependencyFactory: DependencyFactoryInternal?,
            dependencyConstraintHandler: DependencyConstraintHandler?,
            componentMetadataHandler: ComponentMetadataHandler?,
            componentModuleMetadataHandler: ComponentModuleMetadataHandler?,
            resolutionQueryFactory: ArtifactResolutionQueryFactory?,
            attributesSchema: AttributesSchema?,
            variantTransformRegistry: VariantTransformRegistry?,
            artifactTypeRegistry: ArtifactTypeRegistry?,
            objects: ObjectFactory?,
            platformSupport: PlatformSupport?
        ): DependencyHandler {
            return instantiator.newInstance<DefaultDependencyHandler>(
                DefaultDependencyHandler::class.java,
                configurationContainer,
                dependencyFactory,
                dependencyConstraintHandler,
                componentMetadataHandler,
                componentModuleMetadataHandler,
                resolutionQueryFactory,
                attributesSchema,
                variantTransformRegistry,
                artifactTypeRegistry,
                objects,
                platformSupport
            )
        }

        @Provides
        fun createDependencyLockingHandler(instantiator: Instantiator, dependencyLockingProvider: DependencyLockingProvider?, serviceRegistry: ServiceRegistry): DependencyLockingHandler {
            check(!domainObjectContext.isPluginContext()) { "Cannot use locking handler in plugins context" }
            // The lambda factory is to avoid eager creation of the configuration container
            return instantiator.newInstance<DefaultDependencyLockingHandler>(
                DefaultDependencyLockingHandler::class.java,
                Supplier { serviceRegistry.get<ConfigurationContainer?>(ConfigurationContainer::class.java) },
                dependencyLockingProvider
            )
        }

        @Provides
        fun createDependencyLockingProvider(
            fileResolver: FileResolver,
            startParameter: StartParameter,
            context: DomainObjectContext,
            globalDependencyResolutionRules: GlobalDependencyResolutionRules,
            listenerManager: ListenerManager,
            propertyFactory: PropertyFactory,
            filePropertyFactory: FilePropertyFactory,
            fileResourceListener: FileResourceListener
        ): DependencyLockingProvider {
            if (domainObjectContext.isPluginContext()) {
                return instance
            }

            val dependencyLockingProvider = DefaultDependencyLockingProvider(
                fileResolver,
                startParameter,
                context,
                globalDependencyResolutionRules.getDependencySubstitutionRules(),
                propertyFactory,
                filePropertyFactory,
                fileResourceListener
            )
            if (startParameter.isWriteDependencyLocks()) {
                listenerManager.addListener(object : BuildModelLifecycleListener {
                    override fun beforeModelDiscarded(model: GradleInternal, buildFailed: Boolean) {
                        if (!buildFailed) {
                            dependencyLockingProvider.buildFinished()
                        }
                    }
                })
            }
            return dependencyLockingProvider
        }

        @Provides
        fun createDependencyConstraintHandler(
            instantiator: Instantiator,
            configurationContainer: ConfigurationContainerInternal,
            dependencyConstraintFactory: DependencyConstraintFactoryInternal?,
            platformSupport: PlatformSupport?
        ): DependencyConstraintHandler {
            return instantiator.newInstance<DefaultDependencyConstraintHandler>(DefaultDependencyConstraintHandler::class.java, configurationContainer, dependencyConstraintFactory, platformSupport)
        }

        @Provides([ComponentMetadataHandler::class, ComponentMetadataHandlerInternal::class])
        fun createComponentMetadataHandler(
            instantiator: Instantiator,
            objectFactory: ObjectFactory,
            moduleIdentifierFactory: ImmutableModuleIdentifierFactory?,
            interner: SimpleMapInterner?,
            attributesFactory: AttributesFactory?,
            isolatableFactory: IsolatableFactory?,
            componentMetadataRuleExecutor: ComponentMetadataRuleExecutor?,
            platformSupport: PlatformSupport?,
            problems: Problems?
        ): DefaultComponentMetadataHandler {
            val componentMetadataHandler = instantiator.newInstance<DefaultComponentMetadataHandler>(
                DefaultComponentMetadataHandler::class.java,
                instantiator,
                moduleIdentifierFactory,
                interner,
                attributesFactory,
                isolatableFactory,
                componentMetadataRuleExecutor,
                platformSupport,
                problems
            )
            if (domainObjectContext.isScript()) {
                componentMetadataHandler.setVariantDerivationStrategy(objectFactory.newInstance<JavaEcosystemVariantDerivationStrategy?>(JavaEcosystemVariantDerivationStrategy::class.java)!!)
            }
            return componentMetadataHandler
        }

        @Provides
        fun createComponentModuleMetadataHandler(instantiator: Instantiator, moduleIdentifierFactory: ImmutableModuleIdentifierFactory): ComponentModuleMetadataHandlerInternal {
            return instantiator.newInstance<DefaultComponentModuleMetadataHandler>(DefaultComponentModuleMetadataHandler::class.java, moduleIdentifierFactory)
        }

        @Provides
        fun createArtifactHandler(
            instantiator: Instantiator,
            configurationContainer: ConfigurationContainerInternal,
            publishArtifactNotationParserFactory: PublishArtifactNotationParserFactory
        ): ArtifactHandler {
            return instantiator.newInstance<DefaultArtifactHandler>(DefaultArtifactHandler::class.java, configurationContainer, publishArtifactNotationParserFactory.create())
        }

        @Provides
        fun createComponentMetadataProcessorFactory(
            componentMetadataHandler: ComponentMetadataHandlerInternal,
            dependencyResolutionManagement: DependencyResolutionManagementInternal,
            context: DomainObjectContext
        ): ComponentMetadataProcessorFactory {
            if (context.isScript()) {
                return ComponentMetadataProcessorFactory { resolutionContext: MetadataResolutionContext? -> componentMetadataHandler.createComponentMetadataProcessor(resolutionContext!!) }
            }
            return componentMetadataHandler.createFactory(dependencyResolutionManagement)
        }

        @Provides
        fun createResolutionFailureHandler(
            instantiatorFactory: InstantiatorFactory,
            serviceRegistry: ServiceRegistry,
            problemsService: ProblemsInternal,
            transformedVariantConverter: TransformedVariantConverter
        ): ResolutionFailureHandler {
            val instanceGenerator = instantiatorFactory.inject(serviceRegistry)

            val handler = ResolutionFailureHandler(instanceGenerator, problemsService, transformedVariantConverter)
            GradlePluginVariantsSupport.configureFailureHandler(handler)
            PlatformSupport.configureFailureHandler(handler)
            return handler
        }

        @Provides
        fun createArtifactPublicationServices(services: ServiceRegistry): ArtifactPublicationServices {
            return DefaultArtifactPublicationServices(services)
        }

        @Provides
        fun createDependencyResolutionServices(services: ServiceRegistry): DependencyResolutionServices {
            return DefaultDependencyResolutionServices(services)
        }

        @Provides
        fun createRepositoriesSupplier(repositoryHandler: RepositoryHandler, drm: DependencyResolutionManagementInternal, context: DomainObjectContext): RepositoriesSupplier {
            return org.gradle.api.internal.artifacts.RepositoriesSupplier {
                var repositories: MutableList<ResolutionAwareRepository?> = collectRepositories(repositoryHandler)
                if (context.isScript() || context.isDetachedState()) {
                    return@RepositoriesSupplier repositories
                }
                val mode = drm.getConfiguredRepositoriesMode()
                if (mode.useProjectRepositories()) {
                    if (repositories.isEmpty()) {
                        repositories = collectRepositories(drm.getRepositories())
                    }
                } else {
                    repositories = collectRepositories(drm.getRepositories())
                }
                repositories
            }
        }

        @Provides
        fun createConfigurationServicesBundle(
            buildOperationRunner: BuildOperationRunner,
            projectStateRegistry: ProjectStateRegistry,
            fileCollectionFactory: FileCollectionFactory,
            objectFactory: ObjectFactory,
            attributesFactory: AttributesFactory,
            collectionCallbackActionDecorator: CollectionCallbackActionDecorator,
            domainObjectCollectionFactory: DomainObjectCollectionFactory,
            calculatedValueContainerFactory: CalculatedValueContainerFactory,
            taskDependencyFactory: TaskDependencyFactory,
            problems: ProblemsInternal,
            attributeDesugaring: AttributeDesugaring,
            exceptionMapper: ResolveExceptionMapper,
            providerFactory: ProviderFactory
        ): ConfigurationServicesBundle {
            return DefaultConfigurationServicesBundle(
                buildOperationRunner,
                projectStateRegistry,
                calculatedValueContainerFactory,
                objectFactory,
                fileCollectionFactory,
                taskDependencyFactory,
                attributesFactory,
                domainObjectCollectionFactory,
                collectionCallbackActionDecorator,
                problems,
                attributeDesugaring,
                exceptionMapper,
                providerFactory
            )
        }

        companion object {
            private fun collectRepositories(repositoryHandler: RepositoryHandler): MutableList<ResolutionAwareRepository?> {
                return repositoryHandler.stream()
                    .map<ResolutionAwareRepository?> { obj: ArtifactRepository? -> ResolutionAwareRepository::class.java.cast(obj) }
                    .collect(Collectors.toList())
            }
        }
    }

    private class DefaultDependencyResolutionServices(private val services: ServiceRegistry) : DependencyResolutionServices {
        override fun getResolveRepositoryHandler(): RepositoryHandler? {
            return services.get<RepositoryHandler?>(RepositoryHandler::class.java)
        }

        override fun getConfigurationContainer(): ConfigurationContainerInternal? {
            return services.get<ConfigurationContainerInternal?>(ConfigurationContainerInternal::class.java)
        }

        override fun getDependencyHandler(): DependencyHandler? {
            return services.get<DependencyHandler?>(DependencyHandler::class.java)
        }

        override fun getDependencyLockingHandler(): DependencyLockingHandler? {
            return services.get<DependencyLockingHandler?>(DependencyLockingHandler::class.java)
        }

        override fun getAttributesFactory(): AttributesFactory? {
            return services.get<AttributesFactory?>(AttributesFactory::class.java)
        }

        override fun getAttributesSchema(): AttributesSchema? {
            return services.get<AttributesSchema?>(AttributesSchema::class.java)
        }

        override fun getObjectFactory(): ObjectFactory? {
            return services.get<ObjectFactory?>(ObjectFactory::class.java)
        }

        override fun getDependencyFactory(): DependencyFactory? {
            return services.get<DependencyFactory?>(DependencyFactory::class.java)
        }

        override fun getAttributeDescribers(): AttributeDescriberRegistry? {
            return services.get<AttributeDescriberRegistry?>(AttributeDescriberRegistry::class.java)
        }
    }

    private class DefaultArtifactPublicationServices(private val services: ServiceRegistry) : ArtifactPublicationServices {
        override fun createRepositoryHandler(): RepositoryHandler {
            val instantiator = services.get<Instantiator?>(Instantiator::class.java)
            val baseRepositoryFactory = services.get<BaseRepositoryFactory?>(BaseRepositoryFactory::class.java)
            val callbackDecorator = services.get<CollectionCallbackActionDecorator?>(CollectionCallbackActionDecorator::class.java)
            return instantiator!!.newInstance<DefaultRepositoryHandler>(DefaultRepositoryHandler::class.java, baseRepositoryFactory, instantiator, callbackDecorator)
        }
    }
}
