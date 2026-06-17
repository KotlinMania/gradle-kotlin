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
package org.gradle.api.publish.internal.mapping

import com.google.common.annotations.VisibleForTesting
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Transformer
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencyConstraint
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedVariantResult
import org.gradle.api.component.SoftwareComponentVariant
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependencyConstraint
import org.gradle.api.internal.artifacts.dependencies.ProjectDependencyInternal
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyPublicationResolver
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.AttributeDesugaring
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchemaFactory.create
import org.gradle.api.internal.provider.Providers
import org.gradle.api.provider.Provider
import org.gradle.api.publish.internal.component.ResolutionBackedVariant
import org.gradle.api.publish.internal.validation.VariantWarningCollector
import org.gradle.api.publish.internal.versionmapping.VersionMappingStrategyInternal
import java.util.function.BiFunction
import javax.inject.Inject

/**
 * Default implementation of [DependencyCoordinateResolverFactory] that
 * resolves dependencies using version mapping.
 */
class DefaultDependencyCoordinateResolverFactory @Inject constructor(
    private val projectDependencyResolver: ProjectDependencyPublicationResolver,
    private val moduleIdentifierFactory: ImmutableModuleIdentifierFactory,
    private val attributeDesugaring: AttributeDesugaring
) : DependencyCoordinateResolverFactory {
    override fun createCoordinateResolvers(
        variant: SoftwareComponentVariant,
        versionMappingStrategy: VersionMappingStrategyInternal
    ): Provider<DependencyCoordinateResolverFactory.DependencyResolvers> {
        var configuration: Configuration? = null
        if (variant is ResolutionBackedVariant) {
            val resolutionBackedVariant = variant
            configuration = resolutionBackedVariant.getResolutionConfiguration()

            if (resolutionBackedVariant.getPublishResolvedCoordinates()) {
                if (configuration == null) {
                    throw InvalidUserDataException("Cannot enable dependency mapping without configuring a resolution configuration.")
                } else {
                    return getDependencyMappingResolver(configuration)
                }
            }
        }

        val attributes = (variant.getAttributes() as AttributeContainerInternal).asImmutable()
        val versionMapping = versionMappingStrategy.findStrategyForVariant(attributes)

        // Fallback to component coordinate mapping if variant mapping is not enabled
        var componentResolver: Provider<out ComponentDependencyResolver>? = null
        if (versionMapping.isEnabled()) {
            if (versionMapping.getUserResolutionConfiguration() != null) {
                configuration = versionMapping.getUserResolutionConfiguration()
            } else if (versionMapping.getDefaultResolutionConfiguration() != null && configuration == null) {
                // The configuration set on the variant is almost always more correct than the
                // default version mapping configuration, which is currently set project-wide
                // by the Java plugin. For this reason, we only use the version mapping default
                // if the dependency mapping configuration is not set.
                configuration = versionMapping.getDefaultResolutionConfiguration()
            }

            if (configuration != null) {
                if (USE_LEGACY_VERSION_MAPPING) {
                    componentResolver = getLegacyResolver(configuration)
                } else {
                    componentResolver =
                        getDependencyMappingResolver(configuration).map<ComponentDependencyResolver>(Transformer { obj: DependencyCoordinateResolverFactory.DependencyResolvers? -> obj!!.getComponentResolver() })
                }
            }
        }

        if (componentResolver == null) {
            // Both version mapping and dependency mapping are disabled
            componentResolver = Providers.of<ProjectOnlyComponentDependencyResolver>(ProjectOnlyComponentDependencyResolver(projectDependencyResolver))
        }

        return componentResolver.map<DependencyCoordinateResolverFactory.DependencyResolvers>({ cr: ComponentDependencyResolver ->
            DependencyCoordinateResolverFactory.DependencyResolvers(
                VariantResolverAdapter(cr), cr
            )
        })
    }

    private fun getDependencyMappingResolver(configuration: Configuration): Provider<DependencyCoordinateResolverFactory.DependencyResolvers> {
        val resolutionResult = configuration.getIncoming().getResolutionResult()
        return resolutionResult.getRootComponent().zip<ResolvedVariantResult, DependencyCoordinateResolverFactory.DependencyResolvers>(
            resolutionResult.getRootVariant(),
            BiFunction { rootComponent: ResolvedComponentResult?, rootVariant: ResolvedVariantResult -> this.getVariantMappingResolvers(rootComponent!!, rootVariant) })
    }

    private fun getVariantMappingResolvers(rootComponent: ResolvedComponentResult, rootVariant: ResolvedVariantResult): DependencyCoordinateResolverFactory.DependencyResolvers {
        val resolver = ResolutionBackedPublicationDependencyResolver(
            projectDependencyResolver,
            moduleIdentifierFactory,
            rootComponent,
            rootVariant,
            attributeDesugaring
        )

        return DependencyCoordinateResolverFactory.DependencyResolvers(resolver, resolver)
    }

    private fun getLegacyResolver(configuration: Configuration): Provider<ComponentDependencyResolver> {
        return configuration.getIncoming().getResolutionResult().getRootComponent()
            .map<ComponentDependencyResolver>(Transformer { root: ResolvedComponentResult? -> VersionMappingComponentDependencyResolver(projectDependencyResolver, root!!) })
    }

    /**
     * Adapts a [ComponentDependencyResolver] to a [VariantDependencyResolver]
     * by returning component-precision coordinates.
     */
    private class VariantResolverAdapter(private val delegate: ComponentDependencyResolver) : VariantDependencyResolver {
        override fun resolveVariantCoordinates(dependency: ExternalDependency, warnings: VariantWarningCollector): ResolvedCoordinates? {
            return delegate.resolveComponentCoordinates(dependency)
        }

        override fun resolveVariantCoordinates(dependency: ProjectDependency, warnings: VariantWarningCollector): ResolvedCoordinates {
            return delegate.resolveComponentCoordinates(dependency)
        }
    }

    /**
     * A [ComponentDependencyResolver] which does not depend on resolving a dependency graph.
     */
    @VisibleForTesting
    internal class ProjectOnlyComponentDependencyResolver(private val projectDependencyResolver: ProjectDependencyPublicationResolver) : ComponentDependencyResolver {
        override fun resolveComponentCoordinates(dependency: ExternalDependency): ResolvedCoordinates? {
            return null
        }

        override fun resolveComponentCoordinates(dependency: ProjectDependency): ResolvedCoordinates {
            val identityPath = (dependency as ProjectDependencyInternal).getTargetProjectIdentity().getBuildTreePath()
            return ResolvedCoordinates.Companion.create(projectDependencyResolver.resolveComponent<ModuleVersionIdentifier?>(ModuleVersionIdentifier::class.java, identityPath))
        }

        override fun resolveComponentCoordinates(dependency: DependencyConstraint): ResolvedCoordinates? {
            return null
        }

        override fun resolveComponentCoordinates(dependency: DefaultProjectDependencyConstraint): ResolvedCoordinates {
            return resolveComponentCoordinates(dependency.projectDependency)
        }
    }

    companion object {
        /**
         * Determines whether we implement publication versionMapping with the legacy implementation
         * or the new dependency mapping implementation.
         *
         * TODO: While this is currently static, we should selectively enable it in order to run
         * versionMapping tests against both implementations.
         *
         * TODO: Once dependency mapping is stabilized, we should be able to turn this off / remove it entirely
         */
        private const val USE_LEGACY_VERSION_MAPPING = true
    }
}
