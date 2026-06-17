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

internal class GradleBuildAction(private val resilient: Boolean) : BuildAction<GradleBuildModel?>, Serializable {
    public override fun execute(controller: BuildController): GradleBuildModel {
        if (resilient) {
            val result: FetchModelResult<GradleBuild?> = controller.fetch(GradleBuild::class.java)
            return GradleBuildModel(result.getModel(), result.getFailures())
        } else {
            val model: GradleBuild? = controller.getModel(GradleBuild::class.java)
            return GradleBuildModel(model, mutableListOf<Failure?>())
        }
    }
}
