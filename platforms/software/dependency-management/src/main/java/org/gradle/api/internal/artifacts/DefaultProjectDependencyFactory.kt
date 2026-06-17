/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.internal.artifacts

import org.gradle.api.UnknownProjectException
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.capabilities.Capability
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency
import org.gradle.api.internal.artifacts.dsl.CapabilityNotationParser
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.project.ProjectState
import org.gradle.api.internal.project.ProjectStateLookup
import org.gradle.api.model.ObjectFactory
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.internal.typeconversion.NotationParser
import org.gradle.util.Path

@ServiceScope([Scope.Build::class, Scope.Project::class])
class DefaultProjectDependencyFactory(
    private val instantiator: Instantiator,
    capabilityNotationParser: CapabilityNotationParser?,
    objectFactory: ObjectFactory?,
    attributesFactory: AttributesFactory?,
    projectStateLookup: ProjectStateLookup,
    projectFinder: ProjectFinder
) {
    private val capabilityNotationParser: NotationParser<Any?, Capability?>?
    private val objectFactory: ObjectFactory?
    private val attributesFactory: AttributesFactory?
    private val projectStateLookup: ProjectStateLookup
    private val projectFinder: ProjectFinder

    init {
        this.capabilityNotationParser = capabilityNotationParser
        this.objectFactory = objectFactory
        this.attributesFactory = attributesFactory
        this.projectStateLookup = projectStateLookup
        this.projectFinder = projectFinder
    }

    fun create(projectState: ProjectState): ProjectDependency {
        val projectDependency = instantiator.newInstance<DefaultProjectDependency>(DefaultProjectDependency::class.java, projectState)
        injectServices(projectDependency)
        return projectDependency
    }

    fun create(projectPath: String?): ProjectDependency {
        return create(projectFinder.resolveIdentityPath(projectPath))
    }

    fun create(projectIdentityPath: Path): ProjectDependency {
        val projectState = projectStateLookup.findProject(projectIdentityPath)
        if (projectState == null) {
            throw UnknownProjectException(String.format("Project with path '%s' could not be found.", projectIdentityPath.asString()))
        }
        return create(projectState)
    }

    private fun injectServices(projectDependency: DefaultProjectDependency) {
        projectDependency.setAttributesFactory(attributesFactory)
        projectDependency.setCapabilityNotationParser(capabilityNotationParser)
        projectDependency.setObjectFactory(objectFactory)
    }
}
