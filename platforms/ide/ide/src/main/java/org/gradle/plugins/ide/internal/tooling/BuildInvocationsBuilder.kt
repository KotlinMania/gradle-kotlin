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

import com.google.common.collect.ImmutableList
import com.google.common.collect.Maps
import com.google.common.collect.Ordering
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectState
import org.gradle.api.internal.project.ProjectTaskLister
import org.gradle.api.internal.tasks.PublicTaskSpecification
import org.gradle.plugins.ide.internal.tooling.model.DefaultBuildInvocations
import org.gradle.plugins.ide.internal.tooling.model.LaunchableGradleTask
import org.gradle.plugins.ide.internal.tooling.model.LaunchableGradleTaskSelector
import org.gradle.plugins.ide.internal.tooling.model.TaskNameComparator
import org.gradle.tooling.internal.gradle.DefaultProjectIdentifier
import org.gradle.tooling.model.gradle.BuildInvocations
import org.gradle.tooling.provider.model.ToolingModelBuilder
import java.util.function.Consumer

class BuildInvocationsBuilder(private val taskLister: ProjectTaskLister) : ToolingModelBuilder {
    private val taskNameComparator: TaskNameComparator

    init {
        this.taskNameComparator = TaskNameComparator()
    }

    override fun canBuild(modelName: String): Boolean {
        return modelName == MODEL_NAME
    }

    override fun buildAll(modelName: String, project: Project): DefaultBuildInvocations? {
        if (!canBuild(modelName)) {
            throw GradleException("Unknown model name " + modelName)
        }
        val projectState = (project as ProjectInternal).getOwner()

        val projectIdentifier = getProjectIdentifier(project)
        // construct task selectors
        val selectors: MutableList<LaunchableGradleTaskSelector?> = ArrayList<LaunchableGradleTaskSelector?>()
        val selectorsByName: MutableMap<String?, LaunchableGradleTaskSelector> = Maps.newTreeMap<String?, String?, LaunchableGradleTaskSelector?>(Ordering.natural<String?>())
        val visibleTasks: MutableSet<String?> = LinkedHashSet<String?>()
        findTasks(projectState, selectorsByName, visibleTasks)
        for (selectorName in selectorsByName.keys) {
            val selector: LaunchableGradleTaskSelector = selectorsByName.get(selectorName)!!
            selectors.add(
                selector
                    .setName(selectorName)
                    .setTaskName(selectorName)
                    .setProjectIdentifier(projectIdentifier)
                    .setDisplayName(selectorName + " in " + project + " and subprojects.")
                    .setPublic(visibleTasks.contains(selectorName))
            )
        }

        // construct project tasks
        val projectTasks = tasks(project, projectIdentifier)

        // construct build invocations from task selectors and project tasks
        return DefaultBuildInvocations()
            .setSelectors(selectors)
            .setTasks(projectTasks)
            .setProjectIdentifier(projectIdentifier)
    }

    private fun getProjectIdentifier(project: Project): DefaultProjectIdentifier {
        return DefaultProjectIdentifier(project.getRootDir(), project.getPath())
    }

    // build tasks without project reference
    private fun tasks(project: Project, projectIdentifier: DefaultProjectIdentifier?): MutableList<LaunchableGradleTask?> {
        return taskLister.listProjectTasks(project).stream()
            .map<LaunchableGradleTask?> { task: Task? -> ToolingModelBuilderSupport.buildFromTask<LaunchableGradleTask?>(LaunchableGradleTask(), projectIdentifier, task) }
            .collect(ImmutableList.toImmutableList<LaunchableGradleTask?>())
    }

    private fun findTasks(p: ProjectState, taskSelectors: MutableMap<String?, LaunchableGradleTaskSelector>, visibleTasks: MutableCollection<String?>) {
        for (child in p.getChildProjects()) {
            findTasks(child, taskSelectors, visibleTasks)
        }

        p.applyToMutableState(Consumer { project: ProjectInternal? ->
            for (task in taskLister.listProjectTasks(project!!)) {
                // in the map, store a minimally populated LaunchableGradleTaskSelector that contains just the description and the path
                // replace the LaunchableGradleTaskSelector stored in the map iff we come across a task with the same name whose path has a smaller ordering
                // this way, for each task selector, its description will be the one from the selected task with the 'smallest' path
                if (!taskSelectors.containsKey(task.getName())) {
                    val taskSelector = LaunchableGradleTaskSelector()
                        .setDescription(task.getDescription()).setPath(task.getPath())
                    taskSelectors.put(task.getName(), taskSelector!!)
                } else {
                    val taskSelector: LaunchableGradleTaskSelector = taskSelectors.get(task.getName())!!
                    if (hasPathWithLowerOrdering(task, taskSelector)) {
                        taskSelector.setDescription(task.getDescription()).setPath(task.getPath())
                    }
                }

                // visible tasks are specified as those that have a non-empty group
                if (PublicTaskSpecification.INSTANCE.isSatisfiedBy(task)) {
                    visibleTasks.add(task.getName())
                }
            }
        })
    }

    private fun hasPathWithLowerOrdering(task: Task, referenceTaskSelector: LaunchableGradleTaskSelector): Boolean {
        return taskNameComparator.compare(task.getPath(), referenceTaskSelector.getPath()) < 0
    }

    companion object {
        private val MODEL_NAME: String = BuildInvocations::class.java.getName()
    }
}
