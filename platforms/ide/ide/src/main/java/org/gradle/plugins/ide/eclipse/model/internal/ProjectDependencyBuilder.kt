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
package org.gradle.plugins.ide.eclipse.model.internal

import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.tasks.TaskDependency
import org.gradle.plugins.ide.eclipse.internal.EclipsePluginConstants
import org.gradle.plugins.ide.eclipse.internal.EclipseProjectMetadata
import org.gradle.plugins.ide.eclipse.model.FileReference
import org.gradle.plugins.ide.eclipse.model.ProjectDependency
import org.gradle.plugins.ide.internal.IdeArtifactRegistry

class ProjectDependencyBuilder(private val ideArtifactRegistry: IdeArtifactRegistry) {
    fun build(componentIdentifier: ProjectComponentIdentifier, publication: FileReference?, buildDependencies: TaskDependency?, testDependency: Boolean, asJavaModule: Boolean): ProjectDependency {
        val dependency = buildProjectDependency(determineTargetProjectPath(componentIdentifier))
        dependency.setPublication(publication)
        if (buildDependencies != null) {
            dependency.buildDependencies(buildDependencies)
        }

        if (testDependency) {
            dependency.getEntryAttributes().put(EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_KEY, EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_VALUE)
        }

        if (asJavaModule) {
            dependency.getEntryAttributes().put(EclipsePluginConstants.MODULE_ATTRIBUTE_KEY, EclipsePluginConstants.MODULE_ATTRIBUTE_VALUE)
        }

        if (containsTestFixtures(componentIdentifier)) {
            dependency.getEntryAttributes().put(EclipsePluginConstants.WITHOUT_TEST_CODE_ATTRIBUTE_KEY, "false")
        } else {
            dependency.getEntryAttributes().put(EclipsePluginConstants.WITHOUT_TEST_CODE_ATTRIBUTE_KEY, "true")
        }
        return dependency
    }

    private fun determineTargetProjectPath(id: ProjectComponentIdentifier): String {
        return "/" + determineTargetProjectName(id)
    }

    fun determineTargetProjectName(id: ProjectComponentIdentifier): String {
        val eclipseProject = ideArtifactRegistry.getIdeProject<EclipseProjectMetadata?>(EclipseProjectMetadata::class.java, id)
        return if (eclipseProject == null) id.getProjectName() else eclipseProject.getName()
    }

    private fun containsTestFixtures(id: ProjectComponentIdentifier?): Boolean {
        val eclipseProject = ideArtifactRegistry.getIdeProject<EclipseProjectMetadata?>(EclipseProjectMetadata::class.java, id)
        return if (eclipseProject != null) eclipseProject.hasJavaTestFixtures() else false
    }

    private fun buildProjectDependency(path: String?): ProjectDependency {
        val out = ProjectDependency(path)
        out.setExported(false)
        return out
    }
}
