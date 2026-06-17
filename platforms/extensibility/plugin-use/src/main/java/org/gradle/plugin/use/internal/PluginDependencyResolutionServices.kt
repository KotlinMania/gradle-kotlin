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
package org.gradle.plugin.use.internal

import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.internal.artifacts.DependencyResolutionServices
import org.gradle.api.internal.artifacts.dsl.RepositoryHandlerInternal
import org.gradle.internal.Factory
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.plugin.use.resolve.internal.PluginArtifactRepositories
import org.gradle.plugin.use.resolve.internal.PluginArtifactRepositoriesProvider

@ServiceScope(Scope.Build::class)
class PluginDependencyResolutionServices(private val factory: Factory<DependencyResolutionServices?>) : PluginRepositoryHandlerProvider, PluginArtifactRepositoriesProvider {
    private var dependencyResolutionServices: DependencyResolutionServices? = null
        get() {
            if (field == null) {
                field = factory.create()
            }
            return field
        }

    private val resolveRepositoryHandler: RepositoryHandlerInternal
        get() = this.dependencyResolutionServices!!.getResolveRepositoryHandler() as RepositoryHandlerInternal

    override fun getPluginRepositoryHandler(): RepositoryHandler {
        return this.resolveRepositoryHandler
    }

    override fun createPluginResolveRepositories(): PluginArtifactRepositories {
        return DefaultPluginArtifactRepositories(factory, this.resolveRepositoryHandler)
    }
}
