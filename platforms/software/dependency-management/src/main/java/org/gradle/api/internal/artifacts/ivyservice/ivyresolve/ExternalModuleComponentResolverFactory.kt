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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve

import org.gradle.api.Action
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.result.ArtifactResult
import org.gradle.api.internal.artifacts.ComponentMetadataProcessorFactory
import org.gradle.api.internal.artifacts.ComponentSelectionRulesInternal
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.MetadataResolutionContext
import org.gradle.api.internal.artifacts.ivyservice.CacheExpirationControl
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionComparator
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.DependencyVerificationOverride
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleRepositoryCacheProvider
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.DefaultComponentSelectionRules
import org.gradle.api.internal.artifacts.repositories.ArtifactResolutionDetails
import org.gradle.api.internal.artifacts.repositories.ContentFilteringRepository
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository
import org.gradle.api.internal.artifacts.result.DefaultResolvedArtifactResult
import org.gradle.api.internal.attributes.AttributeSchemaServices
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema
import org.gradle.api.internal.component.ArtifactType
import org.gradle.api.logging.Logging.getLogger
import org.gradle.internal.Actions
import org.gradle.internal.component.external.model.ExternalModuleComponentGraphResolveState
import org.gradle.internal.component.external.model.ModuleComponentGraphResolveStateFactory
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata
import org.gradle.internal.component.model.ComponentArtifactMetadata
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata
import org.gradle.internal.component.model.ComponentOverrideMetadata
import org.gradle.internal.model.CalculatedValueFactory
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.resolve.caching.ComponentMetadataSupplierRuleExecutor
import org.gradle.internal.resolve.resolver.ArtifactResolver
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult
import org.gradle.internal.resolve.result.BuildableComponentResolveResult
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.util.internal.BuildCommencedTimeProvider
import javax.inject.Inject

/**
 * Creates resolvers that can resolve module components from repositories.
 */
