/*
 * Copyright 2026 the original author or authors.
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

internal class SetStartParameterAction(private val resilient: Boolean) : BuildAction<String?>, Serializable {
    public override fun execute(controller: BuildController): String? {
        if (resilient) {
            val gradleBuild: GradleBuild? = controller.fetch(GradleBuild::class.java).getModel()
            if (gradleBuild != null) {
                val result: FetchModelResult<StartParametersModel?> = controller.fetch(gradleBuild.getRootProject(), StartParametersModel::class.java)
                return if (result.getFailures().isEmpty()) "successful" else "unsuccessful"
            }
            return "unsuccessful"
        } else {
            val gradleBuild: GradleBuild = controller.getModel(GradleBuild::class.java)
            val result: StartParametersModel = controller.getModel(gradleBuild.getRootProject(), StartParametersModel::class.java)
            return result.toString()
        }
    }
}
