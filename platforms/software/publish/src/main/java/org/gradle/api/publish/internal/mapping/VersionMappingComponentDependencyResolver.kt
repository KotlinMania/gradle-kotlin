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

import org.gradle.api.artifacts.DependencyConstraint
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentSelector
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.internal.artifacts.ProjectComponentIdentifierInternal
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependencyConstraint
import org.gradle.api.internal.artifacts.dependencies.ProjectDependencyInternal
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyPublicationResolver
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchemaFactory.create
import org.gradle.internal.Actions
import org.gradle.internal.component.local.model.ProjectComponentSelectorInternal
import org.gradle.util.Path

/**
 * A [VariantDependencyResolver] that performs version mapping.
 *
 * @see org.gradle.api.publish.VersionMappingStrategy
 */
class VersionMappingComponentDependencyResolver(
    private val projectDependencyResolver: ProjectDependencyPublicationResolver,
    private val root: ResolvedComponentResult
) : ComponentDependencyResolver {
    override fun resolveComponentCoordinates(dependency: ExternalDependency): ResolvedCoordinates? {
        return resolveModule(dependency.getGroup(), dependency.getName())
    }

    override fun resolveComponentCoordinates(dependency: ProjectDependency): ResolvedCoordinates {
        val identityPath = (dependency as ProjectDependencyInternal).getTargetProjectIdentity().getBuildTreePath()
        val coordinates = projectDependencyResolver.resolveComponent<ModuleVersionIdentifier?>(ModuleVersionIdentifier::class.java, identityPath)
        val resolved = maybeResolveVersion(coordinates!!.getGroup(), coordinates.getName(), identityPath)
        return ResolvedCoordinates.Companion.create(if (resolved != null) resolved else coordinates)
    }

    override fun resolveComponentCoordinates(dependency: DependencyConstraint): ResolvedCoordinates? {
        return resolveModule(dependency.getGroup(), dependency.getName())
    }

    override fun resolveComponentCoordinates(dependency: DefaultProjectDependencyConstraint): ResolvedCoordinates {
        return resolveComponentCoordinates(dependency.projectDependency)
    }

    fun resolveModule(group: String, name: String): ResolvedCoordinates? {
        val resolved = maybeResolveVersion(group, name, null)
        if (resolved != null) {
            return ResolvedCoordinates.Companion.create(resolved)
        }
        return null
    }

    fun maybeResolveVersion(group: String, module: String, identityPath: Path?): ModuleVersionIdentifier? {
        val resolvedComponentResults: MutableSet<ResolvedComponentResult> = LinkedHashSet<ResolvedComponentResult>()
        eachElement(root, Actions.doNothing<T>(), Actions.doNothing<T>(), resolvedComponentResults)

        for (selected in resolvedComponentResults) {
            val moduleVersion = selected.getModuleVersion()
            if (moduleVersion != null && group == moduleVersion.getGroup() && module == moduleVersion.getName()) {
                return moduleVersion
            }
        }

        val allDependencies: MutableSet<DependencyResult> = LinkedHashSet<DependencyResult>()
        eachElement(root, Actions.doNothing<T>(), allDependencies::add, HashSet<E?>())

        // If we reach this point it means we have a dependency which doesn't belong to the resolution result
        // Which can mean two things:
        // 1. the graph used to get the resolved version has nothing to do with the dependencies we're trying to get versions for (likely user error)
        // 2. the graph contains first-level dependencies which have been substituted (likely) so we're going to iterate on dependencies instead
        for (dependencyResult in allDependencies) {
            if (dependencyResult is ResolvedDependencyResult) {
                val rcs = dependencyResult.getRequested()
                val selected = dependencyResult.getSelected()
                if (rcs is ModuleComponentSelector) {
                    val requested = rcs
                    if (requested.getGroup() == group && requested.getModule() == module) {
                        return getModuleVersionId(selected)
                    }
                } else if (rcs is ProjectComponentSelector) {
                    val pcs = rcs as ProjectComponentSelectorInternal
                    if (pcs.identityPath.equals(identityPath)) {
                        return getModuleVersionId(selected)
                    }
                }
            }
        }

        return null
    }

    private fun getModuleVersionId(selected: ResolvedComponentResult): ModuleVersionIdentifier? {
        // Match found - need to make sure that if the selection is a project, we use its publication identity
        if (selected.getId() is ProjectComponentIdentifier) {
            val identityPath = (selected.getId() as ProjectComponentIdentifierInternal).getIdentityPath()
            return projectDependencyResolver.resolveComponent<ModuleVersionIdentifier?>(ModuleVersionIdentifier::class.java, identityPath)
        }
        return selected.getModuleVersion()
    }
}
