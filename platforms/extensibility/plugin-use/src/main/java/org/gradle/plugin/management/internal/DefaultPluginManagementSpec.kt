/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.plugin.management.internal

import org.gradle.api.Action
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.initialization.ConfigurableIncludedPluginBuild
import org.gradle.api.internal.file.FileResolver
import org.gradle.initialization.IncludedBuildSpec
import org.gradle.internal.Actions
import org.gradle.internal.build.BuildIncluder
import org.gradle.plugin.management.PluginResolutionStrategy
import org.gradle.plugin.use.PluginDependenciesSpec
import org.gradle.plugin.use.PluginDependencySpec
import org.gradle.plugin.use.PluginId
import org.gradle.plugin.use.internal.DefaultPluginId
import org.gradle.plugin.use.internal.PluginRepositoryHandlerProvider

class DefaultPluginManagementSpec(
    private val pluginRepositoryHandlerProvider: PluginRepositoryHandlerProvider,
    private val pluginResolutionStrategy: PluginResolutionStrategyInternal,
    private val fileResolver: FileResolver,
    private val buildIncluder: BuildIncluder
) : PluginManagementSpecInternal {
    private val pluginDependenciesSpec: PluginDependenciesSpec

    private val includedBuildSpecs: MutableList<IncludedBuildSpec?> = ArrayList<IncludedBuildSpec?>()

    init {
        this.pluginDependenciesSpec = DefaultPluginManagementSpec.PluginDependenciesSpecImpl()
    }

    override fun repositories(repositoriesAction: Action<in RepositoryHandler?>) {
        repositoriesAction.execute(getRepositories())
    }

    override fun getRepositories(): RepositoryHandler {
        return pluginRepositoryHandlerProvider.getPluginRepositoryHandler()
    }

    override fun resolutionStrategy(action: Action<in PluginResolutionStrategy?>) {
        action.execute(pluginResolutionStrategy)
    }

    override fun getResolutionStrategy(): PluginResolutionStrategyInternal {
        return pluginResolutionStrategy
    }

    override fun plugins(action: Action<in PluginDependenciesSpec?>) {
        action.execute(pluginDependenciesSpec)
    }

    override fun getPlugins(): PluginDependenciesSpec {
        return pluginDependenciesSpec
    }

    override fun includeBuild(rootProject: String) {
        includeBuild(rootProject, Actions.doNothing<ConfigurableIncludedPluginBuild?>())
    }

    override fun includeBuild(rootProject: String, configuration: Action<ConfigurableIncludedPluginBuild?>) {
        val projectDir = fileResolver.resolve(rootProject)
        val buildSpec = IncludedBuildSpec.includedPluginBuild(projectDir, configuration)
        buildIncluder.registerPluginBuild(buildSpec)
        includedBuildSpecs.add(buildSpec)
    }

    override fun getIncludedBuilds(): MutableList<IncludedBuildSpec?> {
        return includedBuildSpecs
    }

    private inner class PluginDependenciesSpecImpl : PluginDependenciesSpec {
        override fun id(id: String): PluginDependencySpec {
            return DefaultPluginManagementSpec.PluginDependencySpecImpl(DefaultPluginId.of(id))
        }
    }

    private inner class PluginDependencySpecImpl(private val id: PluginId) : PluginDependencySpec {
        override fun version(version: String): PluginDependencySpec {
            pluginResolutionStrategy.setDefaultPluginVersion(id, version)
            return this
        }

        override fun apply(apply: Boolean): PluginDependencySpec {
            require(!apply) { "Cannot apply a plugin from within a pluginManagement block." }
            return this
        }
    }
}
