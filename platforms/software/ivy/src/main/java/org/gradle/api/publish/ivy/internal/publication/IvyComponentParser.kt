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
package org.gradle.api.publish.ivy.internal.publication

import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Strings
import com.google.common.collect.ImmutableList
import org.gradle.api.GradleException
import org.gradle.api.Transformer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.component.SoftwareComponentVariant
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.artifacts.dsl.dependencies.PlatformSupport
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.ExactVersionSelector.Companion.isExact
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.api.internal.component.UsageContext
import org.gradle.api.internal.provider.MergeProvider
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging.getLogger
import org.gradle.api.provider.Provider
import org.gradle.api.publish.internal.component.IvyPublishingAwareVariant
import org.gradle.api.publish.internal.mapping.DependencyCoordinateResolverFactory
import org.gradle.api.publish.internal.mapping.ResolvedCoordinates
import org.gradle.api.publish.internal.mapping.VariantDependencyResolver
import org.gradle.api.publish.internal.validation.PublicationErrorChecker
import org.gradle.api.publish.internal.validation.PublicationWarningsCollector
import org.gradle.api.publish.internal.validation.VariantWarningCollector
import org.gradle.api.publish.internal.versionmapping.VersionMappingStrategyInternal
import org.gradle.api.publish.ivy.IvyArtifact
import org.gradle.api.publish.ivy.IvyConfiguration
import org.gradle.api.publish.ivy.IvyConfigurationContainer
import org.gradle.api.publish.ivy.internal.dependency.DefaultIvyDependency
import org.gradle.api.publish.ivy.internal.dependency.DefaultIvyDependencySet
import org.gradle.api.publish.ivy.internal.dependency.DefaultIvyExcludeRule
import org.gradle.api.publish.ivy.internal.dependency.IvyDependency
import org.gradle.api.publish.ivy.internal.dependency.IvyExcludeRule
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.typeconversion.NotationParser
import javax.inject.Inject

/**
 * Encapsulates all logic required to extract data from a [SoftwareComponentInternal] in order to
 * transform it to a representation compatible with Ivy.
 */
