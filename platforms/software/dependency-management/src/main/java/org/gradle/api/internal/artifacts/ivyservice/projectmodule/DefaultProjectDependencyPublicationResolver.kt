/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.projectmodule

import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.component.ComponentWithVariants
import org.gradle.api.component.SoftwareComponent
import org.gradle.api.component.SoftwareComponentVariant
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectStateRegistry
import org.gradle.execution.ProjectConfigurer
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.deprecation.DeprecationLogger.deprecateAction
import org.gradle.internal.lazy.Lazy
import org.gradle.internal.lazy.Lazy.Companion.locking
import org.gradle.internal.logging.text.TreeFormatter
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.util.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function
import java.util.stream.Collectors

/**
 * A service that will resolve a project identity path into publication coordinates.
 * This resolver can determine the coordinates of a project's root component
 * or can resolve the coordinates of a specific variant of that component.
 */
@ServiceScope(Scope.Build::class)
class DefaultProjectDependencyPublicationResolver(
    private val publicationRegistry: ProjectPublicationRegistry,
    private val projectConfigurer: ProjectConfigurer,
    private val projects: ProjectStateRegistry
) : ProjectDependencyPublicationResolver {
    private val resolverCache = VariantCoordinateResolverCache()

    override fun <T> resolveComponent(coordsType: Class<T?>, identityPath: Path): T? {
        return
        T > withCoordinateResolver<T?>(
            coordsType, identityPath,
            Function { obj: VariantCoordinateResolver<T?>? -> obj!!.componentCoordinates }
        )
    }

    override fun <T> resolveVariant(coordsType: Class<T?>, identityPath: Path, variantName: String): T? {
        return
        T > withCoordinateResolver<T?>(coordsType, identityPath, Function { resolver: VariantCoordinateResolver<T?>? -> resolver!!.getVariantCoordinates(variantName) }
        )
    }

    /**
     * Execute the action with a resolver for the given project.
     */
    private fun <T> withCoordinateResolver(coordsType: Class<T?>, identityPath: Path, action: Function<VariantCoordinateResolver<T?>, T?>): T? {
        val projectState = projects.stateFor(identityPath)

        // Ensure target project is configured
        projectConfigurer.configureFully(projectState)

        return projectState.fromMutableState<T?>(Function { project: ProjectInternal? ->
            val resolver = resolverCache.computeIfAbsent<T?>(identityPath, coordsType, { key -> }
            <T> createCoordinateResolver < T ? > (identityPath, coordsType, project)
            )
            action.apply(resolver)
        })
    }

    // It would be nice to get rid of the project parameter
    private fun <T> createCoordinateResolver(identityPath: Path, coordsType: Class<T?>, project: ProjectInternal): VariantCoordinateResolver<T?> {
        val publications: MutableMap<ProjectComponentPublication, T?>
        T > getPublications<T?>(identityPath, coordsType)

        if (publications.isEmpty()) {
            return VariantCoordinateResolver.Companion.fixed<T?>(TODO("Cannot convert element"))<T> org . gradle . api . internal . artifacts . ivyservice . projectmodule . DefaultProjectDependencyPublicationResolver . Companion . getImplicitCoordinates < T ? > (coordsType, project)
        }

        // For all published components, find those that are not children of other components.
        // These are the top-level publications.
        val topLevel: MutableSet<ProjectComponentPublication> = LinkedHashSet<ProjectComponentPublication>()
        val topLevelWithComponent: MutableSet<ProjectComponentPublication> = LinkedHashSet<ProjectComponentPublication>()
        val childComponents: MutableSet<SoftwareComponent> = getChildComponents(publications.keys)
        for (entry in publications.entries) {
            val publication = entry.key
            val component: SoftwareComponent? = publication.getComponent().getOrNull()
            if (!publication.isAlias() && !childComponents.contains(component)) {
                topLevel.add(publication)
                if (component != null) {
                    topLevelWithComponent.add(publication)
                }
            }
        }

        if (topLevelWithComponent.size == 1) {
            val singleComponent = topLevelWithComponent.iterator().next().getComponent().get()
            val componentCoordinates: MutableMap<SoftwareComponent, T?>
            T > getComponentCoordinates<T?>(coordsType, publications.keys)
            return MultiCoordinateVariantResolver<T?>(singleComponent, identityPath, componentCoordinates)
        }

        // See if all entry points have the same identifier
        return VariantCoordinateResolver.Companion.fixed<T?>(TODO("Cannot convert element"))<T> org . gradle . api . internal . artifacts . ivyservice . projectmodule . DefaultProjectDependencyPublicationResolver . Companion . getCommonCoordinates < T ? > (project, coordsType, topLevel)
    }

    /**
     * Given a project and a coordinate type, find all publications that publish a component
     * with the given coordinate type.
     */
    private fun <T> getPublications(identityPath: Path, coordsType: Class<T?>): MutableMap<ProjectComponentPublication, T?> {
        val allPublications = publicationRegistry.getPublicationsForProject<ProjectComponentPublication>(ProjectComponentPublication::class.java, identityPath)
        val publications: MutableMap<ProjectComponentPublication, T?> = LinkedHashMap<ProjectComponentPublication, T?>(allPublications.size)
        for (publication in allPublications) {
            val coordinates = publication.getCoordinates<T?>(coordsType)
            if (!publication.isLegacy() && coordinates != null) {
                publications.put(publication, coordinates)
            }
        }
        return publications
    }

    /**
     * Resolves the coordinates of variants of a single component
     */
    private interface VariantCoordinateResolver<T> {
        /**
         * Get the coordinates of the root component
         */
        val componentCoordinates: T?

        /**
         * Get the coordinates of the variant with given name
         */
        fun getVariantCoordinates(variantName: String): T?

        class FixedVariantCoordinateResolver<T> private constructor(private val coordinates: T?) : VariantCoordinateResolver<T?> {
            override fun getComponentCoordinates(): T? {
                return coordinates
            }

            override fun getVariantCoordinates(resolvedVariant: String): T? {
                return coordinates
            }
        }

        companion object {
            /**
             * Create a resolver that always returns the given coordinates
             */
            fun <T> fixed(coordinates: T?): VariantCoordinateResolver<T?> {
                return VariantCoordinateResolver.FixedVariantCoordinateResolver<T?>(coordinates)
            }
        }
    }

    /**
     * A [VariantCoordinateResolver] that supports composite components distributed across multiple coordinates
     */
    private class MultiCoordinateVariantResolver<T>(private val root: SoftwareComponent, identityPath: Path, private val componentCoordinates: MutableMap<SoftwareComponent, T?>) :
        VariantCoordinateResolver<T?> {
        private val variantCoordinatesMap: Lazy<MutableMap<String, T?>?>

        init {
            this.variantCoordinatesMap =
                locking().of<MutableMap<String, T?>?>(
                    {}<T> org . gradle . api . internal . artifacts . ivyservice . projectmodule . DefaultProjectDependencyPublicationResolver . MultiCoordinateVariantResolver . Companion . mapVariantNamesToCoordinates < T ? > (root,
                    componentCoordinates, identityPath
                ))
        }

        override fun getComponentCoordinates(): T? {
            return componentCoordinates.get(root)
        }

        override fun getVariantCoordinates(resolvedVariant: String): T? {
            return variantCoordinatesMap.get()!!.get(resolvedVariant)
        }

        companion object {
            private fun <T> mapVariantNamesToCoordinates(root: SoftwareComponent, componentsMap: MutableMap<SoftwareComponent, T?>, identityPath: Path): MutableMap<String, T?> {
                val result: MutableMap<String, T?> = HashMap<String, T?>()
                ComponentWalker.walkComponent<T?>(root, componentsMap, ComponentWalker.ComponentVisitor { variant: SoftwareComponentVariant?, coordinates: T? ->
                    if (result.put(variant!!.getName(), coordinates) != null) {
                        throw InvalidUserDataException(
                            String.format(
                                "Found multiple variants with name '%s' in component '%s' of project '%s'",
                                variant.getName(), root.getName(), identityPath
                            )
                        )
                    }
                })
                return result
            }
        }
    }

    /**
     * Walks a composite component and its subcomponents to determine coordinates of each variant.
     */
    private object ComponentWalker {
        /**
         * Visit every variant of a composite component
         */
        fun <T> walkComponent(component: SoftwareComponent, componentsMap: MutableMap<SoftwareComponent, T?>, visitor: ComponentVisitor<T?>) {
            T > walkComponent<T?>(component, componentsMap, LinkedHashSet<SoftwareComponent>(), HashSet<T?>(), visitor)
        }

        fun <T> walkComponent(
            component: SoftwareComponent,
            componentCoordinates: MutableMap<SoftwareComponent, T?>,
            componentsSeen: MutableSet<SoftwareComponent>,
            coordinatesSeen: MutableSet<T?>,
            visitor: ComponentVisitor<T?>
        ) {
            if (!componentsSeen.add(component)) {
                val allComponents = componentsSeen.stream()
                    .map<String> { obj: SoftwareComponent? -> obj!!.getName() }
                    .collect(Collectors.joining(", "))
                throw InvalidUserDataException("Circular dependency detected while resolving component coordinates. Found the following components: " + allComponents)
            }

            val coordinates = componentCoordinates.get(component)
            if (!coordinatesSeen.add(coordinates)) {
                throw InvalidUserDataException("Multiple child components may not share the same coordinates: " + coordinates)
            }

            // First visit the local variants
            if (component is SoftwareComponentInternal) {
                val componentInternal = component
                for (variant in componentInternal.getUsages()) {
                    visitor.visitVariant(variant, coordinates)
                }
            }

            // Then visit all child components' variants
            if (component is ComponentWithVariants) {
                val parent = component
                for (child in parent.getVariants()) {
                    T > walkComponent<T?>(child, componentCoordinates, componentsSeen, coordinatesSeen, visitor)
                }
            }
        }

        internal interface ComponentVisitor<T> {
            fun visitVariant(variant: SoftwareComponentVariant, coordinates: T?)
        }
    }

    private class VariantCoordinateResolverCache {
        private val cache: MutableMap<Key, VariantCoordinateResolver<*>> = ConcurrentHashMap<Key, VariantCoordinateResolver<*>>()

        fun <T> computeIfAbsent(identityPath: Path, coordsType: Class<T?>, factory: Function<Key, VariantCoordinateResolver<T?>>): VariantCoordinateResolver<T?> {
            val key = Key(identityPath, coordsType)
            val result = cache.computeIfAbsent(key, factory)
            return uncheckedCast<VariantCoordinateResolver<T?>?>(result)!!
        }

        private class Key(private val identityPath: Path, private val coordsType: Class<*>) {
            override fun equals(o: Any): Boolean {
                if (this === o) {
                    return true
                }
                if (o == null || javaClass != o.javaClass) {
                    return false
                }
                val key = o as Key
                return identityPath == key.identityPath && coordsType == key.coordsType
            }

            override fun hashCode(): Int {
                return identityPath.hashCode() xor coordsType.hashCode()
            }
        }
    }

    companion object {
        /**
         * Get the coordinates of a project that has no publications.
         */
        private fun <T> getImplicitCoordinates(coordsType: Class<T?>, project: Project): T? {
            if (coordsType == ModuleVersionIdentifier::class.java) {
                deprecateAction("Declaring a dependency on an unpublished project")
                    .withContext("A dependency was declared on " + project.getDisplayName() + ", but that project does not declare any publications.")!!
                    .withAdvice("Ensure " + project.getDisplayName() + " declares at least one publication.")!!
                    .willBecomeAnErrorInGradle10()
                    .withUpgradeGuideSection(9, "publishing_dependency_on_unpublished_project")!!
                    .nagUser()

                // These synthetic coordinates are problematic, since they are not the real coordinates of the target project. The target project is not actually published.
                // We should throw an exception here in all cases in Gradle 10, instead requiring the user to declare at least one publication in the target project.
                return coordsType.cast(DefaultModuleVersionIdentifier.newId(project.getGroup().toString(), project.getName(), project.getVersion().toString()))
            }

            throw UnsupportedOperationException(String.format("Could not find any publications of type %s in %s.", coordsType.getSimpleName(), project.getDisplayName()))
        }

        /**
         * Try to find a single set of coordinates shared by all top-level publications.
         */
        private fun <T> getCommonCoordinates(project: Project, coordsType: Class<T?>, topLevel: MutableCollection<ProjectComponentPublication>): T? {
            val iterator = topLevel.iterator()
            val candidate = iterator.next().getCoordinates<T?>(coordsType)
            while (iterator.hasNext()) {
                val alternative = iterator.next().getCoordinates<T?>(coordsType)
                if (candidate != alternative) {
                    val formatter = TreeFormatter()
                    formatter.node("Publishing is not able to resolve a dependency on a project with multiple publications that have different coordinates.")
                    formatter.node("Found the following publications in " + project.getDisplayName())
                    formatter.startChildren()
                    for (publication in topLevel) {
                        formatter.node(publication.getDisplayName().getCapitalizedDisplayName() + " with coordinates " + publication.getCoordinates<T?>(coordsType))
                    }
                    formatter.endChildren()
                    throw UnsupportedOperationException(formatter.toString())
                }
            }
            return candidate
        }

        /**
         * For each declared component in a set of publications, map it with its coordinates.
         */
        private fun <T> getComponentCoordinates(coordsType: Class<T?>, publications: MutableCollection<ProjectComponentPublication>): MutableMap<SoftwareComponent, T?> {
            val coordinatesMap: MutableMap<SoftwareComponent, T?> = HashMap<SoftwareComponent, T?>()
            for (publication in publications) {
                val component: SoftwareComponent? = publication.getComponent().getOrNull()
                if (component != null && !publication.isAlias()) {
                    val coordinates = publication.getCoordinates<T?>(coordsType)
                    if (coordinates != null) {
                        coordinatesMap.put(component, coordinates)
                    }
                }
            }
            return coordinatesMap
        }

        /**
         * Get all components that are a child of another component.
         */
        private fun getChildComponents(publications: MutableCollection<ProjectComponentPublication>): MutableSet<SoftwareComponent> {
            val children: MutableSet<SoftwareComponent> = HashSet<SoftwareComponent>()
            for (publication in publications) {
                val component: SoftwareComponent? = publication.getComponent().getOrNull()
                if (component is ComponentWithVariants) {
                    val parent = component
                    // Child components are not top-level entry points.
                    children.addAll(parent.getVariants())
                }
            }
            return children
        }
    }
}