@ServiceScope(Scope.Build::class)
class ExternalModuleComponentResolverFactory @Inject constructor(
    private val cacheProvider: ModuleRepositoryCacheProvider,
    private val startParameterResolutionOverride: StartParameterResolutionOverride,
    private val dependencyVerificationOverride: DependencyVerificationOverride,
    private val listener: ChangingValueDependencyResolutionListener?,
    private val timeProvider: BuildCommencedTimeProvider?,
    private val versionComparator: VersionComparator,
    private val moduleIdentifierFactory: ImmutableModuleIdentifierFactory,
    private val repositoryDisabler: RepositoryDisabler?,
    private val versionParser: VersionParser,
    private val moduleResolveStateFactory: ModuleComponentGraphResolveStateFactory?,
    private val calculatedValueFactory: CalculatedValueFactory,
    private val attributesFactory: AttributesFactory,
    private val attributeSchemaServices: AttributeSchemaServices,
    private val componentMetadataSupplierRuleExecutor: ComponentMetadataSupplierRuleExecutor
) {
    /**
     * Creates component resolvers for the given repositories.
     */
    fun createResolvers(
        repositories: MutableCollection<out ResolutionAwareRepository>,
        metadataProcessor: ComponentMetadataProcessorFactory,
        componentSelectionRules: ComponentSelectionRulesInternal,
        dependencyVerificationEnabled: Boolean,
        cacheExpirationControl: CacheExpirationControl,
        consumerSchema: ImmutableAttributesSchema
    ): ComponentResolvers {
        if (repositories.isEmpty()) {
            return NoRepositoriesResolver()
        }

        val moduleResolver = UserResolverChain(
            versionComparator,
            componentSelectionRules,
            versionParser,
            consumerSchema,
            attributesFactory,
            attributeSchemaServices,
            metadataProcessor,
            componentMetadataSupplierRuleExecutor,
            calculatedValueFactory,
            cacheExpirationControl
        )
        val parentModuleResolver = ParentModuleLookupResolver(
            versionComparator,
            moduleIdentifierFactory,
            versionParser,
            consumerSchema,
            attributesFactory,
            attributeSchemaServices,
            metadataProcessor,
            componentMetadataSupplierRuleExecutor,
            calculatedValueFactory,
            cacheExpirationControl
        )

        for (repository in repositories) {
            val baseRepository = repository.createResolver()

            baseRepository!!.setComponentResolvers(parentModuleResolver)
            val instantiator = baseRepository.getComponentMetadataInstantiator()
            val metadataResolutionContext: MetadataResolutionContext = DefaultMetadataResolutionContext(cacheExpirationControl, instantiator)
            val componentMetadataProcessor = metadataProcessor.createComponentMetadataProcessor(metadataResolutionContext)

            var moduleComponentRepository: ModuleComponentRepository<ExternalModuleComponentGraphResolveState?>
            if (baseRepository.isLocal()) {
                moduleComponentRepository = CachingModuleComponentRepository(
                    baseRepository,
                    cacheProvider.inMemoryOnlyCaches,
                    moduleResolveStateFactory,
                    cacheExpirationControl,
                    timeProvider,
                    componentMetadataProcessor,
                    ChangingValueDependencyResolutionListener.Companion.NO_OP
                )
                moduleComponentRepository = LocalModuleComponentRepository<ExternalModuleComponentGraphResolveState?>(moduleComponentRepository)
            } else {
                val overrideRepository: ModuleComponentRepository<ModuleComponentResolveMetadata?> = startParameterResolutionOverride.overrideModuleVersionRepository(baseRepository)
                moduleComponentRepository = CachingModuleComponentRepository(
                    overrideRepository,
                    cacheProvider.persistentCaches,
                    moduleResolveStateFactory,
                    cacheExpirationControl,
                    timeProvider,
                    componentMetadataProcessor,
                    listener
                )
            }
            moduleComponentRepository = cacheProvider.resolvedArtifactCaches.provideResolvedArtifactCache(moduleComponentRepository, dependencyVerificationEnabled)

            if (baseRepository.isDynamicResolveMode()) {
                moduleComponentRepository = IvyDynamicResolveModuleComponentRepository(moduleComponentRepository, moduleResolveStateFactory)
            }

            moduleComponentRepository = ErrorHandlingModuleComponentRepository(moduleComponentRepository, repositoryDisabler)
            moduleComponentRepository = filterRepository(repository, moduleComponentRepository)
            moduleComponentRepository = maybeApplyDependencyVerification(moduleComponentRepository, dependencyVerificationEnabled)

            moduleResolver.add(moduleComponentRepository)
            parentModuleResolver.add(moduleComponentRepository)
        }

        return moduleResolver
    }

    private fun maybeApplyDependencyVerification(
        moduleComponentRepository: ModuleComponentRepository<ExternalModuleComponentGraphResolveState?>,
        dependencyVerificationEnabled: Boolean
    ): ModuleComponentRepository<ExternalModuleComponentGraphResolveState?> {
        if (!dependencyVerificationEnabled) {
            LOGGER!!.warn("Dependency verification has been disabled.")
            return moduleComponentRepository
        }

        return dependencyVerificationOverride.overrideDependencyVerification(moduleComponentRepository)
    }

    fun verifiedArtifact(defaultResolvedArtifactResult: DefaultResolvedArtifactResult): ArtifactResult {
        return dependencyVerificationOverride.verifiedArtifact(defaultResolvedArtifactResult)
    }

    /**
     * Provides access to the top-level resolver chain for looking up parent modules when parsing module descriptor files.
     */
    private class ParentModuleLookupResolver(
        versionComparator: VersionComparator,
        moduleIdentifierFactory: ImmutableModuleIdentifierFactory,
        versionParser: VersionParser,
        attributesSchema: ImmutableAttributesSchema,
        attributesFactory: AttributesFactory,
        attributeSchemaServices: AttributeSchemaServices,
        componentMetadataProcessorFactory: ComponentMetadataProcessorFactory,
        componentMetadataSupplierRuleExecutor: ComponentMetadataSupplierRuleExecutor,
        calculatedValueFactory: CalculatedValueFactory,
        cacheExpirationControl: CacheExpirationControl
    ) : ComponentResolvers, DependencyToComponentIdResolver, ComponentMetaDataResolver, ArtifactResolver {
        private val delegate: UserResolverChain

        init {
            this.delegate = UserResolverChain(
                versionComparator,
                DefaultComponentSelectionRules(moduleIdentifierFactory),
                versionParser,
                attributesSchema,
                attributesFactory,
                attributeSchemaServices,
                componentMetadataProcessorFactory,
                componentMetadataSupplierRuleExecutor,
                calculatedValueFactory,
                cacheExpirationControl
            )
        }

        fun add(moduleComponentRepository: ModuleComponentRepository<ExternalModuleComponentGraphResolveState?>) {
            delegate.add(moduleComponentRepository)
        }

        override fun getComponentIdResolver(): DependencyToComponentIdResolver {
            return this
        }

        override fun getComponentResolver(): ComponentMetaDataResolver {
            return this
        }

        override fun getArtifactResolver(): ArtifactResolver {
            return this
        }

        override fun resolve(
            selector: ComponentSelector,
            overrideMetadata: ComponentOverrideMetadata,
            acceptor: VersionSelector,
            rejector: VersionSelector?,
            result: BuildableComponentIdResolveResult,
            consumerAttributes: ImmutableAttributes
        ) {
            delegate.getComponentIdResolver().resolve(selector, overrideMetadata, acceptor, rejector, result, consumerAttributes)
        }

        override fun resolve(identifier: ComponentIdentifier, componentOverrideMetadata: ComponentOverrideMetadata, result: BuildableComponentResolveResult) {
            delegate.getComponentResolver().resolve(identifier, componentOverrideMetadata, result)
        }

        override fun isFetchingMetadataCheap(identifier: ComponentIdentifier): Boolean {
            return delegate.getComponentResolver().isFetchingMetadataCheap(identifier)
        }

        override fun resolveArtifactsWithType(component: ComponentArtifactResolveMetadata, artifactType: ArtifactType, result: BuildableArtifactSetResolveResult) {
            delegate.getArtifactResolver().resolveArtifactsWithType(component, artifactType, result)
        }

        override fun resolveArtifact(component: ComponentArtifactResolveMetadata, artifact: ComponentArtifactMetadata, result: BuildableArtifactResolveResult) {
            delegate.getArtifactResolver().resolveArtifact(component, artifact, result)
        }
    }

    private class DefaultMetadataResolutionContext(val cacheExpirationControl: CacheExpirationControl?, val injectingInstantiator: Instantiator?) : MetadataResolutionContext
    companion object {
        private val LOGGER = getLogger(ExternalModuleComponentResolverFactory::class.java)

        private fun filterRepository(
            repository: ResolutionAwareRepository?,
            moduleComponentRepository: ModuleComponentRepository<ExternalModuleComponentGraphResolveState?>
        ): ModuleComponentRepository<ExternalModuleComponentGraphResolveState?> {
            var filter: Action<in ArtifactResolutionDetails?>? = Actions.doNothing<ArtifactResolutionDetails?>()
            if (repository is ContentFilteringRepository) {
                filter = (repository as ContentFilteringRepository).contentFilter
            }

            if (filter === Actions.doNothing<Any?>()) {
                return moduleComponentRepository
            }

            return FilteredModuleComponentRepository(moduleComponentRepository, filter)
        }
    }
}
