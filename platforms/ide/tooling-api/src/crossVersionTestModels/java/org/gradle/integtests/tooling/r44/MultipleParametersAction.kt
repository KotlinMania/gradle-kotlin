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
package org.gradle.integtests.tooling.r44

import org.gradle.api.Action
import java.util.LinkedList

class MultipleParametersAction : BuildAction<MutableList<CustomModel?>?> {
    public override fun execute(controller: BuildController): MutableList<CustomModel?> {
        val result: MutableList<CustomModel?> = LinkedList<CustomModel?>()
        val model0: CustomModel = controller.getModel(CustomModel::class.java)
        val model1: CustomModel = controller.getModel(CustomModel::class.java, CustomParameter::class.java, object : Action<CustomParameter?>() {
            public override fun execute(customParameter: CustomParameter) {
                customParameter.setValue(model0.getParameterValue() + ":parameter1")
            }
        })
        val model2: CustomModel? = controller.getModel(CustomModel::class.java, CustomParameter::class.java, object : Action<CustomParameter?>() {
            public override fun execute(customParameter: CustomParameter) {
                customParameter.setValue(model1.getParameterValue() + ":parameter2")
            }
        })
        result.add(model0)
        result.add(model1)
        result.add(model2)
        return result
    }
}
