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
package org.gradle.plugins.ide.internal.tooling

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectState
import org.gradle.api.internal.tasks.TaskContainerInternal
import org.gradle.plugins.ide.internal.tooling.model.DefaultGradleProject
import org.gradle.plugins.ide.internal.tooling.model.LaunchableGradleProjectTask
import org.gradle.tooling.internal.gradle.DefaultProjectIdentifier
import org.gradle.tooling.model.GradleProject
import java.util.Objects
import java.util.function.Function
import java.util.stream.Collectors

/**
 * Builds the [GradleProject] model that contains the project hierarchy and task information.
 */
class GradleProjectBuilder : GradleProjectBuilderInternal {
    override fun canBuild(modelName: String): Boolean {
        return modelName == MODEL_NAME
    }

    override fun buildAll(modelName: String, project: Project): Any {
        return buildForRoot(project)
    }

    override fun buildForRoot(project: Project): DefaultGradleProject {
        val realizeTasks = GradleProjectBuilderOptions.shouldRealizeTasks()
        val rootProjectState = (project.getRootProject() as ProjectInternal).getOwner()
        return buildHierarchy(rootProjectState, realizeTasks)
    }

    companion object {
        private val MODEL_NAME: String = GradleProject::class.java.getName()

        /**
         * When `realizeTasks` is false, the project's task graph will not be realized, and the task list in the model will be empty
         */
        private fun buildHierarchy(project: ProjectState, realizeTasks: Boolean): DefaultGradleProject {
            val children = project.getChildProjects().stream()
                .map<DefaultGradleProject?> { it: ProjectState? -> Companion.buildHierarchy(it!!, realizeTasks) }
                .collect(Collectors.toList())

            val mutableProject = project.getMutableModel()
            val projectIdentityPath = project.getIdentityPath().asString()
            val gradleProject = DefaultGradleProject()
                .setProjectIdentifier(DefaultProjectIdentifier(mutableProject.getRootDir(), mutableProject.getPath()))
                .setName(mutableProject.getName())
                .setDescription(mutableProject.getDescription())
                .setBuildDirectory(mutableProject.getLayout().getBuildDirectory().getAsFile().get())
                .setProjectDirectory(mutableProject.getProjectDir())
                .setBuildTreePath(projectIdentityPath)
                .setChildren(children)

            gradleProject.buildScript.setSourceFile(mutableProject.getBuildFile())

            for (child in children) {
                child.setParent(gradleProject)
            }

            if (realizeTasks) {
                val tasks = project.fromMutableState<MutableList<LaunchableGradleProjectTask?>?>(Function { p: ProjectInternal? -> collectTasks(gradleProject, p!!.getTasks()) }
                )
                gradleProject.setTasks(tasks)
            }

            return gradleProject
        }

        private fun collectTasks(owner: DefaultGradleProject, tasks: TaskContainerInternal): MutableList<LaunchableGradleProjectTask?> {
            tasks.discoverTasks()
            tasks.realize()

            return tasks.getNames().stream()
                .map<Task?> { name: String? -> tasks.findByName(name!!) }
                .filter { obj: Task? -> Objects.nonNull(obj) }
                .map<LaunchableGradleProjectTask?> { task: Task? -> Companion.buildTask(owner, task!!) }
                .collect(Collectors.toList())
        }

        private fun buildTask(owner: DefaultGradleProject, task: Task): LaunchableGradleProjectTask {
            val model = ToolingModelBuilderSupport.buildFromTask<LaunchableGradleProjectTask>(LaunchableGradleProjectTask(), owner.getProjectIdentifier(), task)
            model.setProject(owner)
            return model
        }
    }
}
