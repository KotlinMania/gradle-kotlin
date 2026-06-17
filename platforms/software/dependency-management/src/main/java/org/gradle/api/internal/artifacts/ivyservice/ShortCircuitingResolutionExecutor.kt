/*
 * Copyright 2009 the original author or authors.
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

import com.google.common.annotations.VisibleForTesting
import org.gradle.api.artifacts.ResolutionStrategy
import org.gradle.api.artifacts.ResolveException
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedConfiguration
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.artifacts.UnresolvedDependency
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.DefaultResolverResults
import org.gradle.api.internal.artifacts.LegacyResolutionParameters
import org.gradle.api.internal.artifacts.ResolverResults
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingProvider
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSelectionSpec
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.SelectedArtifactResults
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.SelectedArtifactSet
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedArtifactSet
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.results.DefaultVisitedGraphResults
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.results.VisitedGraphResults
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.GraphStructureBuilder
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ResolvedDependencyGraph
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository
import org.gradle.api.internal.attributes.AttributeDesugaring
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.api.specs.Specs
import org.gradle.internal.component.model.VariantGraphResolveState
import org.gradle.internal.model.CalculatedValue
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import java.util.function.Supplier

/**
 * Detects empty resolutions and skips a lot of work in those cases.
 */
@ServiceScope(Scope.Project::class)
class ShortCircuitingResolutionExecutor(
    private val delegate: ResolutionExecutor,
    private val attributeDesugaring: AttributeDesugaring,
    private val dependencyLockingProvider: DependencyLockingProvider
) {
    fun resolveBuildDependencies(legacyParams: LegacyResolutionParameters, params: ResolutionParameters, futureCompleteResults: CalculatedValue<ResolverResults>): ResolverResults {
        if (hasDependencies(params)) {
            return delegate.resolveBuildDependencies(legacyParams, params, futureCompleteResults)
        }

        val graphResults = emptyGraphResults(params)
        return DefaultResolverResults.buildDependenciesResolved(
            graphResults, EmptyResults.Companion.INSTANCE,
            DefaultResolverResults.DefaultLegacyResolverResults.buildDependenciesResolved()
        )
    }

    @Throws(ResolveException::class)
    fun resolveGraph(legacyParams: LegacyResolutionParameters, params: ResolutionParameters, repositories: MutableList<ResolutionAwareRepository>): ResolverResults {
        if (hasDependencies(params)) {
            return delegate.resolveGraph(legacyParams, params, repositories)
        }

        if (params.isDependencyLockingEnabled()) {
            val lockingState = dependencyLockingProvider.loadLockState(params.getDependencyLockingId(), params.getResolutionHost().displayName())
            if (lockingState.mustValidateLockState() && !lockingState.lockedDependencies.isEmpty()) {
                // Invalid lock state, need to do a real resolution to gather locking failures
                return delegate.resolveGraph(legacyParams, params, repositories)
            }
            dependencyLockingProvider.persistResolvedDependencies(
                params.getDependencyLockingId(),
                params.getResolutionHost().displayName(),
                mutableSetOf<ModuleComponentIdentifier>(),
                mutableSetOf<ModuleComponentIdentifier>()
            )
        }

        val graphResults = emptyGraphResults(params)
        val resolvedConfiguration: ResolvedConfiguration = DefaultResolvedConfiguration(
            graphResults, params.getResolutionHost(), EmptyResults.Companion.INSTANCE, EmptyLenientConfiguration()
        )
        return DefaultResolverResults.graphResolved(
            graphResults, EmptyResults.Companion.INSTANCE,
            DefaultResolverResults.DefaultLegacyResolverResults.graphResolved(resolvedConfiguration)
        )
    }

    private fun emptyGraphResults(params: ResolutionParameters): VisitedGraphResults {
        val rootComponent = params.getRootComponent()
        val rootVariant: VariantGraphResolveState = params.getRootVariant()

        val structure = GraphStructureBuilder.empty(
            rootComponent.moduleVersionId,
            rootComponent.getId()!!,
            rootVariant.getMetadata()!!.getAttributes()!!,
            rootVariant.getMetadata()!!.getCapabilities()!!,
            rootVariant.getMetadata()!!.getName(),
            attributeDesugaring
        )
        return DefaultVisitedGraphResults(
            ResolvedDependencyGraph(
                rootVariant.attributes,
                Supplier { structure },
                null
            ),
            mutableSetOf<UnresolvedDependency>()
        )
    }

    class EmptyResults : VisitedArtifactSet, SelectedArtifactSet, SelectedArtifactResults {
        override fun select(spec: ArtifactSelectionSpec): SelectedArtifactSet {
            return this
        }

        override fun selectLegacy(spec: ArtifactSelectionSpec): SelectedArtifactResults {
            return this
        }

        override fun visitDependencies(context: TaskDependencyResolveContext) {
        }

        override fun visitArtifacts(visitor: ArtifactVisitor, continueOnSelectionFailure: Boolean) {
        }

        override fun getArtifacts(): ResolvedArtifactSet {
            return ResolvedArtifactSet.EMPTY
        }

        override fun getArtifactsWithId(id: Int): ResolvedArtifactSet {
            return ResolvedArtifactSet.EMPTY
        }

        companion object {
            val INSTANCE: EmptyResults = EmptyResults()
        }
    }

    @VisibleForTesting
    class EmptyLenientConfiguration : LenientConfigurationInternal {
        override fun getImplicitSelectionSpec(): ArtifactSelectionSpec {
            return ArtifactSelectionSpec(
                ImmutableAttributes.EMPTY, Specs.satisfyAll<ComponentIdentifier>(), false, false, ResolutionStrategy.SortOrder.DEFAULT
            )
        }

        override fun getFirstLevelModuleDependencies(): MutableSet<ResolvedDependency> {
            return mutableSetOf<ResolvedDependency>()
        }

        override fun getAllModuleDependencies(): MutableSet<ResolvedDependency> {
            return mutableSetOf<ResolvedDependency>()
        }

        override fun getUnresolvedModuleDependencies(): MutableSet<UnresolvedDependency> {
            return mutableSetOf<UnresolvedDependency>()
        }

        override fun getArtifacts(): MutableSet<ResolvedArtifact> {
            return mutableSetOf<ResolvedArtifact>()
        }
    }

    companion object {
        private fun hasDependencies(params: ResolutionParameters): Boolean {
            val rootVariant = params.getRootVariant()

            if (!rootVariant.files!!.isEmpty()) {
                return true
            }

            for (dependency in rootVariant.getDependencies()!!) {
                if (!dependency.isConstraint) {
                    return true
                }
            }

            // All dependencies are constraints
            return false
        }
    }
}
