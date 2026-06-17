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
package org.gradle.api.internal.artifacts.ivyservice

import com.google.common.collect.ImmutableList
import org.gradle.StartParameter
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.artifacts.UnresolvedDependency
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentSelector
import org.gradle.api.internal.DomainObjectContext
import org.gradle.api.internal.artifacts.ComponentMetadataProcessorFactory
import org.gradle.api.internal.artifacts.ComponentSelectorConverter
import org.gradle.api.internal.artifacts.DefaultResolverResults
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.LegacyResolutionParameters
import org.gradle.api.internal.artifacts.ResolverResults
import org.gradle.api.internal.artifacts.VariantTransformRegistry
import org.gradle.api.internal.artifacts.capability.CapabilitySelectorSerializer
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingProvider
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ComponentResolvers
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ExternalModuleComponentResolverFactory
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResolverProviderFactories
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResolverProviderFactory
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentRegistry
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyResolver
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ComponentResolversChain
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.DependencyGraphResolver
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSelectionSpec
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.DefaultVisitedArtifactSet
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSetResolver
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactsGraphVisitor
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VariantArtifactSetCache
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedArtifactResults
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedArtifactSet
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.CompositeDependencyGraphVisitor
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.results.DefaultVisitedGraphResults
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.results.VisitedGraphResults
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.ResolutionFailureCollector
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorFactory
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.StreamingResolutionResultBuilder
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ThisBuildTreeOnlyGraphElementStore
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.store.ResolutionResultsStoreFactory
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository
import org.gradle.api.internal.artifacts.transform.ConsumerProvidedVariantFinder
import org.gradle.api.internal.artifacts.transform.DefaultTransformUpstreamDependenciesResolver
import org.gradle.api.internal.artifacts.transform.TransformUpstreamDependenciesResolver
import org.gradle.api.internal.artifacts.transform.TransformedVariantFactory
import org.gradle.api.internal.attributes.AttributeDesugaring
import org.gradle.api.internal.attributes.AttributeSchemaServices
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.attributes.immutable.artifact.ImmutableArtifactTypeRegistry
import org.gradle.api.internal.model.NamedObjectInstantiator
import org.gradle.api.internal.tasks.TaskDependencyFactory
import org.gradle.api.specs.Spec
import org.gradle.api.specs.Specs
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector.Companion.newSelector
import org.gradle.internal.component.model.DependencyMetadata
import org.gradle.internal.component.model.ExcludeMetadata
import org.gradle.internal.component.model.GraphVariantSelector
import org.gradle.internal.component.model.IvyArtifactName
import org.gradle.internal.component.model.LocalComponentDependencyMetadata
import org.gradle.internal.component.resolution.failure.ResolutionFailureHandler
import org.gradle.internal.locking.DependencyLockingGraphVisitor
import org.gradle.internal.model.CalculatedValue
import org.gradle.internal.model.CalculatedValueContainerFactory
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.resolve.resolver.ResolvedVariantCache
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import javax.inject.Inject

/**
 * Performs a graph resolution. This class acts as the entry-point to the dependency resolution process.
 *
 *
 * Resolution can either be executed partially or completely:
 *
 *  *
 * During partial resolution, only project dependencies are resolved and all external dependencies are ignored.
 * These results are faster to calculate and are sufficient for task dependency resolution.
 *
 *  *
 * During full resolution, the entire graph is traversed and no dependencies are ignored.
 *
 *
 */
