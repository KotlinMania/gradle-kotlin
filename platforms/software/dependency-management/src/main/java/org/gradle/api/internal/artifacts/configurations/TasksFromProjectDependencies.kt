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
package org.gradle.api.internal.artifacts.configurations

import org.gradle.api.Task
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.internal.artifacts.dependencies.ProjectDependencyInternal
import org.gradle.api.internal.project.ProjectStateRegistry
import org.gradle.api.internal.tasks.TaskDependencyContainerInternal
import org.gradle.api.internal.tasks.TaskDependencyFactory
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import java.util.function.Consumer
import java.util.function.Supplier

@Deprecated("")
class TasksFromProjectDependencies(
    taskName: String,
    projectDependencies: Supplier<MutableSet<ProjectDependency>>,
    taskDependencyFactory: TaskDependencyFactory,
    projectStateRegistry: ProjectStateRegistry
) : TaskDependencyContainerInternal {
    private val taskDependencyDelegate: TaskDependencyContainerInternal

    init {
        this.taskDependencyDelegate = taskDependencyFactory.visitingDependencies(
            Consumer { context: TaskDependencyResolveContext? -> Companion.resolveProjectDependencies(context!!, projectDependencies.get(), projectStateRegistry, taskName) }
        )
    }

    override fun visitDependencies(context: TaskDependencyResolveContext) {
        taskDependencyDelegate.visitDependencies(context)
    }

    override fun getDependencies(task: Task?): MutableSet<out Task> {
        return taskDependencyDelegate.getDependencies(task)
    }

    override fun getDependenciesForInternalUse(task: Task?): MutableSet<out Task> {
        return taskDependencyDelegate.getDependenciesForInternalUse(task)
    }

    companion object {
        private fun resolveProjectDependencies(
            context: TaskDependencyResolveContext,
            projectDependencies: MutableSet<ProjectDependency>,
            projectStateRegistry: ProjectStateRegistry,
            taskName: String
        ) {
            for (projectDependency in projectDependencies) {
                val identityPath = (projectDependency as ProjectDependencyInternal).getTargetProjectIdentity().getBuildTreePath()
                val projectState = projectStateRegistry.stateFor(identityPath)
                projectState.ensureTasksDiscovered()

                val nextTask = projectState.getMutableModel().getTasks().findByName(taskName)
                if (nextTask != null && context.getTask() !== nextTask) {
                    context.add(nextTask)
                }
            }
        }
    }
}
