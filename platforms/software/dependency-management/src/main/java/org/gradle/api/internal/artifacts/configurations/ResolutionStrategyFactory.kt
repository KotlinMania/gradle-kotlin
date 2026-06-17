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
package org.gradle.api.internal.artifacts.configurations

import org.gradle.StartParameter
import org.gradle.api.capabilities.Capability
import org.gradle.api.internal.artifacts.ComponentSelectorConverter
import org.gradle.api.internal.artifacts.GlobalDependencyResolutionRules
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.dsl.CapabilityNotationParserFactory
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingProvider
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.ComponentSelectorNotationConverter
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DefaultDependencySubstitutions
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionsInternal
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.CapabilitiesResolutionInternal
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.DefaultCachePolicy
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.DefaultCapabilitiesResolution
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.DefaultResolutionStrategy
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.model.ObjectFactory
import org.gradle.internal.Factory
import org.gradle.internal.build.BuildState
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.internal.typeconversion.NotationParser
import org.gradle.vcs.internal.VcsResolver
import javax.inject.Inject

/**
 * Creates fully initialized [ResolutionStrategyInternal] instances.
 */
@ServiceScope(Scope.Project::class)
class ResolutionStrategyFactory @Inject constructor(
    private val currentBuild: BuildState,
    private val instantiator: Instantiator,
    private val globalDependencySubstitutionRules: GlobalDependencyResolutionRules,
    private val vcsResolver: VcsResolver,
    private val attributesFactory: AttributesFactory,
    private val moduleIdentifierFactory: ImmutableModuleIdentifierFactory,
    private val componentSelectorConverter: ComponentSelectorConverter,
    private val dependencyLockingProvider: DependencyLockingProvider,
    private val moduleSelectorNotationParser: ComponentSelectorNotationConverter,
    private val objectFactory: ObjectFactory,
    private val startParameter: StartParameter
) : Factory<ResolutionStrategyInternal?> {
    private val capabilityNotationParser: NotationParser<Any, Capability>

    init {
        this.capabilityNotationParser = CapabilityNotationParserFactory(false).create()
    }

    override fun create(): ResolutionStrategyInternal {
        val capabilitiesResolutionInternal: CapabilitiesResolutionInternal = instantiator.newInstance<DefaultCapabilitiesResolution>(
            DefaultCapabilitiesResolution::class.java,
            capabilityNotationParser
        )

        val dependencySubstitutions: DependencySubstitutionsInternal? = DefaultDependencySubstitutions.forResolutionStrategy(
            currentBuild, moduleSelectorNotationParser, instantiator, objectFactory, attributesFactory, capabilityNotationParser
        )

        val cachePolicy: CachePolicy = createCachePolicy(startParameter)

        return instantiator.newInstance<DefaultResolutionStrategy>(
            DefaultResolutionStrategy::class.java,
            cachePolicy,
            dependencySubstitutions,
            globalDependencySubstitutionRules,
            vcsResolver,
            moduleIdentifierFactory,
            componentSelectorConverter,
            dependencyLockingProvider,
            capabilitiesResolutionInternal,
            objectFactory
        )
    }

    companion object {
        private fun createCachePolicy(startParameter: StartParameter): CachePolicy {
            val cachePolicy: CachePolicy = DefaultCachePolicy()
            if (startParameter.isOffline()) {
                cachePolicy.setOffline()
            } else if (startParameter.isRefreshDependencies()) {
                cachePolicy.setRefreshDependencies()
            }
            return cachePolicy
        }
    }
}
