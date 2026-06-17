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
package org.gradle.language.internal

import org.gradle.api.Action
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationContainerInternal
import org.gradle.language.ComponentDependencies
import javax.inject.Inject

open class DefaultComponentDependencies @Inject constructor(configurations: RoleBasedConfigurationContainerInternal, implementationName: String?, protected val dependencyHandler: DependencyHandler) :
    ComponentDependencies {
    val implementationDependencies: Configuration

    init {
        this.implementationDependencies = configurations.dependencyScopeLocked(implementationName)
    }

    override fun project(projectPath: String): ProjectDependency {
        return dependencyHandler.project(projectPath)
    }

    override fun implementation(notation: Any) {
        implementationDependencies.getDependencies().add(dependencyHandler.create(notation))
    }

    override fun implementation(notation: Any, action: Action<in ExternalModuleDependency?>) {
        val dependency = dependencyHandler.create(notation) as ExternalModuleDependency
        action.execute(dependency)
        implementationDependencies.getDependencies().add(dependency)
    }
}
