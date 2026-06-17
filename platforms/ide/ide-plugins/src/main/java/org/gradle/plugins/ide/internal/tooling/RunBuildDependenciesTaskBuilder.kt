/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.StartParameter
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectState
import org.gradle.api.tasks.TaskDependency
import org.gradle.plugins.ide.eclipse.EclipsePlugin
import org.gradle.plugins.ide.eclipse.model.EclipseModel
import org.gradle.plugins.ide.internal.tooling.eclipse.DefaultRunClosedProjectBuildDependencies
import org.gradle.tooling.model.eclipse.EclipseRuntime
import org.gradle.tooling.model.eclipse.EclipseWorkspaceProject
import org.gradle.tooling.model.eclipse.RunClosedProjectBuildDependencies
import org.gradle.tooling.provider.model.ParameterizedToolingModelBuilder
import org.jspecify.annotations.NullMarked
import java.util.function.BinaryOperator
import java.util.function.Consumer
import java.util.function.Function
import java.util.stream.Collectors

@NullMarked
class RunBuildDependenciesTaskBuilder : ParameterizedToolingModelBuilder<EclipseRuntime> {
    private var projectOpenStatus: MutableMap<String, Boolean>? = null

    override fun getParameterType(): Class<EclipseRuntime> {
        return EclipseRuntime::class.java
    }

    override fun buildAll(modelName: String, eclipseRuntime: EclipseRuntime, project: Project): RunClosedProjectBuildDependencies {
        this.projectOpenStatus = eclipseRuntime.getWorkspace().getProjects().stream()
            .collect(
                Collectors.toMap(
                    Function { obj: EclipseWorkspaceProject? -> obj!!.getName() },
                    Function { project: EclipseWorkspaceProject? -> EclipseModelBuilder.Companion.isProjectOpen(project) },
                    BinaryOperator { a: Boolean?, b: Boolean? -> a || b })
            )

        val rootProjectState = (project.getRootProject() as ProjectInternal).getOwner()
        val buildDependencies = populate(rootProjectState)
        if (!buildDependencies.isEmpty()) {
            val rootGradle = (project as ProjectInternal).getGradle().getRoot()
            val startParameter: StartParameter = rootGradle.getStartParameter()
            val taskPaths: MutableList<String> = ArrayList<Any?>(startParameter.taskNames)
            rootGradle.getOwner().getRootProject().applyToMutableState(Consumer { rootProject: ProjectInternal? ->
                val parentTaskName: String = Companion.parentTaskName(rootProject!!, "eclipseClosedDependencies")
                rootProject.getTasks().register(parentTaskName, Action { task: Task? -> task!!.dependsOn(*buildDependencies.toTypedArray<Any>()) })
                taskPaths.add(parentTaskName)
            })
            startParameter.taskNames = taskPaths
        }
        return DefaultRunClosedProjectBuildDependencies
    }

    private fun populate(p: ProjectState): MutableList<TaskDependency> {
        val currentElements: MutableList<TaskDependency>? = p.fromMutableState<MutableList<TaskDependency>>(Function { project: ProjectInternal? ->
            project!!.getPluginManager().apply(EclipsePlugin::class.java)
            val eclipseModel = project.getExtensions().getByType<EclipseModel>(EclipseModel::class.java)
            val eclipseClasspath = eclipseModel.getClasspath()

            val elements: EclipseModelBuilder.ClasspathElements = EclipseModelBuilder.Companion.gatherClasspathElements(projectOpenStatus, eclipseClasspath, false)
            elements.getBuildDependencies()
        })

        val buildDependencies: MutableList<TaskDependency> = ArrayList<TaskDependency>(currentElements)
        for (childProject in p.getChildProjects()) {
            buildDependencies.addAll(populate(childProject))
        }
        return buildDependencies
    }

    override fun canBuild(modelName: String): Boolean {
        return MODEL_NAME == modelName
    }

    override fun buildAll(modelName: String, project: Project): Any {
        // nothing to do if no EclipseRuntime is supplied.
        return DefaultRunClosedProjectBuildDependencies
    }

    companion object {
        private val MODEL_NAME: String = RunClosedProjectBuildDependencies::class.java.getName()

        private fun parentTaskName(project: Project, baseName: String): String {
            if (project.getTasks().findByName(baseName) == null) {
                return baseName
            } else {
                return parentTaskName(project, baseName + "_")
            }
        }
    }
}
