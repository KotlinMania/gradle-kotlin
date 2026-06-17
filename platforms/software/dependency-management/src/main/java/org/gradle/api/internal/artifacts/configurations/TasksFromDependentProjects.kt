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

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.internal.artifacts.dependencies.ProjectDependencyInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.TaskDependencyContainerInternal
import org.gradle.api.internal.tasks.TaskDependencyFactory
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.util.Path
import java.util.function.Consumer

@Deprecated("")
class TasksFromDependentProjects(val taskName: String, val configurationName: String, checker: TaskDependencyChecker, taskDependencyFactory: TaskDependencyFactory) : TaskDependencyContainerInternal {
    private val taskDependencyDelegate: TaskDependencyContainerInternal

    constructor(taskName: String, configurationName: String, taskDependencyFactory: TaskDependencyFactory) : this(taskName, configurationName, TaskDependencyChecker(), taskDependencyFactory)

    init {
        this.taskDependencyDelegate = taskDependencyFactory.visitingDependencies(Consumer { context: TaskDependencyResolveContext? ->
            val thisProject = context!!.getTask()!!.getProject()
            val tasksWithName = thisProject.getRootProject().getTasksByName(taskName, true)
            for (nextTask in tasksWithName) {
                if (context.getTask() !== nextTask) {
                    val isDependency = checker.isDependent(thisProject, configurationName, nextTask.getProject())
                    if (isDependency) {
                        context.add(nextTask)
                    }
                }
            }
        })
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

    internal class TaskDependencyChecker {
        //checks if candidate project is dependent of the origin project with given configuration
        fun isDependent(originProject: Project, configurationName: String, candidateProject: Project): Boolean {
            val configuration = candidateProject.getConfigurations().findByName(configurationName)
            if (configuration == null) {
                return false
            }

            val identityPath = (originProject as ProjectInternal).getIdentityPath()
            return doesConfigurationDependOnProject(configuration, identityPath)
        }

        companion object {
            private fun doesConfigurationDependOnProject(configuration: Configuration, identityPath: Path): Boolean {
                val projectDependencies: MutableSet<ProjectDependency> = configuration.getAllDependencies().withType<ProjectDependency>(ProjectDependency::class.java)
                for (projectDependency in projectDependencies) {
                    val dependencyIdentityPath = (projectDependency as ProjectDependencyInternal).getTargetProjectIdentity().getBuildTreePath()
                    if (dependencyIdentityPath == identityPath) {
                        return true
                    }
                }
                return false
            }
        }
    }
}
