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
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.internal.DomainObjectContext
import org.gradle.api.internal.artifacts.ComponentModuleMetadataHandlerInternal
import org.gradle.api.internal.artifacts.ConfigurationResolver
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.LegacyResolutionParameters
import org.gradle.api.internal.artifacts.RepositoriesSupplier
import org.gradle.api.internal.artifacts.ResolverResults
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal
import org.gradle.api.internal.artifacts.configurations.ConfigurationsProvider
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal
import org.gradle.api.internal.artifacts.dsl.ImmutableModuleReplacements
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.AdhocRootComponentProvider
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.ProjectRootComponentProvider
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.RootComponentProvider
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.LocalVariantGraphResolveStateBuilder
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.Conflict
import org.gradle.api.internal.artifacts.repositories.ContentFilteringRepository
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository
import org.gradle.api.internal.artifacts.type.ArtifactTypeRegistry
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.AttributeSchemaServices
import org.gradle.api.internal.attributes.AttributesSchemaInternal
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchemaFactory
import org.gradle.api.internal.project.ProjectIdentity
import org.gradle.internal.component.local.model.LocalComponentGraphResolveState
import org.gradle.internal.component.local.model.LocalComponentGraphResolveStateFactory
import org.gradle.internal.component.local.model.LocalVariantGraphResolveState
import org.gradle.internal.model.CalculatedValue
import org.gradle.internal.model.CalculatedValueContainerFactory
import org.gradle.util.Path
import java.util.stream.Collectors

/**
 * Responsible for resolving a configuration. Delegates to a [ShortCircuitingResolutionExecutor] to perform
 * the actual resolution.
 */
