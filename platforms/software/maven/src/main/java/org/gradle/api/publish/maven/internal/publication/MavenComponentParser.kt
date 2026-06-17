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
package org.gradle.api.publish.maven.internal.publication

import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableList
import com.google.common.collect.Sets
import org.gradle.api.GradleException
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.Transformer
import org.gradle.api.artifacts.DependencyConstraint
import org.gradle.api.artifacts.ExcludeRule
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.capabilities.Capability
import org.gradle.api.component.SoftwareComponentVariant
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.artifacts.DefaultExcludeRule
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependencyConstraint
import org.gradle.api.internal.artifacts.dsl.dependencies.PlatformSupport
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionSelectorScheme.Companion.isLatestVersion
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionSelectorScheme.Companion.isSubVersion
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.MavenVersionSelectorScheme.Companion.isSubstituableLatest
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.api.internal.provider.MergeProvider
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging.getLogger
import org.gradle.api.provider.Provider
import org.gradle.api.publish.internal.component.MavenPublishingAwareVariant
import org.gradle.api.publish.internal.mapping.ComponentDependencyResolver
import org.gradle.api.publish.internal.mapping.DependencyCoordinateResolverFactory
import org.gradle.api.publish.internal.mapping.ResolvedCoordinates
import org.gradle.api.publish.internal.mapping.VariantDependencyResolver
import org.gradle.api.publish.internal.validation.PublicationWarningsCollector
import org.gradle.api.publish.internal.validation.VariantWarningCollector
import org.gradle.api.publish.internal.versionmapping.VersionMappingStrategyInternal
import org.gradle.api.publish.maven.MavenArtifact
import org.gradle.api.publish.maven.internal.dependencies.DefaultMavenDependency
import org.gradle.api.publish.maven.internal.dependencies.DefaultMavenPomDependencies
import org.gradle.api.publish.maven.internal.dependencies.MavenDependency
import org.gradle.api.publish.maven.internal.dependencies.MavenPomDependencies
import org.gradle.api.publish.maven.internal.dependencies.VersionRangeMapper
import org.gradle.internal.typeconversion.NotationParser
import java.io.File
import java.util.Objects
import java.util.function.Consumer
import java.util.stream.Collectors
import java.util.stream.Stream
import javax.inject.Inject

/**
 * Encapsulates all logic required to extract data from a [SoftwareComponentInternal] in order to
 * transform it to a representation compatible with Maven.
 */
