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
package org.gradle.integtests.tooling.r18

import org.gradle.integtests.tooling.r16.CustomModel
import org.gradle.tooling.BuildAction
import kotlin.collections.HashMap
import kotlin.collections.MutableMap

class UseOtherTypesToFetchProjectModel : BuildAction<MutableMap<String?, CustomModel?>?> {
    fun execute(controller: BuildController): MutableMap<String?, CustomModel?> {
        // Use an IdeaModule to reference a project
        val ideaProject: IdeaProject = controller.getModel(IdeaProject::class.java)
        for (ideaModule in ideaProject.getModules()) {
            visit(ideaModule, controller, HashMap<String?, CustomModel?>())
        }

        // Use an EclipseProject to reference a project
        val eclipseProject: EclipseProject = controller.getModel(EclipseProject::class.java)
        visit(eclipseProject, controller, HashMap<String?, CustomModel?>())

        // Use a GradleProject to reference a project
        val rootProject: GradleProject = controller.getModel(GradleProject::class.java)
        val projects: MutableMap<String?, CustomModel?> = HashMap<String?, CustomModel?>()
        visit(rootProject, controller, projects)
        return projects
    }

    fun visit(element: HierarchicalElement, buildController: BuildController, results: MutableMap<String?, CustomModel?>) {
        results.put(element.getName(), buildController.getModel(element, CustomModel::class.java))
        for (child in element.getChildren()) {
            visit(child, buildController, results)
        }
    }
}
