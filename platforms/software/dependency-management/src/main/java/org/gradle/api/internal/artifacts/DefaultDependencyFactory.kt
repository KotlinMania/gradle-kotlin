/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.FileCollectionDependency
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.capabilities.Capability
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.artifacts.dependencies.AbstractModuleDependency
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactoryInternal
import org.gradle.api.internal.artifacts.dsl.dependencies.ModuleFactoryHelper
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.notations.DependencyNotationParser
import org.gradle.api.internal.notations.ProjectDependencyFactory
import org.gradle.api.model.ObjectFactory
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.typeconversion.NotationParser

class DefaultDependencyFactory(
    private val instantiator: Instantiator,
    private val dependencyNotationParser: DependencyNotationParser,
    private val capabilityNotationParser: NotationParser<Any?, Capability?>?,
    private val objectFactory: ObjectFactory?,
    private val projectDependencyFactory: ProjectDependencyFactory,
    private val attributesFactory: AttributesFactory?,
    private val project: Project?
) : DependencyFactoryInternal {
    override fun createDependency(dependencyNotation: Any?): Dependency? {
        val dependency: Dependency?
        if (dependencyNotation is Dependency && dependencyNotation !is MinimalExternalModuleDependency) {
            dependency = dependencyNotation
        } else {
            dependency = dependencyNotationParser.notationParser!!.parseNotation(dependencyNotation)
        }
        injectServices(dependency)
        return dependency
    }

    private fun injectServices(dependency: Dependency?) {
        if (dependency is AbstractModuleDependency) {
            val moduleDependency = dependency
            moduleDependency.setAttributesFactory(attributesFactory)
            moduleDependency.setObjectFactory(objectFactory)
            moduleDependency.setCapabilityNotationParser(capabilityNotationParser)
        }
    }

    override fun createProjectDependencyFromMap(map: MutableMap<out String?, out Any?>?): ProjectDependency? {
        return projectDependencyFactory.createFromMap(map)
    }

    // region DependencyFactory methods
    override fun create(dependencyNotation: CharSequence): ExternalModuleDependency {
        val dependency: ExternalModuleDependency = dependencyNotationParser.stringNotationParser!!.parseNotation(dependencyNotation.toString())
        injectServices(dependency)
        return dependency
    }

    override fun create(group: String?, name: String, version: String?): ExternalModuleDependency {
        return create(group, name, version, null, null)
    }

    override fun create(group: String?, name: String, version: String?, classifier: String?, extension: String?): ExternalModuleDependency {
        val dependency = instantiator.newInstance<DefaultExternalModuleDependency>(DefaultExternalModuleDependency::class.java, group, name, version)
        ModuleFactoryHelper.addExplicitArtifactsIfDefined(dependency, extension, classifier)
        injectServices(dependency)
        return dependency
    }

    override fun create(fileCollection: FileCollection): FileCollectionDependency {
        return dependencyNotationParser.fileCollectionNotationParser!!.parseNotation(fileCollection)
    }

    @Deprecated("")
    override fun create(project: Project): ProjectDependency {
        val dependency: ProjectDependency = dependencyNotationParser.projectNotationParser!!.parseNotation(project)
        injectServices(dependency)
        return dependency
    }

    override fun createProjectDependency(projectPath: String): ProjectDependency {
        return projectDependencyFactory.create(projectPath)
    }

    override fun createProjectDependency(): ProjectDependency {
        checkNotNull(project) { "This dependency factory is not associated with a project, so a dependency for the current project cannot be created.  Use create(Project) instead." }
        return projectDependencyFactory.create(project.getPath())
    }

    // endregion
    override fun gradleApi(): Dependency {
        return createDependency(DependencyFactoryInternal.ClassPathNotation.GRADLE_API)!!
    }

    override fun gradleTestKit(): Dependency {
        return createDependency(DependencyFactoryInternal.ClassPathNotation.GRADLE_TEST_KIT)!!
    }

    override fun localGroovy(): Dependency {
        return createDependency(DependencyFactoryInternal.ClassPathNotation.LOCAL_GROOVY)!!
    }
}