class DefaultConfigurationResolver(
    private val repositoriesSupplier: RepositoriesSupplier,
    private val resolutionExecutor: ShortCircuitingResolutionExecutor,
    private val artifactTypeRegistry: ArtifactTypeRegistry,
    private val componentModuleMetadataHandler: ComponentModuleMetadataHandlerInternal,
    private val attributeSchemaServices: AttributeSchemaServices,
    private val variantStateBuilder: LocalVariantGraphResolveStateBuilder,
    private val calculatedValueContainerFactory: CalculatedValueContainerFactory,
    private val rootComponentProvider: RootComponentProvider
) : ConfigurationResolver {
    override fun resolveBuildDependencies(configuration: ConfigurationInternal, futureCompleteResults: CalculatedValue<ResolverResults?>): ResolverResults? {
        val rootComponent = rootComponentProvider.getRootComponent(configuration.isDetachedConfiguration)
        val rootVariant = asRootVariant(configuration, rootComponent.getId()!!)

        val params = getResolutionParameters(configuration, rootComponent, rootVariant, false)
        val legacyParams: LegacyResolutionParameters = ConfigurationLegacyResolutionParameters(configuration.getResolutionStrategy())
        return resolutionExecutor.resolveBuildDependencies(legacyParams, params, futureCompleteResults)
    }

    override fun resolveGraph(configuration: ConfigurationInternal): ResolverResults? {
        val rootComponent = rootComponentProvider.getRootComponent(configuration.isDetachedConfiguration)
        val rootVariant = asRootVariant(configuration, rootComponent.getId()!!)

        val attributes: AttributeContainerInternal = rootVariant.attributes
        val filteredRepositories = repositoriesSupplier.get()!!.stream()
            .filter { repository: ResolutionAwareRepository? -> !shouldSkipRepository(repository, configuration.getName(), attributes) }
            .collect(Collectors.toList())

        val params = getResolutionParameters(configuration, rootComponent, rootVariant, true)
        val legacyParams: LegacyResolutionParameters = ConfigurationLegacyResolutionParameters(configuration.getResolutionStrategy())
        return resolutionExecutor.resolveGraph(legacyParams, params, filteredRepositories)
    }

    //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
    private fun asRootVariant(configuration: ConfigurationInternal, componentId: ComponentIdentifier): LocalVariantGraphResolveState {
        return variantStateBuilder.createRootVariantState(
            configuration,
            componentId,
            LocalVariantGraphResolveStateBuilder.DependencyCache(),
            configuration.domainObjectContext.getModel(),
            calculatedValueContainerFactory
        )
    }

    val allRepositories: MutableList<ResolutionAwareRepository?>?
        get() = repositoriesSupplier.get()

    private fun getResolutionParameters(
        configuration: ConfigurationInternal,
        rootComponent: LocalComponentGraphResolveState,
        rootVariant: LocalVariantGraphResolveState,
        includeConsistentResolutionLocks: Boolean
    ): ResolutionParameters {
        val resolutionStrategy = configuration.getResolutionStrategy()
        val moduleVersionLocks: ImmutableList<ResolutionParameters.ModuleVersionLock?> =
            if (includeConsistentResolutionLocks) configuration.consistentResolutionVersionLocks else ImmutableList.of<ResolutionParameters.ModuleVersionLock?>()
        val immutableArtifactTypeRegistry = attributeSchemaServices.artifactTypeRegistryFactory.create(artifactTypeRegistry)
        val moduleReplacements: ImmutableModuleReplacements = componentModuleMetadataHandler.moduleReplacements
        val failureResolutions = ConfigurationFailureResolutions(configuration.domainObjectContext.getProjectIdentity(), configuration.getName())

        return ResolutionParameters(
            configuration.resolutionHost,
            rootComponent,
            rootVariant,
            moduleVersionLocks,
            resolutionStrategy.sortOrder,
            configuration.configurationIdentity,
            immutableArtifactTypeRegistry,
            moduleReplacements,
            resolutionStrategy.conflictResolution,
            configuration.getName(),
            resolutionStrategy.isDependencyLockingEnabled,
            resolutionStrategy.includeAllSelectableVariantResults,
            resolutionStrategy.isDependencyVerificationEnabled,
            resolutionStrategy.isFailingOnDynamicVersions,
            resolutionStrategy.isFailingOnChangingVersions,
            failureResolutions,
            resolutionStrategy.cachePolicy.asImmutable()
        )
    }

    private class ConfigurationLegacyResolutionParameters(private val resolutionStrategy: ResolutionStrategyInternal) : LegacyResolutionParameters {
        val dependencySubstitutionRules: ImmutableActionSet<DependencySubstitutionInternal?>
            get() = resolutionStrategy.dependencySubstitutionRule

        val capabilityConflictResolutionRules: ImmutableList<CapabilitiesResolutionInternal.CapabilityResolutionRule?>
            get() = resolutionStrategy.capabilitiesResolutionRules.rules

        val componentSelectionRules: ComponentSelectionRulesInternal
            get() = resolutionStrategy.getComponentSelection()
    }

    private class ConfigurationFailureResolutions(
        private val owningProject: ProjectIdentity?,
        private val configurationName: String?
    ) : ResolutionParameters.FailureResolutions {
        override fun forVersionConflict(conflict: Conflict): MutableList<String?>? {
            if (owningProject == null) {
                // owningProject is null for settings execution
                return mutableListOf<String?>()
            }

            val taskPath = owningProject.getBuildTreePath().append(Path.path("dependencyInsight")).asString()

            val moduleId = conflict.getModuleId()
            val dependencyNotation = moduleId.getGroup() + ":" + moduleId.getName()

            return mutableListOf<String?>(
                String.format(
                    "Run with %s --configuration %s --dependency %s to get more insight on how to solve the conflict.",
                    taskPath, configurationName, dependencyNotation
                )
            )
        }
    }

    /**
     * Constructs new instances of [DefaultConfigurationResolver]s.
     */
    class Factory(
        private val moduleIdentity: DependencyMetaDataProvider,
        private val repositoriesSupplier: RepositoriesSupplier,
        private val resolutionExecutor: ShortCircuitingResolutionExecutor,
        private val artifactTypeRegistry: ArtifactTypeRegistry,
        private val componentModuleMetadataHandler: ComponentModuleMetadataHandlerInternal,
        private val attributeSchemaServices: AttributeSchemaServices,
        private val variantStateBuilder: LocalVariantGraphResolveStateBuilder,
        private val calculatedValueContainerFactory: CalculatedValueContainerFactory,
        private val moduleIdentifierFactory: ImmutableModuleIdentifierFactory,
        private val attributesSchemaFactory: ImmutableAttributesSchemaFactory,
        private val localResolveStateFactory: LocalComponentGraphResolveStateFactory
    ) : ConfigurationResolver.Factory {
        override fun create(
            configurations: ConfigurationsProvider,
            owner: DomainObjectContext,
            schema: AttributesSchemaInternal
        ): ConfigurationResolver? {
            val rootComponentProvider = createRootComponentProvider(configurations, owner, schema)

            return DefaultConfigurationResolver(
                repositoriesSupplier,
                resolutionExecutor,
                artifactTypeRegistry,
                componentModuleMetadataHandler,
                attributeSchemaServices,
                variantStateBuilder,
                calculatedValueContainerFactory,
                rootComponentProvider
            )
        }

        private fun createRootComponentProvider(
            configurations: ConfigurationsProvider,
            owner: DomainObjectContext,
            schema: AttributesSchemaInternal
        ): RootComponentProvider {
            val adhocRootComponentProvider = AdhocRootComponentProvider(
                schema,
                moduleIdentifierFactory,
                attributesSchemaFactory,
                localResolveStateFactory
            )

            if (owner.getProjectIdentity() == null) {
                return adhocRootComponentProvider
            }

            // TODO #1629: Eventually, resolutions within a project should live within
            //  an adhoc root component, and should use an AdhocRootComponentProvider.
            return ProjectRootComponentProvider(
                owner.getProject()!!.getOwner(),
                moduleIdentity,
                schema,
                configurations,
                moduleIdentifierFactory,
                localResolveStateFactory,
                attributesSchemaFactory,
                adhocRootComponentProvider
            )
        }
    }

    companion object {
        /**
         * Determines if the repository should not be used to resolve this configuration.
         */
        private fun shouldSkipRepository(
            repository: ResolutionAwareRepository?,
            configurationName: String?,
            consumerAttributes: AttributeContainer
        ): Boolean {
            if (repository !is ContentFilteringRepository) {
                return false
            }

            val cfr = repository as ContentFilteringRepository

            val includedConfigurations: MutableSet<String?>? = cfr.includedConfigurations
            val excludedConfigurations: MutableSet<String?>? = cfr.excludedConfigurations

            if ((includedConfigurations != null && !includedConfigurations.contains(configurationName)) ||
                (excludedConfigurations != null && excludedConfigurations.contains(configurationName))
            ) {
                return true
            }

            val requiredAttributes: MutableMap<Attribute<Any?>?, MutableSet<Any?>?>? = cfr.requiredAttributes
            return hasNonRequiredAttribute(requiredAttributes, consumerAttributes)
        }

        /**
         * Accepts a map of attribute types to the set of values that are allowed for that attribute type.
         * If the request attributes of the resolve context being resolved do not match the allowed values,
         * then the repository is skipped.
         */
        private fun hasNonRequiredAttribute(
            requiredAttributes: MutableMap<Attribute<Any?>?, MutableSet<Any?>?>?,
            consumerAttributes: AttributeContainer
        ): Boolean {
            if (requiredAttributes == null) {
                return false
            }

            for (entry in requiredAttributes.entries) {
                val key: Attribute<Any?> = entry.key!!
                val allowedValues: MutableSet<Any?> = entry.value!!
                val value = consumerAttributes.getAttribute<Any?>(key)
                if (!allowedValues.contains(value)) {
                    return true
                }
            }

            return false
        }
    }
}
