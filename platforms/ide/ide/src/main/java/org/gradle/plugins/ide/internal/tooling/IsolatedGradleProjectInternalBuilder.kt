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

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.internal.TaskInternal
import org.gradle.api.tasks.TaskContainer
import org.gradle.plugins.ide.internal.tooling.model.IsolatedGradleProjectInternal
import org.gradle.plugins.ide.internal.tooling.model.LaunchableGradleTask
import org.gradle.tooling.internal.gradle.DefaultProjectIdentifier
import org.gradle.tooling.provider.model.ParameterizedToolingModelBuilder
import org.jspecify.annotations.NullMarked
import java.util.Objects
import java.util.stream.Collectors

/**
 * Builds the [IsolatedGradleProjectInternal] that contains information about a project and its tasks.
 */
@NullMarked
class IsolatedGradleProjectInternalBuilder : ParameterizedToolingModelBuilder<IsolatedGradleProjectParameter> {
    override fun getParameterType(): Class<IsolatedGradleProjectParameter> {
        return IsolatedGradleProjectParameter::class.java
    }

    override fun canBuild(modelName: String): Boolean {
        return modelName == IsolatedGradleProjectInternal::class.java.getName()
    }

    override fun buildAll(modelName: String, parameter: IsolatedGradleProjectParameter, project: Project): IsolatedGradleProjectInternal {
        return build(project, parameter.getRealizeTasks())
    }

    override fun buildAll(modelName: String, project: Project): IsolatedGradleProjectInternal {
        return build(project, true)
    }

    companion object {
        private fun build(project: Project, realizeTasks: Boolean): IsolatedGradleProjectInternal {
            val gradleProject = IsolatedGradleProjectInternal()
                .setProjectIdentifier(DefaultProjectIdentifier(project.getRootDir(), project.getPath()))
                .setName(project.getName())
                .setDescription(project.getDescription())
                .setBuildDirectory(project.getLayout().getBuildDirectory().getAsFile().get())
                .setProjectDirectory(project.getProjectDir())

            gradleProject.getBuildScript().setSourceFile(project.getBuildFile())

            if (realizeTasks) {
                val tasks: MutableList<LaunchableGradleTask> = buildTasks(gradleProject, project.getTasks())
                gradleProject.setTasks(tasks)
            }

            return gradleProject
        }

        private fun buildTasks(owner: IsolatedGradleProjectInternal, tasks: TaskContainer): MutableList<LaunchableGradleTask> {
            return tasks.getNames().stream()
                .map<Task> { name: String? -> tasks.findByName(name!!) }
                .filter { obj: Task? -> Objects.nonNull(obj) }
                .map<LaunchableGradleTask> { task: Task? -> Companion.buildTask(owner, task!!) }
                .collect(Collectors.toList())
        }

        private fun buildTask(owner: IsolatedGradleProjectInternal, task: Task): LaunchableGradleTask {
            return ToolingModelBuilderSupport.buildFromTask<LaunchableGradleTask>(LaunchableGradleTask(), owner.getProjectIdentifier(), task)
                .setBuildTreePath(getBuildTreePath(task))
        }

        private fun getBuildTreePath(task: Task): String {
            return (task as TaskInternal).getIdentityPath().asString()
        }
    }
}
