/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.integtests.tooling.r48

import org.gradle.api.Action
import java.io.Serializable

class CustomProjectsLoadedAction(// Task graph is not calculated yet. Plugins can add tasks to it.
    private val tasks: MutableList<String?>?
) : BuildAction<String?>, Serializable {
    public override fun execute(controller: BuildController): String? {
        val model: CustomProjectsLoadedModel
        if (tasks == null || tasks.isEmpty()) {
            model = controller.getModel(CustomProjectsLoadedModel::class.java)
        } else {
            model = controller.getModel(CustomProjectsLoadedModel::class.java, CustomParameter::class.java, object : Action<CustomParameter?>() {
                public override fun execute(customParameter: CustomParameter) {
                    customParameter.setTasks(tasks)
                }
            })
        }
        return model.getValue()
    }
}
