/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.integtests.tooling.r68

import org.gradle.tooling.BuildAction
import java.util.Arrays
import kotlin.collections.ArrayList
import kotlin.collections.MutableList

class ActionRunsMultipleLevelsOfNestedActions : BuildAction<MutableList<Models?>?> {
    public override fun execute(controller: BuildController): MutableList<Models?> {
        val buildModel: GradleBuild = controller.getBuildModel()
        val projectActions: MutableList<GetModelViaNestedAction?> = ArrayList<GetModelViaNestedAction?>()
        for (project in buildModel.getProjects()) {
            projectActions.add(GetModelViaNestedAction(project))
        }
        return controller.run(projectActions)
    }

    internal class GetModelViaNestedAction(project: BasicGradleProject?) : BuildAction<Models?> {
        private val project: BasicGradleProject?

        init {
            this.project = project
        }

        public override fun execute(controller: BuildController): Models {
            val models: MutableList<CustomModel?>? = controller.run(
                Arrays.asList<T?>(
                    ActionRunsNestedActions.GetProjectModel(project),
                    ActionRunsNestedActions.GetProjectModel(project),
                    ActionRunsNestedActions.GetProjectModel(project),
                    ActionRunsNestedActions.GetProjectModel(project),
                    ActionRunsNestedActions.GetProjectModel(project)
                )
            )
            return Models(controller.getCanQueryProjectModelInParallel(CustomModel::class.java), models)
        }
    }
}
