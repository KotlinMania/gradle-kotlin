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
package org.gradle.plugins.ide.internal

import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectStateRegistry
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.util.internal.CollectionUtils.collect
import java.util.concurrent.Callable
import java.util.function.Function
import javax.inject.Inject

class DefaultIdeArtifactRegistry @Inject constructor(
    private val store: IdeArtifactStore,
    private val projectRegistry: ProjectStateRegistry,
    private val fileOperations: FileOperations,
    currentProject: ProjectInternal
) : IdeArtifactRegistry {
    private val currentProjectId: ProjectComponentIdentifier

    init {
        this.currentProjectId = currentProject.getOwner().getComponentIdentifier()
    }

    override fun registerIdeProject(ideProjectMetadata: IdeProjectMetadata?) {
        store.put(currentProjectId, ideProjectMetadata)
    }

    override fun <T : IdeProjectMetadata?> getIdeProject(type: Class<T?>, project: ProjectComponentIdentifier): T? {
        val projectState = projectRegistry.stateFor(project)
        if (!projectState.getOwner().isImplicitBuild()) {
            // Do not include implicit builds in workspace
            for (ideProjectMetadata in store.get(project)) {
                if (type.isInstance(ideProjectMetadata)) {
                    return type.cast(ideProjectMetadata)
                }
            }
        }
        return null
    }

    override fun <T : IdeProjectMetadata?> getIdeProjects(type: Class<T?>): MutableList<IdeArtifactRegistry.Reference<T?>?> {
        val result: MutableList<IdeArtifactRegistry.Reference<T?>?> = ArrayList<IdeArtifactRegistry.Reference<T?>?>()
        for (project in projectRegistry.getAllProjects()) {
            if (project.getOwner().isImplicitBuild()) {
                // Do not include implicit builds in workspace
                continue
            }
            val projectId = project.getComponentIdentifier()
            for (ideProjectMetadata in store.get(projectId)) {
                if (type.isInstance(ideProjectMetadata)) {
                    val metadata = type.cast(ideProjectMetadata)
                    result.add(MetadataReference<T?>(metadata, projectId))
                }
            }
        }
        return result
    }

    override fun getIdeProjectFiles(type: Class<out IdeProjectMetadata?>): FileCollection? {
        return fileOperations.immutableFiles(object : Callable<MutableList<FileCollection?>?> {
            override fun call(): MutableList<FileCollection?> {
                return collect(
                    getIdeProjects(type),
                    Function { result: IdeArtifactRegistry.Reference<IdeProjectMetadata?>? ->
                        val singleton = fileOperations.configurableFiles(result!!.get()!!.getFile())
                        singleton.builtBy(result.get()!!.getGeneratorTasks())
                        singleton
                    }
                )
            }
        })
    }

    private class MetadataReference<T : IdeProjectMetadata?>(private val metadata: T?, private val projectId: ProjectComponentIdentifier?) : IdeArtifactRegistry.Reference<T?> {
        override fun get(): T? {
            return metadata
        }

        override fun getOwningProject(): ProjectComponentIdentifier? {
            return projectId
        }

        override fun visitDependencies(context: TaskDependencyResolveContext) {
            for (task in get()!!.getGeneratorTasks()) {
                context.add(task)
            }
        }
    }
}