@ServiceScope(Scope.Project::class)
class ResolutionExecutor @Inject constructor(
    private val dependencyGraphResolver: DependencyGraphResolver,
    private val storeFactory: ResolutionResultsStoreFactory,
    startParameter: StartParameter,
    private val moduleIdentifierFactory: ImmutableModuleIdentifierFactory,
    private val buildOperationExecutor: BuildOperationExecutor,
    private val calculatedValueContainerFactory: CalculatedValueContainerFactory,
    private val componentSelectorConverter: ComponentSelectorConverter,
    private val capabilitySelectorSerializer: CapabilitySelectorSerializer,
    private val artifactSetResolver: ResolvedArtifactSetResolver,
    private val componentSelectionDescriptorFactory: ComponentSelectionDescriptorFactory,
    private val graphElementStore: ThisBuildTreeOnlyGraphElementStore,
    private val resolvedVariantCache: ResolvedVariantCache,
    private val graphVariantSelector: GraphVariantSelector,
    private val localComponentRegistry: LocalComponentRegistry,
    resolverFactories: ResolverProviderFactories,
    private val externalResolverFactory: ExternalModuleComponentResolverFactory,
    private val projectDependencyResolver: ProjectDependencyResolver,
    private val dependencyLockingProvider: DependencyLockingProvider,
    private val transformedVariantFactory: TransformedVariantFactory,
    private val attributesFactory: AttributesFactory,
    private val domainObjectContext: DomainObjectContext,
    private val taskDependencyFactory: TaskDependencyFactory,
    private val consumerProvidedVariantFinder: ConsumerProvidedVariantFinder,
    private val attributeSchemaServices: AttributeSchemaServices,
    private val resolutionFailureHandler: ResolutionFailureHandler,
    private val variantArtifactSetCache: VariantArtifactSetCache,
    private val transformRegistry: VariantTransformRegistry,
    private val componentMetadataProcessorFactory: ComponentMetadataProcessorFactory,
    private val attributeDesugaring: AttributeDesugaring,
    private val namedObjectInstantiator: NamedObjectInstantiator
) {
    private val buildProjectDependencies: Boolean
    private val resolverFactories: MutableList<ResolverProviderFactory>

    init {
        this.buildProjectDependencies = startParameter.isBuildProjectDependencies
        this.resolverFactories = resolverFactories.factories
    }

    /**
     * Traverses enough of the graph to calculate the build dependencies of the graph.
     *
     * @param legacyParams Legacy parameters describing what and how to resolve
     * @param params Immutable thread-safe parameters describing what and how to resolve
     * @param futureCompleteResults The future value of the output of [.resolveGraph]. See
     * [DefaultTransformUpstreamDependenciesResolver] for why this is needed.
     *
     * @return An immutable result set, containing a subset of the graph that is sufficient to calculate the build dependencies.
     */
    fun resolveBuildDependencies(
        legacyParams: LegacyResolutionParameters,
        params: ResolutionParameters,
        futureCompleteResults: CalculatedValue<ResolverResults>
    ): ResolverResults {
        val failureCollector = ResolutionFailureCollector(componentSelectorConverter, domainObjectContext)
        val resolutionResultBuilder = InMemoryResolutionResultBuilder()

        val resolvers = getResolvers(params, legacyParams, mutableListOf<ResolutionAwareRepository>())
        val artifactsGraphVisitor = artifactVisitorFor(params.getArtifactTypeRegistry())

        val visitors = ImmutableList.of<DependencyGraphVisitor>(failureCollector, resolutionResultBuilder, artifactsGraphVisitor)
        doResolve(params, legacyParams, ImmutableList.of<ResolutionParameters.ModuleVersionLock>(), resolvers, IS_LOCAL_EDGE, visitors)

        val unresolvedDependencies = failureCollector.complete(mutableSetOf<UnresolvedDependency>())
        val graphResults: VisitedGraphResults = DefaultVisitedGraphResults(resolutionResultBuilder.getResolvedDependencyGraph(), unresolvedDependencies)
        val artifactsResults = artifactsGraphVisitor.complete()

        val dependenciesResolverFactory: TransformUpstreamDependenciesResolver.Factory = TransformUpstreamDependenciesResolver.Factory { visitedArtifacts: VisitedArtifactSet? ->
            DefaultTransformUpstreamDependenciesResolver(
                params.getResolutionHost(),
                params.getConfigurationIdentity(),
                params.getRootVariant().attributes,
                params.getDefaultSortOrder(),
                graphResults,
                visitedArtifacts!!,
                futureCompleteResults,
                domainObjectContext,
                calculatedValueContainerFactory,
                attributesFactory,
                taskDependencyFactory
            )
        }

        val visitedArtifacts = getVisitedArtifactSet(params, resolvers, graphResults, artifactsResults, dependenciesResolverFactory)

        return DefaultResolverResults.buildDependenciesResolved(
            graphResults,
            visitedArtifacts,
            DefaultResolverResults.DefaultLegacyResolverResults.buildDependenciesResolved()
        )
    }

    /**
     * Traverses the full dependency graph.
     *
     * @param legacyParams Legacy parameters describing what and how to resolve
     * @param params Immutable thread-safe parameters describing what and how to resolve
     * @param repositories The repositories used to resolve external dependencies
     *
     * @return An immutable result set, containing the full graph of resolved components.
     */
    fun resolveGraph(
        legacyParams: LegacyResolutionParameters,
        params: ResolutionParameters,
        repositories: MutableList<ResolutionAwareRepository>
    ): ResolverResults {
        val stores = storeFactory.createStoreSet()
        val graphStructureBuilder = StreamingResolutionResultBuilder(
            stores.nextBinaryStore(),
            stores.graphStructureCache(),
            graphElementStore,
            attributeDesugaring,
            capabilitySelectorSerializer,
            componentSelectionDescriptorFactory,
            moduleIdentifierFactory,
            attributesFactory,
            namedObjectInstantiator,
            params.getIncludeAllSelectableVariantResults()
        )

        val failureCollector = ResolutionFailureCollector(componentSelectorConverter, domainObjectContext)

        val graphVisitors = ImmutableList.builder<DependencyGraphVisitor>()
        graphVisitors.add(graphStructureBuilder)
        graphVisitors.add(failureCollector)

        var lockingVisitor: DependencyLockingGraphVisitor? = null
        if (params.isDependencyLockingEnabled()) {
            lockingVisitor = DependencyLockingGraphVisitor(params.getDependencyLockingId(), params.getResolutionHost().displayName(), dependencyLockingProvider)
            graphVisitors.add(lockingVisitor)
        } else {
            dependencyLockingProvider.confirmNotLocked(params.getDependencyLockingId())
        }

        val resolvers = getResolvers(params, legacyParams, repositories)
        val artifactVisitor = artifactVisitorFor(params.getArtifactTypeRegistry())
        graphVisitors.add(artifactVisitor)

        doResolve(params, legacyParams, getAllVersionLocks(params), resolvers, Specs.satisfyAll<DependencyMetadata>(), graphVisitors.build())

        val artifactsResults = artifactVisitor.complete()

        var lockingFailures = mutableSetOf<UnresolvedDependency>()
        if (lockingVisitor != null) {
            lockingFailures = lockingVisitor.collectLockingFailures()
        }

        val resolutionFailures = failureCollector.complete(lockingFailures)
        val resolvedDependencyGraph = graphStructureBuilder.getResolvedDependencyGraph(lockingFailures)
        val graphResults: VisitedGraphResults = DefaultVisitedGraphResults(resolvedDependencyGraph, resolutionFailures)

        // Only write dependency locks if resolution completed without failure.
        if (lockingVisitor != null && !graphResults.hasAnyFailure()) {
            lockingVisitor.writeLocks()
        }

        val dependenciesResolverFactory: TransformUpstreamDependenciesResolver.Factory = TransformUpstreamDependenciesResolver.Factory { visitedArtifacts: VisitedArtifactSet? ->
            DefaultTransformUpstreamDependenciesResolver(
                params.getResolutionHost(),
                params.getConfigurationIdentity(),
                params.getRootVariant().attributes,
                params.getDefaultSortOrder(),
                graphResults,
                visitedArtifacts!!,
                domainObjectContext,
                calculatedValueContainerFactory,
                attributesFactory,
                taskDependencyFactory
            )
        }

        val visitedArtifacts = getVisitedArtifactSet(params, resolvers, graphResults, artifactsResults, dependenciesResolverFactory)

        // Legacy results
        val lenientConfiguration = DefaultLenientConfiguration(
            params.getResolutionHost(),
            graphResults,
            visitedArtifacts,
            resolvedDependencyGraph.graphSource,
            artifactSetResolver,
            getImplicitSelectionSpec(params),
            buildOperationExecutor
        )

        val configuration = DefaultResolvedConfiguration(graphResults, params.getResolutionHost(), visitedArtifacts, lenientConfiguration)

        return DefaultResolverResults.graphResolved(
            graphResults,
            visitedArtifacts,
            DefaultResolverResults.DefaultLegacyResolverResults.graphResolved(configuration)
        )
    }

    private fun artifactVisitorFor(immutableArtifactTypeRegistry: ImmutableArtifactTypeRegistry): ResolvedArtifactsGraphVisitor {
        return ResolvedArtifactsGraphVisitor(
            buildProjectDependencies,
            immutableArtifactTypeRegistry,
            variantArtifactSetCache,
            calculatedValueContainerFactory
        )
    }

    private fun getVisitedArtifactSet(
        params: ResolutionParameters,
        resolvers: ComponentResolvers,
        graphResults: VisitedGraphResults,
        artifactsResults: VisitedArtifactResults,
        dependenciesResolverFactory: TransformUpstreamDependenciesResolver.Factory
    ): VisitedArtifactSet {
        val consumerSchema = params.getRootComponent().getMetadata()!!.getAttributesSchema()
        return DefaultVisitedArtifactSet(
            graphResults,
            params.getResolutionHost(),
            artifactsResults,
            artifactSetResolver,
            transformedVariantFactory,
            dependenciesResolverFactory,
            consumerSchema,
            consumerProvidedVariantFinder,
            attributesFactory,
            attributeSchemaServices,
            resolutionFailureHandler,
            resolvers.artifactResolver,
            params.getArtifactTypeRegistry(),
            resolvedVariantCache,
            graphVariantSelector,
            transformRegistry
        )
    }

    /**
     * Perform dependency resolution and visit the results.
     */
    private fun doResolve(
        params: ResolutionParameters,
        legacyParams: LegacyResolutionParameters,
        moduleVersionLocks: ImmutableList<ResolutionParameters.ModuleVersionLock>,
        resolvers: ComponentResolvers,
        edgeFilter: Spec<DependencyMetadata?>,
        visitors: ImmutableList<DependencyGraphVisitor>
    ) {
        val syntheticDependencies = ImmutableList.builderWithExpectedSize<DependencyMetadata>(moduleVersionLocks.size)
        for (lock in moduleVersionLocks) {
            syntheticDependencies.add(asDependencyConstraintMetadata(lock))
        }

        dependencyGraphResolver.resolve(
            params.getRootComponent(),
            params.getRootVariant(),
            syntheticDependencies.build(),
            edgeFilter,
            componentSelectorConverter,
            resolvers.componentIdResolver,
            resolvers.componentResolver,
            params.getModuleReplacements(),
            legacyParams.dependencySubstitutionRules,
            params.getModuleConflictResolutionStrategy(),
            legacyParams.capabilityConflictResolutionRules,
            params.isFailingOnDynamicVersions(),
            params.isFailingOnChangingVersions(),
            params.getFailureResolutions(),
            CompositeDependencyGraphVisitor(visitors)
        )
    }

    /**
     * Get component resolvers that resolve local and external components.
     */
    private fun getResolvers(
        params: ResolutionParameters,
        legacyParams: LegacyResolutionParameters,
        repositories: MutableList<ResolutionAwareRepository>
    ): ComponentResolvers {
        val resolvers: MutableList<ComponentResolvers> = ArrayList<ComponentResolvers>(3)
        for (factory in resolverFactories) {
            factory.create(resolvers, localComponentRegistry)
        }
        resolvers.add(projectDependencyResolver)

        // TODO: We should reuse these resolvers for all resolutions instead of creating
        // a new one each time we resolve a graph. This means we should not pass any
        // state to `createResolvers` that is specific to this resolution.
        resolvers.add(
            externalResolverFactory.createResolvers(
                repositories,
                componentMetadataProcessorFactory,
                legacyParams.componentSelectionRules!!,
                params.isDependencyVerificationEnabled(),
                params.getCacheExpirationControl(),
                params.getRootComponent().getMetadata()!!.getAttributesSchema()
            )
        )

        return ComponentResolversChain(resolvers)
    }

    private fun getAllVersionLocks(params: ResolutionParameters): ImmutableList<ResolutionParameters.ModuleVersionLock> {
        if (!params.isDependencyLockingEnabled()) {
            return params.getModuleVersionLocks()
        }

        if (params.isFailingOnDynamicVersions()) {
            throw InvalidUserCodeException(
                "Both dependency locking and fail on dynamic versions are enabled. You must choose between the two modes."
            )
        } else if (params.isFailingOnChangingVersions()) {
            throw InvalidUserCodeException(
                "Both dependency locking and fail on changing versions are enabled. You must choose between the two modes."
            )
        }

        return ImmutableList.builder<ResolutionParameters.ModuleVersionLock>()
            .addAll(getLockfileLocks(params))
            .addAll(params.getModuleVersionLocks())
            .build()
    }

    private fun getLockfileLocks(params: ResolutionParameters): ImmutableList<ResolutionParameters.ModuleVersionLock> {
        val dependencyLockingState = dependencyLockingProvider.loadLockState(
            params.getDependencyLockingId(),
            params.getResolutionHost().displayName()
        )

        val strict = dependencyLockingState.mustValidateLockState()

        val lockedDependencies = dependencyLockingState.lockedDependencies
        val locks = ImmutableList.builderWithExpectedSize<ResolutionParameters.ModuleVersionLock>(lockedDependencies.size)
        for (lockedDependency in lockedDependencies) {
            locks.add(
                ResolutionParameters.ModuleVersionLock(
                    lockedDependency.getModuleIdentifier(),
                    lockedDependency.getVersion(),
                    "Dependency version enforced by Dependency Locking",
                    strict
                )
            )
        }
        return locks.build()
    }

    companion object {
        private val IS_LOCAL_EDGE: Spec<DependencyMetadata?> = org.gradle.api.specs.Spec { element: DependencyMetadata? -> element!!.selector is ProjectComponentSelector }

        private fun getImplicitSelectionSpec(params: ResolutionParameters): ArtifactSelectionSpec {
            val requestAttributes: ImmutableAttributes = params.getRootVariant().attributes
            val sortOrder = params.getDefaultSortOrder()
            return ArtifactSelectionSpec(requestAttributes, Specs.satisfyAll<ComponentIdentifier>(), false, false, sortOrder)
        }

        private fun asDependencyConstraintMetadata(lock: ResolutionParameters.ModuleVersionLock): LocalComponentDependencyMetadata {
            val versionConstraint: VersionConstraint = if (lock.isStrict())
                DefaultImmutableVersionConstraint.strictly(lock.getVersion())
            else
                DefaultImmutableVersionConstraint.of(lock.getVersion())

            val selector = newSelector(
                lock.getModuleId(),
                versionConstraint
            )

            return LocalComponentDependencyMetadata(
                selector,
                null,
                ImmutableList.of<IvyArtifactName>(),
                ImmutableList.of<ExcludeMetadata>(),
                false,
                false,
                false,
                true,
                false,
                true,
                lock.getReason()
            )
        }
    }
}
