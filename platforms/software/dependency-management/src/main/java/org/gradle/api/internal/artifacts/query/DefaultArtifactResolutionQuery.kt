/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.api.internal.artifacts.query

import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.query.ArtifactResolutionQuery
import org.gradle.api.artifacts.result.ArtifactResolutionResult
import org.gradle.api.artifacts.result.ComponentArtifactsResult
import org.gradle.api.artifacts.result.ComponentResult
import org.gradle.api.capabilities.Capability
import org.gradle.api.component.Artifact
import org.gradle.api.component.Component
import org.gradle.api.internal.artifacts.ComponentMetadataProcessorFactory
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.RepositoriesSupplier
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyFactory
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ErrorHandlingArtifactResolver
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ExternalModuleComponentResolverFactory
import org.gradle.api.internal.artifacts.repositories.ContentFilteringRepository
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository
import org.gradle.api.internal.artifacts.result.DefaultArtifactResolutionResult
import org.gradle.api.internal.artifacts.result.DefaultComponentArtifactsResult
import org.gradle.api.internal.artifacts.result.DefaultResolvedArtifactResult
import org.gradle.api.internal.artifacts.result.DefaultUnresolvedArtifactResult
import org.gradle.api.internal.artifacts.result.DefaultUnresolvedComponentResult
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema
import org.gradle.api.internal.component.ComponentTypeRegistry
import org.gradle.internal.Describables
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier.Companion.newId
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata
import org.gradle.internal.component.model.DefaultComponentOverrideMetadata
import org.gradle.internal.resolve.resolver.ArtifactResolver
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult
import org.gradle.internal.resolve.result.BuildableComponentResolveResult
import org.gradle.internal.resolve.result.DefaultBuildableArtifactResolveResult
import org.gradle.internal.resolve.result.DefaultBuildableArtifactSetResolveResult
import org.gradle.internal.resolve.result.DefaultBuildableComponentResolveResult
import org.gradle.util.internal.CollectionUtils
import java.util.Arrays
import java.util.stream.Collectors

