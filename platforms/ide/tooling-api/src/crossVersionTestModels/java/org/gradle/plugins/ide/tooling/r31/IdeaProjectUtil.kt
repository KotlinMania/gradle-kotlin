/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.plugins.ide.tooling.r31

import org.gradle.tooling.BuildAction
import java.io.Serializable
import kotlin.collections.ArrayList
import kotlin.collections.LinkedHashMap
import kotlin.collections.MutableList
import kotlin.collections.MutableMap

class IdeaProjectUtil {
    class GetAllIdeaProjectsAction : BuildAction<AllProjects?> {
        public override fun execute(controller: BuildController): AllProjects {
            val result = AllProjects()

            val buildModel: GradleBuild = controller.getBuildModel()
            result.rootBuild = buildModel
            result.rootIdeaProject = controller.getModel(IdeaProject::class.java)

            collectAllNestedBuilds(buildModel, controller, result)

            result.allIdeaProjects.add(result.rootIdeaProject)
            val includedWithoutRoot: LinkedHashMap<GradleBuild?, IdeaProject?> = LinkedHashMap<GradleBuild?, IdeaProject?>(result.includedBuildIdeaProjects)
            includedWithoutRoot.remove(buildModel)
            result.allIdeaProjects.addAll(includedWithoutRoot.values)

            return result
        }

        private fun collectAllNestedBuilds(buildModel: GradleBuild, controller: BuildController, result: AllProjects) {
            for (includedBuild in buildModel.getIncludedBuilds()) {
                if (!result.includedBuildIdeaProjects.containsKey(includedBuild)) {
                    val includedBuildProject: IdeaProject? = controller.getModel(includedBuild, IdeaProject::class.java)
                    result.includedBuildIdeaProjects.put(includedBuild, includedBuildProject)
                    collectAllNestedBuilds(includedBuild, controller, result)
                }
            }
        }
    }

    class AllProjects : Serializable {
        var rootBuild: GradleBuild? = null
        var rootIdeaProject: IdeaProject? = null
        var allIdeaProjects: MutableList<IdeaProject> = ArrayList<IdeaProject>()
        val includedBuildIdeaProjects: MutableMap<GradleBuild?, IdeaProject?> = LinkedHashMap<GradleBuild?, IdeaProject?>()

        fun getIdeaProject(name: String?): IdeaProject? {
            for (ideaProject in allIdeaProjects) {
                if (ideaProject.getName().equals(name)) {
                    return ideaProject
                }
            }
            return null
        }
    }
}
