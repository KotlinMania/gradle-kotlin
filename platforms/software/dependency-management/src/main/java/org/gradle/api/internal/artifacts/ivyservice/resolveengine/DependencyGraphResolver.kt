/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine

import com.google.common.collect.ImmutableList
import org.gradle.api.internal.artifacts.ComponentSelectorConverter
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal
import org.gradle.api.internal.artifacts.configurations.ConflictResolution
import org.gradle.api.internal.artifacts.dsl.ImmutableModuleReplacements
import org.gradle.api.internal.artifacts.ivyservice.ResolutionParameters
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DefaultDependencySubstitutionApplicator
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionApplicator
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionComparator
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.CapabilitiesResolutionInternal
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ComponentState
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.DependencyGraphBuilder
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorFactory
import org.gradle.api.specs.Spec
import org.gradle.internal.ImmutableActionSet
import org.gradle.internal.component.local.model.LocalComponentGraphResolveState
import org.gradle.internal.component.local.model.LocalVariantGraphResolveState
import org.gradle.internal.component.model.DependencyMetadata
import org.gradle.internal.instantiation.InstantiatorFactory
import org.gradle.internal.model.InMemoryCacheFactory
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import javax.inject.Inject

/**
 * Resolves a dependency graph and visits it. Essentially, this class is a [DependencyGraphBuilder] executor.
 */
@ServiceScope(Scope.Project::class)
class DependencyGraphResolver @Inject constructor(
    private val versionComparator: VersionComparator,
    private val versionParser: VersionParser,
    private val instantiatorFactory: InstantiatorFactory,
    private val componentSelectionDescriptorFactory: ComponentSelectionDescriptorFactory,
    private val dependencyGraphBuilder: DependencyGraphBuilder,
    private val cacheFactory: InMemoryCacheFactory
) {
    /**
     * Perform a graph resolution, visiting the resolved graph with the provided visitor.
     *
     *
     * We should keep this class independent of
     * [LegacyResolutionParameters] and
     * [org.gradle.api.artifacts.ResolutionStrategy], as those are tightly
     * coupled to a Configuration, and this resolver should be able to resolve non-Configuration types.
     */
    fun resolve(
        rootComponent: LocalComponentGraphResolveState,
        rootVariant: LocalVariantGraphResolveState,
        syntheticDependencies: MutableList<out DependencyMetadata>,
        edgeFilter: Spec<in DependencyMetadata?>,
        componentSelectorConverter: ComponentSelectorConverter,
        componentIdResolver: DependencyToComponentIdResolver,
        componentMetaDataResolver: ComponentMetaDataResolver,
        moduleReplacements: ImmutableModuleReplacements,
        dependencySubstitutionRule: ImmutableActionSet<DependencySubstitutionInternal>,
        conflictResolution: ConflictResolution,
        capabilityResolutionRules: ImmutableList<CapabilitiesResolutionInternal.CapabilityResolutionRule>,
        failingOnDynamicVersions: Boolean,
        failingOnChangingVersions: Boolean,
        failureResolutions: ResolutionParameters.FailureResolutions,
        modelVisitor: DependencyGraphVisitor
    ) {
        val substitutionApplicator = createDependencySubstitutionApplicator(dependencySubstitutionRule)
        val moduleConflictResolver = createModuleConflictResolver(conflictResolution)

        dependencyGraphBuilder.resolve(
            rootComponent,
            rootVariant,
            syntheticDependencies,
            edgeFilter,
            componentSelectorConverter,
            componentIdResolver,
            componentMetaDataResolver,
            moduleReplacements,
            substitutionApplicator,
            moduleConflictResolver,
            capabilityResolutionRules,
            conflictResolution,
            failingOnDynamicVersions,
            failingOnChangingVersions,
            failureResolutions,
            modelVisitor
        )
    }

    private fun createDependencySubstitutionApplicator(dependencySubstitutionRule: ImmutableActionSet<DependencySubstitutionInternal>): DependencySubstitutionApplicator {
        if (dependencySubstitutionRule.isEmpty()) {
            return DependencySubstitutionApplicator.NO_OP
        }

        return DefaultDependencySubstitutionApplicator(
            componentSelectionDescriptorFactory,
            dependencySubstitutionRule,
            instantiatorFactory,
            cacheFactory
        )
    }

    private fun createModuleConflictResolver(conflictResolution: ConflictResolution): ModuleConflictResolver<ComponentState> {
        val moduleConflictResolver: ModuleConflictResolver<ComponentState> = LatestModuleConflictResolver<ComponentState>(versionComparator, versionParser)
        if (conflictResolution != ConflictResolution.preferProjectModules) {
            return moduleConflictResolver
        }
        return ProjectDependencyForcingResolver<ComponentState>(moduleConflictResolver)
    }
}