class MavenComponentParser @Inject constructor(
    private val platformSupport: PlatformSupport,
    private val versionRangeMapper: VersionRangeMapper,
    private val documentationRegistry: DocumentationRegistry,
    private val mavenArtifactParser: NotationParser<Any, MavenArtifact>,
    private val dependencyCoordinateResolverFactory: DependencyCoordinateResolverFactory
) {
    fun parseArtifacts(component: SoftwareComponentInternal): MutableSet<MavenArtifact> {
        // TODO Artifact names should be determined by the source variant. We shouldn't
        //      blindly "pass-through" the artifact file name.
        val seenArtifacts: MutableSet<ArtifactKey> = HashSet<ArtifactKey>()
        return createSortedVariantsStream(component)
            .flatMap { variant: SoftwareComponentVariant -> variant.getArtifacts().stream() }
            .filter { artifact: PublishArtifact? ->
                val key = ArtifactKey(artifact!!.getFile(), artifact.getClassifier(), artifact.getExtension())
                seenArtifacts.add(key)
            }
            .map<MavenArtifact> { notation: N? -> mavenArtifactParser.parseNotation(notation) }
            .collect(Collectors.toSet())
    }

    fun parseDependencies(
        component: SoftwareComponentInternal,
        versionMappingStrategy: VersionMappingStrategyInternal,
        coordinates: ModuleVersionIdentifier
    ): Provider<ParsedDependencyResult> {
        checkForUnpublishableAttributes(component, documentationRegistry)

        val parsedVariants: MutableList<Provider<ParsedVariantDependencyResult>> = createSortedVariantsStream(component)
            .map<Provider<ParsedVariantDependencyResult>> { variant: SoftwareComponentVariant ->
                dependencyCoordinateResolverFactory
                    .createCoordinateResolvers(variant, versionMappingStrategy)!!
                    .map<ParsedVariantDependencyResult>(Transformer { resolvers: DependencyCoordinateResolverFactory.DependencyResolvers? ->
                        getDependenciesForVariant(
                            variant,
                            resolvers!!,
                            coordinates
                        )
                    })
            }.collect(
                Collectors.toList()
            )

        return MergeProvider<ParsedVariantDependencyResult?>(parsedVariants).map<ParsedDependencyResult>(Transformer { variants: MutableList<ParsedVariantDependencyResult?>? ->
            val dependencies: MutableList<MavenDependency> = ArrayList<MavenDependency>()
            val constraints: MutableList<MavenDependency> = ArrayList<MavenDependency>()
            val platforms: MutableList<MavenDependency> = ArrayList<MavenDependency>()

            val seenDependencies: MutableSet<MavenDependencyKey> = HashSet<MavenDependencyKey>()
            val seenPlatforms: MutableSet<MavenDependencyKey> = HashSet<MavenDependencyKey>()
            val seenConstraints: MutableSet<MavenDependencyKey> = HashSet<MavenDependencyKey>()

            val warnings: MutableMap<String, VariantWarningCollector> = HashMap<String, VariantWarningCollector>()

            for (variant in variants!!) {
                for (dependency in variant.dependencies) {
                    if (seenDependencies.add(MavenDependencyKey.Companion.of(dependency))) {
                        dependencies.add(dependency)
                    }
                }
                for (platform in variant.platforms) {
                    if (seenPlatforms.add(MavenDependencyKey.Companion.of(platform))) {
                        platforms.add(platform)
                    }
                }
                for (dep in variant.constraints) {
                    if (seenConstraints.add(MavenDependencyKey.Companion.of(dep))) {
                        constraints.add(dep)
                    }
                }

                warnings.put(variant.name, variant.warnings)
            }
            ParsedDependencyResult(
                DefaultMavenPomDependencies(
                    ImmutableList.copyOf<MavenDependency>(dependencies),
                    ImmutableList.builder<MavenDependency>().addAll(constraints).addAll(platforms).build()
                ),
                PublicationWarningsCollector(warnings, LOG, UNSUPPORTED_FEATURE, INCOMPATIBLE_FEATURE, PUBLICATION_WARNING_FOOTER, "suppressPomMetadataWarningsFor")
            )
        })
    }

    private fun getDependenciesForVariant(
        variant: SoftwareComponentVariant,
        resolvers: DependencyCoordinateResolverFactory.DependencyResolvers,
        coordinates: ModuleVersionIdentifier
    ): ParsedVariantDependencyResult {
        val warnings = VariantWarningCollector()

        val dependencies: MutableList<MavenDependency> = ArrayList<MavenDependency>()
        val constraints: MutableList<MavenDependency> = ArrayList<MavenDependency>()
        val platforms: MutableList<MavenDependency> = ArrayList<MavenDependency>()

        val scopeMapping = MavenPublishingAwareVariant.scopeForVariant(variant)
        val scope = scopeMapping.scope
        val optional = scopeMapping.isOptional
        val globalExcludes = variant.getGlobalExcludes()

        val dependencyFactory = MavenDependencyFactory(
            warnings,
            resolvers.variantResolver,
            resolvers.componentResolver,
            versionRangeMapper,
            scope,
            optional,
            globalExcludes
        )

        for (dependency in variant.getDependencies()) {
            if (platformSupport.isTargetingPlatform(dependency)) {
                dependencyFactory.convertImportDependencyConstraint(dependency, Consumer { e: MavenDependency -> platforms.add(e) })
            } else {
                dependencyFactory.convertDependency(dependency, Consumer { d: MavenDependency ->
                    if (!isDependencyWithDefaultArtifact(d) || !dependencyMatchesProject(d, coordinates)) {
                        dependencies.add(d)
                    }
                })
            }
        }

        for (dependency in variant.getDependencyConstraints()) {
            if (dependency is DefaultProjectDependencyConstraint || dependency.getVersion() != null) {
                dependencyFactory.convertDependencyConstraint(dependency, Consumer { e: MavenDependency -> constraints.add(e) })
            } else {
                // Some dependency constraints, like those with rejectAll() have no version and do not map to Maven.
                warnings.addIncompatible(String.format("constraint %s:%s declared with a Maven incompatible version notation", dependency.getGroup(), dependency.getName()))
            }
        }

        if (!variant.getCapabilities().isEmpty()) {
            for (capability in variant.getCapabilities()) {
                if (isNotDefaultCapability(capability, coordinates)) {
                    warnings.addVariantUnsupported(String.format("Declares capability %s:%s:%s which cannot be mapped to Maven", capability.getGroup(), capability.getName(), capability.getVersion()))
                }
            }
        }

        return ParsedVariantDependencyResult(variant.getName(), dependencies, platforms, constraints, warnings)
    }

    /**
     * Converts the DSL representation of a variant's dependencies to one suitable for a POM.
     * Dependencies are transformed by querying the provided [VariantDependencyResolver]
     * for their resolved coordinates.
     */
    private class MavenDependencyFactory(
        private val warnings: VariantWarningCollector,
        private val variantDependencyResolver: VariantDependencyResolver,
        private val componentDependencyResolver: ComponentDependencyResolver,
        private val versionRangeMapper: VersionRangeMapper,
        private val scope: String,
        private val optional: Boolean,
        private val globalExcludes: MutableSet<ExcludeRule>
    ) {
        fun convertDependency(dependency: ModuleDependency, collector: Consumer<MavenDependency>) {
            // TODO: These warnings are not very useful. There are cases where a dependency declared
            // with attributes or capabilities is correctly converted to maven coordinates -- even
            // when dependency mapping is disabled.
            // At the very least, we do not want these warnings when dependency mapping is enabled.

            if (!dependency.getAttributes().isEmpty()) {
                warnings.addUnsupported(String.format("dependency on %s declared with Gradle attributes", dependency))
            }
            if (!dependency.getCapabilitySelectors().isEmpty()) {
                warnings.addUnsupported(String.format("dependency on %s declared with Gradle capabilities", dependency))
            }

            val allExcludeRules: MutableSet<ExcludeRule> = getExcludeRules(globalExcludes, dependency)

            if (dependency.getArtifacts().isEmpty()) {
                val coordinates = resolveDependency(dependency, true)
                collector.accept(newDependency(coordinates, null, null, scope, allExcludeRules, optional))
                return
            }

            // If the dependency has artifacts, only map the coordinates to component-level precision.
            // This is so we match the dependency-management behavior where an explicit artifact on a dependency
            // that would otherwise map to different coordinates resolves to the declared coordinates.
            val coordinates = resolveDependency(dependency, false)
            for (artifact in dependency.getArtifacts()) {
                if (artifact.getName() != dependency.getName()) {
                    throw InvalidUserCodeException(
                        "Cannot publish a dependency with an artifact name different from the dependency's artifactId. " +
                                "This functionality is only supported by Ivy repositories. " + String.format(
                            "Declare a dependency with artifactId '%s' instead of '%s'.",
                            artifact.getName(),
                            dependency.getName()
                        )
                    )
                }

                collector.accept(newDependency(coordinates, artifact.getType(), artifact.getClassifier(), scope, allExcludeRules, optional))
            }
        }

        fun convertDependencyConstraint(dependency: DependencyConstraint, collector: Consumer<MavenDependency>) {
            // We use component-level precision for dependency constraints since it is hard to implement correctly.
            // To publish a dependency constraint to Maven, we would need to publish a constraint for _each_ coordinate
            // that the component could be resolved to. Resolution results do not support this type of query, so this
            // remains incomplete for now.

            var identifier: ResolvedCoordinates?
            if (dependency is DefaultProjectDependencyConstraint) {
                identifier = componentDependencyResolver.resolveComponentCoordinates(dependency)
            } else {
                identifier = componentDependencyResolver.resolveComponentCoordinates(dependency)
                if (identifier == null) {
                    identifier = convertDeclaredCoordinates(dependency.getGroup(), dependency.getName(), dependency.getVersion())
                }
            }

            if (identifier!!.version == null) {
                return
            }

            // Do not publish scope, as it has too different of semantics in Maven
            collector.accept(Companion.newDependency(identifier, null, null, null, mutableSetOf<ExcludeRule>(), false))
        }

        fun convertImportDependencyConstraint(dependency: ModuleDependency, collector: Consumer<MavenDependency>) {
            val identifier = resolveDependency(dependency, true)
            collector.accept(newDependency(identifier, "pom", null, "import", mutableSetOf<ExcludeRule>(), false))
        }

        fun resolveDependency(dependency: ModuleDependency, variantPrecision: Boolean): ResolvedCoordinates {
            if (dependency is ProjectDependency) {
                return variantDependencyResolver.resolveVariantCoordinates(dependency, warnings)!!
            } else if (dependency is ExternalDependency) {
                val identifier: ResolvedCoordinates?
                if (variantPrecision) {
                    identifier = variantDependencyResolver.resolveVariantCoordinates(dependency, warnings)
                } else {
                    identifier = componentDependencyResolver.resolveComponentCoordinates(dependency)
                }

                if (identifier != null) {
                    return identifier
                }

                return convertDeclaredCoordinates(dependency.getGroup(), dependency.getName(), dependency.getVersion())
            } else {
                throw GradleException("Unsupported dependency type: " + dependency.javaClass.getName())
            }
        }

        fun convertDeclaredCoordinates(groupId: String, artifactId: String, version: String?): ResolvedCoordinates {
            if (version == null) {
                return ResolvedCoordinates.create(groupId, artifactId, null)
            }

            // Attempt to convert Gradle's rich version notation to Maven's.
            if (isSubVersion(version) ||
                (isLatestVersion(version) && !isSubstituableLatest(version))
            ) {
                warnings.addIncompatible(String.format("%s:%s:%s declared with a Maven incompatible version notation", groupId, artifactId, version))
            }

            return ResolvedCoordinates.create(
                groupId, artifactId, versionRangeMapper.map(version)
            )
        }

        companion object {
            private fun newDependency(
                coordinates: ResolvedCoordinates,
                type: String?,
                classifier: String?,
                scope: String?,
                excludeRules: MutableSet<ExcludeRule>,
                optional: Boolean
            ): MavenDependency {
                return DefaultMavenDependency(
                    coordinates.group, coordinates.name, coordinates.version,
                    type, classifier, scope, excludeRules, optional
                )
            }

            private fun getExcludeRules(globalExcludes: MutableSet<ExcludeRule>, dependency: ModuleDependency): MutableSet<ExcludeRule> {
                if (!dependency.isTransitive()) {
                    return EXCLUDE_ALL_RULE
                }

                val excludeRules = dependency.getExcludeRules()
                if (excludeRules.isEmpty()) {
                    return globalExcludes
                }

                if (globalExcludes.isEmpty()) {
                    return excludeRules
                }

                return Sets.union<ExcludeRule>(globalExcludes, excludeRules)
            }
        }
    }

    /**
     * Parsed dependencies for all variants.
     */
    class ParsedDependencyResult(
        val dependencies: MavenPomDependencies,
        val warnings: PublicationWarningsCollector
    )

    /**
     * Parsed dependencies for a single variant.
     */
    private class ParsedVariantDependencyResult(
        private val name: String,
        private val dependencies: MutableList<MavenDependency>,
        private val platforms: MutableList<MavenDependency>,
        private val constraints: MutableList<MavenDependency>,
        private val warnings: VariantWarningCollector
    )

    private class ArtifactKey(val file: File, val classifier: String?, val extension: String?) {
        override fun equals(obj: Any): Boolean {
            if (obj !is ArtifactKey) {
                return false
            }
            val other = obj
            return file == other.file && classifier == other.classifier && extension == other.extension
        }

        override fun hashCode(): Int {
            return file.hashCode() xor Objects.hash(classifier, extension)
        }
    }

    /**
     * This is used to de-duplicate dependencies based on relevant contents.
     * In particular, version, scope, and optional are ignored.
     */
    private class MavenDependencyKey(
        private val group: String,
        private val name: String,
        private val type: String?,
        private val classifier: String?
    ) {
        override fun equals(o: Any): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }
            val that = o as MavenDependencyKey
            return group == that.group &&
                    name == that.name &&
                    type == that.type &&
                    classifier == that.classifier
        }

        override fun hashCode(): Int {
            return Objects.hash(group, name, type, classifier)
        }

        companion object {
            fun of(dep: MavenDependency): MavenDependencyKey {
                return MavenDependencyKey(
                    dep.getGroupId(),
                    dep.getArtifactId(),
                    dep.getType(),
                    dep.getClassifier()
                )
            }
        }
    }

    companion object {
        @VisibleForTesting
        const val INCOMPATIBLE_FEATURE: String = " contains dependencies that will produce a pom file that cannot be consumed by a Maven client."

        @VisibleForTesting
        const val UNSUPPORTED_FEATURE: String = " contains dependencies that cannot be represented in a published pom file."

        @VisibleForTesting
        val PUBLICATION_WARNING_FOOTER: String = "These issues indicate information that is lost in the published 'pom' metadata file, " +
                "which may be an issue if the published library is consumed by an old Gradle version or Apache Maven.\n" +
                "The 'module' metadata file, which is used by Gradle 6+ is not affected."

        /*
     * Maven supports wildcards in exclusion rules according to:
     * http://www.smartjava.org/content/maven-and-wildcard-exclusions
     * https://issues.apache.org/jira/browse/MNG-3832
     * This should be used for non-transitive dependencies
     */
        private val EXCLUDE_ALL_RULE = mutableSetOf<ExcludeRule>(DefaultExcludeRule("*", "*"))

        private val LOG: Logger = getLogger(MavenComponentParser::class.java)!!

        private fun isNotDefaultCapability(capability: Capability, coordinates: ModuleVersionIdentifier): Boolean {
            return (coordinates.getGroup() != capability.getGroup()) || (coordinates.getName() != capability.getName()) || (coordinates.getVersion() != capability.getVersion())
        }

        private fun isDependencyWithDefaultArtifact(dependency: MavenDependency): Boolean {
            return dependency.getType() == null && dependency.getClassifier() == null
        }

        private fun dependencyMatchesProject(dependency: MavenDependency, coordinates: ModuleVersionIdentifier): Boolean {
            return coordinates.getModule() == DefaultModuleIdentifier.newId(dependency.getGroupId(), dependency.getArtifactId())
        }

        private fun createSortedVariantsStream(component: SoftwareComponentInternal): Stream<out SoftwareComponentVariant> {
            return component.getUsages().stream()
                .sorted(Comparator.comparing(MavenPublishingAwareVariant::scopeForVariant))
        }
    }
}
