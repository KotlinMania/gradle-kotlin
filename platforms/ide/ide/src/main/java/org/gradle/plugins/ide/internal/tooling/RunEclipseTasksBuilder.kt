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

import org.gradle.api.Project
import org.gradle.api.internal.tasks.TaskDependencyUtil
import org.gradle.plugins.ide.eclipse.model.EclipseModel
import org.gradle.tooling.provider.model.ToolingModelBuilder

class RunEclipseTasksBuilder : ToolingModelBuilder {
    override fun canBuild(modelName: String): Boolean {
        return isSyncModel(modelName) || isAutoBuildModel(modelName)
    }

    override fun buildAll(modelName: String, project: Project): Any? {
        val startParameter = project.getGradle().getStartParameter()
        val taskPaths: MutableList<String?> = ArrayList<String?>()
        taskPaths.addAll(startParameter.taskNames)

        val isSyncModel: Boolean = isSyncModel(modelName)
        val isAutoBuildModel: Boolean = isAutoBuildModel(modelName)

        for (p in project.getAllprojects()) {
            val model: EclipseModel? = p.getExtensions().findByType<EclipseModel?>(EclipseModel::class.java)
            if (model != null) {
                if (isSyncModel) {
                    for (t in TaskDependencyUtil.getDependenciesForInternalUse(model.getSynchronizationTasks(), null)) {
                        taskPaths.add(t.getPath())
                    }
                }
                if (isAutoBuildModel) {
                    for (t in TaskDependencyUtil.getDependenciesForInternalUse(model.getAutoBuildTasks(), null)) {
                        taskPaths.add(t.getPath())
                    }
                }
            }
        }

        if (taskPaths.isEmpty()) {
            // If no tasks is specified then the default tasks will be executed.
            // To work around this, we assign a new empty task for execution.
            val placeHolderTaskName: String = placeHolderTaskName(project, "nothing")
            project.getTasks().register(placeHolderTaskName)
            taskPaths.add(placeHolderTaskName)
        }

        project.getGradle().getStartParameter().taskNames = taskPaths
        return null
    }

    companion object {
        private fun isSyncModel(modelName: String): Boolean {
            return modelName == "org.gradle.tooling.model.eclipse.RunEclipseSynchronizationTasks"
        }


        private fun isAutoBuildModel(modelName: String): Boolean {
            return modelName == "org.gradle.tooling.model.eclipse.RunEclipseAutoBuildTasks"
        }

        private fun placeHolderTaskName(project: Project, baseName: String): String {
            if (project.getTasks().findByName(baseName) == null) {
                return baseName
            } else {
                return placeHolderTaskName(project, baseName + "_")
            }
        }
    }
}
