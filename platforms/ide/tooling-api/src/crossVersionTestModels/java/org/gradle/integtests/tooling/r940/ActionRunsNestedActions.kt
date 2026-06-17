/*
 * Copyright 2025 the original author or authors.
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
package org.gradle.integtests.tooling.r940

import org.gradle.tooling.BuildAction
import java.io.Serializable
import kotlin.collections.ArrayList
import kotlin.collections.MutableCollection
import kotlin.collections.MutableList

class ActionRunsNestedActions : BuildAction<ActionRunsNestedActions.Models?> {
    public override fun execute(controller: BuildController): Models {
        val buildModel: GradleBuild = controller.getBuildModel()
        val projectActions: MutableList<NestedAction?> = ArrayList<NestedAction?>()
        for (project in buildModel.getProjects()) {
            projectActions.add(NestedAction(project))
        }
        val results: MutableList<CustomModel?> = controller.run(projectActions)
        return Models(controller.getCanQueryProjectModelInParallel(CustomModel::class.java), results)
    }

    internal class NestedAction(project: BasicGradleProject?) : BuildAction<CustomModel?> {
        private val project: BasicGradleProject?

        init {
            this.project = project
        }

        public override fun execute(controller: BuildController): CustomModel {
            return controller.getModel(project, CustomModel::class.java)
        }
    }

    class Models(val isMayRunInParallel: Boolean, subs: MutableCollection<CustomModel?>) : Serializable {
        val subResults: MutableList<CustomModel?>

        init {
            this.subResults = ArrayList<CustomModel?>(subs)
        }
    }

    interface CustomModel {
        val path: String?
    }
}
