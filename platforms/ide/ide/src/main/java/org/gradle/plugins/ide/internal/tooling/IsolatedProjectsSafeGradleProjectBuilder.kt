/*
 * Copyright 2023 the original author or authors.
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

import com.google.common.collect.ImmutableList
import com.google.common.collect.Streams
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectState
import org.gradle.plugins.ide.internal.tooling.model.DefaultGradleProject
import org.gradle.plugins.ide.internal.tooling.model.IsolatedGradleProjectInternal
import org.gradle.plugins.ide.internal.tooling.model.LaunchableGradleProjectTask
import org.gradle.plugins.ide.internal.tooling.model.LaunchableGradleTask
import org.gradle.tooling.provider.model.internal.IntermediateToolingModelProvider
import org.jspecify.annotations.NullMarked
import java.util.function.BiFunction
import java.util.stream.Collectors

/**
 * Builds the [GradleProject] model that contains the project hierarchy and task information.
 */
@NullMarked
class IsolatedProjectsSafeGradleProjectBuilder(private val intermediateToolingModelProvider: IntermediateToolingModelProvider) : GradleProjectBuilderInternal {
    override fun canBuild(modelName: String): Boolean {
        return modelName == MODEL_NAME
    }

    override fun buildAll(modelName: String, project: Project): Any {
        return buildForRoot(project)
    }

    override fun buildForRoot(project: Project): DefaultGradleProject {
        requireRootProject(project)
        val rootProject = (project as ProjectInternal).getOwner()
        val parameter: IsolatedGradleProjectParameter = createParameter(GradleProjectBuilderOptions.shouldRealizeTasks())
        val rootIsolatedModel = getRootIsolatedModel(rootProject, parameter)
        return build(rootProject, rootProject, rootIsolatedModel, parameter)
    }

    private fun getRootIsolatedModel(rootProject: ProjectState, parameter: IsolatedGradleProjectParameter): IsolatedGradleProjectInternal {
        return getIsolatedModels(rootProject, mutableListOf<ProjectState>(rootProject), parameter).get(0)
    }

    private fun build(root: ProjectState, project: ProjectState, isolatedModel: IsolatedGradleProjectInternal, parameter: IsolatedGradleProjectParameter): DefaultGradleProject {
        val model: DefaultGradleProject = buildWithoutChildren(project, isolatedModel)
        val children: MutableList<ProjectState> = ImmutableList.copyOf<ProjectState>(project.getChildProjects())
        val isolatedChildrenModels = getIsolatedModels(root, children, parameter)
        model.setChildren(buildChildren(root, model, parameter, children, isolatedChildrenModels))
        return model
    }

    private fun buildChildren(
        rootProject: ProjectState,
        parent: DefaultGradleProject,
        parameter: IsolatedGradleProjectParameter,
        children: MutableCollection<ProjectState>,
        isolatedChildrenModels: MutableList<IsolatedGradleProjectInternal>
    ): MutableList<DefaultGradleProject> {
        return Streams.zip<ProjectState, IsolatedGradleProjectInternal, DefaultGradleProject>(
            children.stream(),
            isolatedChildrenModels.stream(),
            BiFunction { c: ProjectState, ic: IsolatedGradleProjectInternal -> build(rootProject, c, ic, parameter) })
            .map<DefaultGradleProject> { it: DefaultGradleProject? -> it!!.setParent(parent) }
            .collect(Collectors.toList())
    }

    private fun getIsolatedModels(root: ProjectState, projects: MutableList<ProjectState>, parameter: IsolatedGradleProjectParameter): MutableList<IsolatedGradleProjectInternal> {
        return intermediateToolingModelProvider.getModels<IsolatedGradleProjectInternal>(root, projects, IsolatedGradleProjectInternal::class.java, parameter)
    }

    companion object {
        private const val MODEL_NAME = "org.gradle.tooling.model.GradleProject"

        private fun requireRootProject(project: Project) {
            require(project == project.getRootProject()) { String.format("%s can only be requested on the root project, got %s", MODEL_NAME, project) }
        }

        private fun buildWithoutChildren(project: ProjectState, isolatedModel: IsolatedGradleProjectInternal): DefaultGradleProject {
            val model = DefaultGradleProject()

            model.setProjectIdentifier(isolatedModel.getProjectIdentifier())
                .setName(isolatedModel.getName())
                .setDescription(isolatedModel.getDescription())
                .setBuildDirectory(isolatedModel.getBuildDirectory())
                .setProjectDirectory(isolatedModel.getProjectDirectory())
                .setBuildTreePath(project.getIdentityPath().asString())

            model.buildScript.setSourceFile(isolatedModel.getBuildScript().getSourceFile())

            val isolatedTasks = isolatedModel.getTasks()
            if (!isolatedTasks.isEmpty()) {
                model.setTasks(isolatedTasks.stream().map<LaunchableGradleProjectTask> { it: LaunchableGradleTask? -> Companion.buildProjectTask(model, it!!) }.collect(Collectors.toList()))
            }

            return model
        }

        private fun buildProjectTask(owner: DefaultGradleProject, model: LaunchableGradleTask): LaunchableGradleProjectTask {
            val target = LaunchableGradleProjectTask()
            target.setPath(model.getPath())
                .setName(model.getName())
                .setGroup(model.getGroup())
                .setDisplayName(model.getDisplayName())
                .setDescription(model.getDescription())
                .setPublic(model.isPublic())
                .setProjectIdentifier(model.getProjectIdentifier())
                .setBuildTreePath(model.getBuildTreePath())
            target.setProject(owner)
            return target
        }

        private fun createParameter(realizeTasks: Boolean): IsolatedGradleProjectParameter {
            return IsolatedGradleProjectParameter { realizeTasks }
        }
    }
}
