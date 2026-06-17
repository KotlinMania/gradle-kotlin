/*
 * Copyright 2022 the original author or authors.
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
package org.gradle.plugin.use.internal

import org.gradle.api.InvalidUserCodeException
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.artifacts.DependencyResolutionServices
import org.gradle.api.internal.artifacts.JavaEcosystemSupport
import org.gradle.api.internal.artifacts.dsl.RepositoryHandlerInternal
import org.gradle.internal.Factory
import org.gradle.plugin.use.resolve.internal.ArtifactRepositoriesPluginResolver
import org.gradle.plugin.use.resolve.internal.PluginArtifactRepositories
import org.gradle.plugin.use.resolve.internal.PluginResolver

internal class DefaultPluginArtifactRepositories(factory: Factory<DependencyResolutionServices?>, private val sharedRepositories: RepositoryHandlerInternal) : PluginArtifactRepositories {
    private val dependencyResolutionServices: DependencyResolutionServices

    init {
        // Create a copy of the shared repository container, so that mutations (eg adding the portal repo) are not reflected in the shared container
        dependencyResolutionServices = factory.create()!!

        JavaEcosystemSupport.configureServices(
            dependencyResolutionServices.getAttributesSchema(),
            dependencyResolutionServices.getAttributeDescribers(),
            dependencyResolutionServices.getObjectFactory()
        )

        val repositoryHandler = dependencyResolutionServices.getResolveRepositoryHandler()
        for (repository in sharedRepositories) {
            // Add a wrapper to the plugin, so that each scope (eg project) can define different exclusive content filters
            repositoryHandler.add(PluginArtifactRepository(repository))
        }
        if (repositoryHandler.isEmpty()) {
            repositoryHandler.gradlePluginPortal()
        }
    }

    override fun getPluginResolver(): PluginResolver {
        return ArtifactRepositoriesPluginResolver(dependencyResolutionServices)
    }

    override fun applyRepositoriesTo(repositories: RepositoryHandler) {
        if (this.isExclusiveContentInUse && !repositories.isEmpty()) {
            throw InvalidUserCodeException(
                "When using exclusive repository content in 'settings.pluginManagement.repositories', you cannot add repositories to 'buildscript.repositories'.\n" +
                        DocumentationRegistry().getDocumentationRecommendationFor("information", "declaring_repositories", "declaring_content_exclusively_found_in_one_repository")
            )
        }
        repositories.addAll(dependencyResolutionServices.getResolveRepositoryHandler())
    }

    private val isExclusiveContentInUse: Boolean
        get() = sharedRepositories.isExclusiveContentInUse
}