class IvyComponentParser @Inject constructor(
    private val instantiator: Instantiator,
    private val platformSupport: PlatformSupport,
    private val ivyArtifactParser: NotationParser<Any, IvyArtifact>,
    private val documentationRegistry: DocumentationRegistry,
    private val collectionCallbackActionDecorator: CollectionCallbackActionDecorator,
    private val dependencyCoordinateResolverFactory: DependencyCoordinateResolverFactory
) {
    fun parseConfigurations(component: SoftwareComponentInternal): IvyConfigurationContainer {
        val configurations
                : IvyConfigurationContainer = instantiator.newInstance<DefaultIvyConfigurationContainer>(DefaultIvyConfigurationContainer::class.java, instantiator, collectionCallbackActionDecorator)

        val defaultConfiguration: IvyConfiguration = configurations.maybeCreate("default")!!
        for (variant in component.getUsages()) {
            val conf: String = mapVariantNameToIvyConfiguration(variant.getName())
            configurations.maybeCreate(conf)
            if (defaultShouldExtend(variant)) {
                defaultConfiguration.extend(conf)
            }
        }

        return configurations
    }

    fun parseArtifacts(component: SoftwareComponentInternal): MutableSet<IvyArtifact> {
        val artifacts: MutableSet<IvyArtifact> = LinkedHashSet<IvyArtifact>()

        val seenArtifacts: MutableMap<String, IvyArtifact> = HashMap<String, IvyArtifact>()
        for (variant in component.getUsages()) {
            val conf: String = mapVariantNameToIvyConfiguration(variant.getName())
            for (publishArtifact in variant.getArtifacts()) {
                val key: String = artifactKey(publishArtifact)
                var ivyArtifact = seenArtifacts.get(key)
                if (ivyArtifact == null) {
                    ivyArtifact = ivyArtifactParser.parseNotation(publishArtifact)
                    ivyArtifact.setConf(conf)
                    seenArtifacts.put(key, ivyArtifact)
                    artifacts.add(ivyArtifact)
                } else {
                    ivyArtifact.setConf(ivyArtifact.conf + "," + conf)
                }
            }
        }

        return artifacts
    }

    fun parseDependencies(component: SoftwareComponentInternal, versionMappingStrategy: VersionMappingStrategyInternal): Provider<ParsedDependencyResult> {
        PublicationErrorChecker.checkForUnpublishableAttributes(component, documentationRegistry)

        val parsedVariants: MutableList<Provider<ParsedVariantDependencyResult>> = component.getUsages().stream()
            .map<Provider<ParsedVariantDependencyResult>> { variant: UsageContext ->
                dependencyCoordinateResolverFactory
                    .createCoordinateResolvers(variant, versionMappingStrategy)!!
                    .map<ParsedVariantDependencyResult>(Transformer { resolvers: DependencyCoordinateResolverFactory.DependencyResolvers? ->
                        getDependenciesForVariant(
                            variant,
                            resolvers!!.variantResolver,
                            platformSupport
                        )
                    })
            }
            .collect(ImmutableList.toImmutableList<Provider<ParsedVariantDependencyResult>>())

        return MergeProvider<ParsedVariantDependencyResult?>(parsedVariants).map<ParsedDependencyResult>(Transformer { variants: MutableList<ParsedVariantDependencyResult?>? ->
            val ivyDependencies = instantiator.newInstance<DefaultIvyDependencySet>(DefaultIvyDependencySet::class.java, collectionCallbackActionDecorator)
            val warnings: MutableMap<String, VariantWarningCollector> = HashMap<String, VariantWarningCollector>()

            for (variant in variants!!) {
                ivyDependencies.addAll(variant.dependencies)
                warnings.put(variant.name, variant.warnings)
            }
            ParsedDependencyResult(
                ivyDependencies,
                PublicationWarningsCollector(warnings, LOG, UNSUPPORTED_FEATURE, "", PUBLICATION_WARNING_FOOTER, "suppressIvyMetadataWarningsFor")
            )
        })
    }

    fun parseGlobalExcludes(component: SoftwareComponentInternal): MutableSet<IvyExcludeRule> {
        val globalExcludes: MutableSet<IvyExcludeRule> = LinkedHashSet<IvyExcludeRule>()

        for (variant in component.getUsages()) {
            val conf: String = mapVariantNameToIvyConfiguration(variant.getName())
            for (excludeRule in variant.getGlobalExcludes()) {
                globalExcludes.add(DefaultIvyExcludeRule(excludeRule, conf))
            }
        }

        return globalExcludes
    }

    private class VariantDependencyFactory(
        private val dependencyResolver: VariantDependencyResolver,
        private val warnings: VariantWarningCollector
    ) {
        fun convertDependency(dependency: ModuleDependency, confMapping: String): IvyDependency {
            val coordinates = resolveDependency(dependency)

            var revConstraint: String? = null
            if ((dependency !is ProjectDependency) && dependency.getVersion() != null &&
                Companion.isDynamicVersion(dependency.getVersion()!!)
            ) {
                revConstraint = dependency.getVersion()
            }

            return DefaultIvyDependency(
                coordinates.group,
                coordinates.name,
                Strings.nullToEmpty(coordinates.version),
                confMapping,
                dependency.isTransitive(),
                revConstraint,
                dependency.getArtifacts(),
                dependency.getExcludeRules()
            )
        }

        fun resolveDependency(dependency: ModuleDependency): ResolvedCoordinates {
            if (!dependency.getAttributes().isEmpty()) {
                warnings.addUnsupported(String.format("dependency on %s declared with Gradle attributes", dependency))
            }
            if (!dependency.getCapabilitySelectors().isEmpty()) {
                warnings.addUnsupported(String.format("dependency on %s declared with Gradle capabilities", dependency))
            }

            if (dependency is ProjectDependency) {
                return dependencyResolver.resolveVariantCoordinates(dependency, warnings)!!
            } else if (dependency is ExternalDependency) {
                val coordinates = dependencyResolver.resolveVariantCoordinates(dependency, warnings)
                if (coordinates != null) {
                    return coordinates
                }

                return convertDeclaredCoordinates(dependency.getGroup(), dependency.getName(), dependency.getVersion())
            } else {
                throw GradleException("Unsupported dependency type: " + dependency.javaClass.getName())
            }
        }

        fun convertDeclaredCoordinates(organization: String, module: String, version: String?): ResolvedCoordinates {
            if (version == null) {
                warnings.addUnsupported(String.format("%s:%s declared without version", organization, module))
            }

            return ResolvedCoordinates.create(organization, module, version)
        }

        companion object {
            private fun isDynamicVersion(version: String): Boolean {
                return !isExact(version)
            }
        }
    }

    /**
     * Parsed dependencies for all variants.
     */
    class ParsedDependencyResult(
        val dependencies: DefaultIvyDependencySet,
        val warnings: PublicationWarningsCollector
    )

    /**
     * Parsed dependencies for a single variant.
     */
    private class ParsedVariantDependencyResult(
        private val name: String,
        private val dependencies: MutableList<IvyDependency>,
        private val warnings: VariantWarningCollector
    )

    companion object {
        @VisibleForTesting
        const val UNSUPPORTED_FEATURE: String = " contains dependencies that cannot be represented in a published ivy descriptor."

        @VisibleForTesting
        val PUBLICATION_WARNING_FOOTER: String = "These issues indicate information that is lost in the published 'ivy.xml' metadata file, " +
                "which may be an issue if the published library is consumed by an old Gradle version or Apache Ivy.\n" +
                "The 'module' metadata file, which is used by Gradle 6+ is not affected."

        private val LOG: Logger = getLogger(IvyComponentParser::class.java)!!

        private const val API_VARIANT = "api"
        private const val API_ELEMENTS_VARIANT = "apiElements"
        private const val RUNTIME_VARIANT = "runtime"
        private const val RUNTIME_ELEMENTS_VARIANT = "runtimeElements"

        /**
         * In general, default extends all configurations such that you get 'everything' when depending on default.
         * If a variant is optional, however it is not included.
         * If a variant represents the Java API variant, it is also not included, because the Java Runtime variant already includes everything
         * (including both also works but would lead to some duplication, that might break backwards compatibility in certain cases).
         */
        private fun defaultShouldExtend(variant: SoftwareComponentVariant): Boolean {
            if (variant !is IvyPublishingAwareVariant) {
                return true
            }
            if (variant.isOptional) {
                return false
            }
            return !isJavaApiVariant(variant.getName())
        }

        private fun isJavaRuntimeVariant(variantName: String): Boolean {
            return RUNTIME_VARIANT == variantName || RUNTIME_ELEMENTS_VARIANT == variantName
        }

        private fun isJavaApiVariant(variantName: String): Boolean {
            return API_VARIANT == variantName || API_ELEMENTS_VARIANT == variantName
        }

        private fun artifactKey(publishArtifact: PublishArtifact): String {
            return publishArtifact.getName() + ":" + publishArtifact.getType() + ":" + publishArtifact.getExtension() + ":" + publishArtifact.getClassifier()
        }

        private fun getDependenciesForVariant(
            variant: SoftwareComponentVariant,
            dependencyResolver: VariantDependencyResolver,
            platformSupport: PlatformSupport
        ): ParsedVariantDependencyResult {
            val warnings = VariantWarningCollector()
            val dependencies = variant.getDependencies()
            val ivyDependencies: MutableList<IvyDependency> = ArrayList<IvyDependency>(dependencies.size)

            val dependencyFactory = VariantDependencyFactory(dependencyResolver, warnings)

            for (dependency in dependencies) {
                val confMapping: String = confMappingFor(variant, dependency)
                if (platformSupport.isTargetingPlatform(dependency)) {
                    warnings.addUnsupported(String.format("%s:%s:%s declared as platform", dependency.getGroup(), dependency.getName(), dependency.getVersion()))
                }
                ivyDependencies.add(dependencyFactory.convertDependency(dependency, confMapping))
            }

            if (!variant.getDependencyConstraints().isEmpty()) {
                for (constraint in variant.getDependencyConstraints()) {
                    warnings.addUnsupported(String.format("%s:%s:%s declared as a dependency constraint", constraint.getGroup(), constraint.getName(), constraint.getVersion()))
                }
            }
            if (!variant.getCapabilities().isEmpty()) {
                for (capability in variant.getCapabilities()) {
                    warnings.addVariantUnsupported(String.format("Declares capability %s:%s:%s which cannot be mapped to Ivy", capability.getGroup(), capability.getName(), capability.getVersion()))
                }
            }

            return ParsedVariantDependencyResult(variant.getName(), ivyDependencies, warnings)
        }

        private fun confMappingFor(variant: SoftwareComponentVariant, dependency: ModuleDependency): String {
            val conf: String = mapVariantNameToIvyConfiguration(variant.getName())
            val targetConfiguration = dependency.getTargetConfiguration()
            val confMappingTarget = if (targetConfiguration == null) Dependency.DEFAULT_CONFIGURATION else Companion.mapVariantNameToIvyConfiguration(dependency.getTargetConfiguration()!!)

            // If the following code is activated implementation/runtime separation will be published to ivy. This however is a breaking change.
            //
            // if (confMappingTarget == null) {
            //     if (variant instanceof MavenPublishingAwareVariant) {
            //         MavenPublishingAwareContext.ScopeMapping mapping = ((MavenPublishingAwareVariant) variant).getScopeMapping();
            //         if (mapping == runtime || mapping == runtime_optional) {
            //             confMappingTarget = "runtime";
            //         }
            //         if (mapping == compile || mapping == compile_optional) {
            //             confMappingTarget = "compile";
            //         }
            //     }
            // }
            return conf + "->" + confMappingTarget
        }

        /**
         * The variant name usually corresponds to the name of the Gradle configuration on which the variant is based on.
         * For backward compatibility, the 'apiElements' and 'runtimeElements' configurations/variants of the Java ecosystem are named 'compile' and 'runtime' in the publication.
         */
        private fun mapVariantNameToIvyConfiguration(variantName: String): String {
            if (isJavaApiVariant(variantName)) {
                return "compile"
            }
            if (isJavaRuntimeVariant(variantName)) {
                return "runtime"
            }
            return variantName
        }
    }
}
