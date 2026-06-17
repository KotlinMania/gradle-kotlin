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
package org.gradle.integtests.tooling.r930

import org.gradle.tooling.BuildAction
import java.util.stream.Collectors

class FetchGradleBuildAction : BuildAction<Result<MutableList<String?>?>?> {
    public override fun execute(controller: BuildController): Result<MutableList<String?>?> {
        val result: FetchModelResult<GradleBuild?> = controller.fetch(null, GradleBuild::class.java, null, null)
        var projectNames: MutableList<String?>? = null
        if (result.getModel() != null) {
            assert(result.getModel() is GradleBuild)
            projectNames = result.getModel().getProjects().stream()
                .map(BasicGradleProject::getName)
                .collect(Collectors.toList())
        }
        val failures: MutableList<String?>? = result.getFailures().stream()
            .map(Failure::getMessage)
            .collect(Collectors.toList())
        val causes: MutableList<String?>? = result.getFailures().stream()
            .flatMap({ f -> f.getCauses().stream() })
            .map(Failure::getMessage)
            .collect(Collectors.toList())
        return Result<MutableList<String?>?>(projectNames, failures, causes)
    }
}
