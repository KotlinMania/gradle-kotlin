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
package org.gradle.integtests.tooling.r30

import org.gradle.integtests.tooling.r16.CustomModel
import org.gradle.tooling.BuildAction
import kotlin.collections.HashMap
import kotlin.collections.MutableMap

class ComplexCustomModelBuildingAction : BuildAction<MutableMap<String?, CustomModel?>?> {
    public override fun execute(controller: BuildController): MutableMap<String?, CustomModel> {
        val result: MutableMap<String?, CustomModel> = HashMap<String?, CustomModel>()
        for (project in controller.getBuildModel().getProjects()) {
            result.put(project.getPath(), controller.getModel(project, CustomModel::class.java))
        }

        val rootProjectModel: CustomModel = controller.getModel(controller.getBuildModel().getRootProject(), CustomModel::class.java)
        for (customModel in result.values) {
            assert(customModel.getThing() === rootProjectModel.getThing())
        }

        return result
    }
}
