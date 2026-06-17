/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.plugins.ide.internal.tooling

import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectComponentPublication
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectPublicationRegistry
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.plugins.ide.internal.tooling.model.DefaultGradleModuleVersion
import org.gradle.plugins.ide.internal.tooling.model.DefaultGradlePublication
import org.gradle.plugins.ide.internal.tooling.model.DefaultProjectPublications
import org.gradle.tooling.internal.gradle.DefaultProjectIdentifier
import org.gradle.tooling.model.gradle.ProjectPublications
import org.gradle.tooling.provider.model.ToolingModelBuilder

internal class PublicationsBuilder(private val publicationRegistry: ProjectPublicationRegistry) : ToolingModelBuilder {
    override fun canBuild(modelName: String): Boolean {
        return modelName == MODEL_NAME
    }

    override fun buildAll(modelName: String, project: Project): Any? {
        val projectIdentifier = DefaultProjectIdentifier(project.getRootDir(), project.getPath())
        return DefaultProjectPublications().setPublications(publications(project as ProjectInternal, projectIdentifier)).setProjectIdentifier(projectIdentifier)
    }

    private fun publications(project: ProjectInternal, projectIdentifier: DefaultProjectIdentifier?): MutableList<DefaultGradlePublication?> {
        val gradlePublications: MutableList<DefaultGradlePublication?> = ArrayList<DefaultGradlePublication?>()

        for (projectPublication in publicationRegistry.getPublicationsForProject<ProjectComponentPublication?>(ProjectComponentPublication::class.java, project.getIdentityPath())) {
            val id = projectPublication!!.getCoordinates<ModuleVersionIdentifier?>(ModuleVersionIdentifier::class.java)
            if (id != null) {
                gradlePublications.add(
                    DefaultGradlePublication()
                        .setId(DefaultGradleModuleVersion(id.getGroup(), id.getName(), id.getVersion()))
                        .setProjectIdentifier(projectIdentifier)
                )
            }
        }

        return gradlePublications
    }

    companion object {
        private val MODEL_NAME: String = ProjectPublications::class.java.getName()
    }
}
