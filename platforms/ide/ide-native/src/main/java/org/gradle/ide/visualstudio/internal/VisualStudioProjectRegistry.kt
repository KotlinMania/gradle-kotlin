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
package org.gradle.ide.visualstudio.internal

import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.DefaultNamedDomainObjectSet
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ProviderFactory
import org.gradle.ide.visualstudio.VisualStudioProject
import org.gradle.internal.reflect.Instantiator
import org.gradle.plugins.ide.internal.IdeArtifactRegistry

class VisualStudioProjectRegistry(
    private val fileResolver: FileResolver?,
    instantiator: Instantiator,
    private val ideArtifactRegistry: IdeArtifactRegistry,
    collectionCallbackActionDecorator: CollectionCallbackActionDecorator,
    private val objectFactory: ObjectFactory?,
    private val providerFactory: ProviderFactory?
) : DefaultNamedDomainObjectSet<DefaultVisualStudioProject?>(
    DefaultVisualStudioProject::class.java, instantiator, collectionCallbackActionDecorator
) {
    fun getProjectConfiguration(targetBinary: VisualStudioTargetBinary): VisualStudioProjectConfiguration? {
        val projectName = targetBinary.getVisualStudioProjectName()
        return getByName(projectName)!!.getConfiguration(targetBinary)
    }

    fun addProjectConfiguration(nativeBinary: VisualStudioTargetBinary): VisualStudioProjectConfiguration {
        val project = getOrCreateProject(nativeBinary.getVisualStudioProjectName(), nativeBinary.getComponentName())
        project.getSdkVersion().set(nativeBinary.getSdkVersion())
        project.getVisualStudioVersion().set(nativeBinary.getVisualStudioVersion())
        val configuration = createVisualStudioProjectConfiguration(project, nativeBinary, nativeBinary.getVisualStudioConfigurationName())
        project.addConfiguration(nativeBinary, configuration)
        return configuration
    }

    private fun createVisualStudioProjectConfiguration(project: VisualStudioProject, nativeBinary: VisualStudioTargetBinary?, configuration: String?): VisualStudioProjectConfiguration {
        return getInstantiator().newInstance<VisualStudioProjectConfiguration>(VisualStudioProjectConfiguration::class.java, project, configuration, nativeBinary)
    }

    fun createProject(vsProjectName: String, componentName: String?): DefaultVisualStudioProject {
        assert(findByName(vsProjectName) == null)
        val vsProject = getInstantiator().newInstance<DefaultVisualStudioProject>(DefaultVisualStudioProject::class.java, vsProjectName, componentName, fileResolver, objectFactory, providerFactory)
        add(vsProject)
        ideArtifactRegistry.registerIdeProject(vsProject.getPublishArtifact())
        return vsProject
    }

    private fun getOrCreateProject(vsProjectName: String, componentName: String?): DefaultVisualStudioProject {
        var vsProject = findByName(vsProjectName)
        if (vsProject == null) {
            vsProject = createProject(vsProjectName, componentName)
        }
        return vsProject
    }
}