class DefaultArtifactResolutionQuery(
    private val resolutionStrategyFactory: ResolutionStrategyFactory,
    private val repositoriesSupplier: RepositoriesSupplier,
    private val externalResolverFactory: ExternalModuleComponentResolverFactory,
    private val componentMetadataProcessorFactory: ComponentMetadataProcessorFactory,
    private val componentTypeRegistry: ComponentTypeRegistry
) : ArtifactResolutionQuery {
    private val componentIds: MutableSet<ComponentIdentifier> = LinkedHashSet<ComponentIdentifier>()
    private var componentType: Class<out Component>? = null
    private val artifactTypes: MutableSet<Class<out Artifact>> = LinkedHashSet<Class<out Artifact>>()

    override fun forComponents(componentIds: Iterable<out ComponentIdentifier>): ArtifactResolutionQuery {
        CollectionUtils.addAll(this.componentIds, componentIds)
        return this
    }

    override fun forComponents(vararg componentIds: ComponentIdentifier): ArtifactResolutionQuery {
        CollectionUtils.addAll<ComponentIdentifier?, MutableSet<ComponentIdentifier>?>(this.componentIds, *componentIds)
        return this
    }

    override fun forModule(group: String, name: String, version: String): ArtifactResolutionQuery {
        componentIds.add(newId(DefaultModuleIdentifier.newId(group, name), version))
        return this
    }

    override fun withArtifacts(componentType: Class<out Component>, vararg artifactTypes: Class<out Artifact>): ArtifactResolutionQuery {
        return withArtifacts(componentType, Arrays.asList<Class<out Artifact>>(*artifactTypes))
    }

    override fun withArtifacts(componentType: Class<out Component>, artifactTypes: MutableCollection<Class<out Artifact>>): ArtifactResolutionQuery {
        check(this.componentType == null) { "Cannot specify component type multiple times." }
        this.componentType = componentType
        this.artifactTypes.addAll(artifactTypes)
        return this
    }

    override fun execute(): ArtifactResolutionResult {
        checkNotNull(componentType) { "Must specify component type and artifacts to query." }

        val repositories: MutableList<out ResolutionAwareRepository>? = repositoriesSupplier.get()
        val filteredRepositories = repositories!!.stream()
            .filter { repository: ResolutionAwareRepository ->
                if (repository is ContentFilteringRepository) {
                    val cfr = repository as ContentFilteringRepository
                    // If the repository requires certain request attributes or requires certain configurations,
                    // it should not be used for ARQs.
                    return@filter cfr.requiredAttributes == null && cfr.includedConfigurations == null
                }
                true
            }
            .collect(Collectors.toList())

        // We use a resolution strategy here in order to use the same defaults for dependency verification,
        // caching, etc. that a normal dependency resolution would use.
        val resolutionStrategy = resolutionStrategyFactory.create()

        val componentResolvers = externalResolverFactory.createResolvers(
            filteredRepositories,
            componentMetadataProcessorFactory,
            resolutionStrategy.getComponentSelection(),
            resolutionStrategy.isDependencyVerificationEnabled,
            resolutionStrategy.cachePolicy.asImmutable(),
            ImmutableAttributesSchema.EMPTY
        )

        val componentMetaDataResolver = componentResolvers.componentResolver
        val artifactResolver: ArtifactResolver = ErrorHandlingArtifactResolver(componentResolvers.artifactResolver)
        return createResult(componentMetaDataResolver, artifactResolver)
    }

    private fun createResult(componentMetaDataResolver: ComponentMetaDataResolver, artifactResolver: ArtifactResolver): ArtifactResolutionResult {
        val componentResults: MutableSet<ComponentResult> = HashSet<ComponentResult>()

        for (componentId in componentIds) {
            try {
                val validId = validateComponentIdentifier(componentId)
                componentResults.add(buildComponentResult(validId, componentMetaDataResolver, artifactResolver))
            } catch (t: Exception) {
                componentResults.add(DefaultUnresolvedComponentResult(componentId, t))
            }
        }

        return DefaultArtifactResolutionResult(componentResults)
    }

    private fun validateComponentIdentifier(componentId: ComponentIdentifier): ComponentIdentifier {
        if (componentId is ModuleComponentIdentifier) {
            return componentId
        }
        require(!componentId is ProjectComponentIdentifier) { String.format("Cannot query artifacts for a project component (%s).", componentId.getDisplayName()) }

        throw IllegalArgumentException(String.format("Cannot resolve the artifacts for component %s with unsupported type %s.", componentId.getDisplayName(), componentId.javaClass.getName()))
    }

    private fun buildComponentResult(componentId: ComponentIdentifier, componentMetaDataResolver: ComponentMetaDataResolver, artifactResolver: ArtifactResolver): ComponentArtifactsResult {
        val moduleResolveResult: BuildableComponentResolveResult = DefaultBuildableComponentResolveResult()
        componentMetaDataResolver.resolve(componentId, DefaultComponentOverrideMetadata.EMPTY, moduleResolveResult)
        val component: ComponentArtifactResolveMetadata = moduleResolveResult.state.prepareForArtifactResolution().getArtifactMetadata()
        val componentResult = DefaultComponentArtifactsResult(component.getId()!!)
        for (artifactType in artifactTypes) {
            addArtifacts(componentResult, artifactType, component, artifactResolver)
        }
        return componentResult
    }

    private fun <T : Artifact?> addArtifacts(
        artifacts: DefaultComponentArtifactsResult,
        type: Class<T?>,
        component: ComponentArtifactResolveMetadata,
        artifactResolver: ArtifactResolver
    ) {
        val artifactSetResolveResult: BuildableArtifactSetResolveResult = DefaultBuildableArtifactSetResolveResult()
        val artifactType = componentTypeRegistry.getComponentRegistration(componentType).getArtifactType(type)
        artifactResolver.resolveArtifactsWithType(component, artifactType, artifactSetResolveResult)

        for (artifactMetaData in artifactSetResolveResult.result!!) {
            val resolveResult: BuildableArtifactResolveResult = DefaultBuildableArtifactResolveResult()
            artifactResolver.resolveArtifact(component, artifactMetaData!!, resolveResult)
            try {
                artifacts.addArtifact(
                    externalResolverFactory.verifiedArtifact(
                        DefaultResolvedArtifactResult(
                            artifactMetaData.getId()!!,
                            ImmutableAttributes.EMPTY,
                            ImmutableList.of<Capability>(),
                            Describables.of(component.getId()!!.getDisplayName()),
                            type,
                            resolveResult.getResult()!!.file
                        )
                    )
                )
            } catch (e: Exception) {
                artifacts.addArtifact(DefaultUnresolvedArtifactResult(artifactMetaData.getId()!!, type, e))
            }
        }
    }
}
