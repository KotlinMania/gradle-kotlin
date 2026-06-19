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

import org.gradle.tooling.*
import org.gradle.tooling.model.*
import org.gradle.tooling.model.build.*
import org.gradle.tooling.model.eclipse.*
import org.gradle.tooling.model.gradle.*
import org.gradle.tooling.model.idea.*
import org.gradle.tooling.model.kotlin.dsl.*
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter
import java.io.File
import org.gradle.integtests.tooling.r48.*

import org.gradle.integtests.tooling.r16.CustomModel
import org.gradle.tooling.BuildAction
import java.util.TreeMap
import java.util.stream.Collectors
import kotlin.collections.ArrayList
import kotlin.collections.MutableList
import kotlin.collections.MutableMap

internal class FetchCustomModelPerProjectAction : BuildAction<Result<MutableMap<String?, String?>?>?> {
    public override fun execute(controller: BuildController?): Result<MutableMap<String?, String?>?> {
        val gradleBuildResult: FetchModelResult<GradleBuild?> = controller.fetch(GradleBuild::class.java)
        assert(gradleBuildResult.getModel() is GradleBuild)
        assert(gradleBuildResult.getFailures().isEmpty())
        val gradleBuild: GradleBuild = checkNotNull(gradleBuildResult.getModel())
        val failures: MutableList<String?> = ArrayList<String?>()
        val causes: MutableList<String?> = ArrayList<String?>()
        val values: MutableMap<String?, String?> = TreeMap<String?, String?>()
        for (project in gradleBuild.getProjects()) {
            val result: FetchModelResult<CustomModel?> = controller.fetch(CustomModel::class.java)
            val model: CustomModel? = result.getModel()
            values.put(project.getName(), if (model != null) model.value else null)
            failures.addAll(result.getFailures().stream().map { it.getMessage() }.collect(Collectors.toList()))
            causes.addAll(result.getFailures().stream().flatMap({ f -> f.getCauses().stream() }).map { it.getMessage() }.collect(Collectors.toList()))
        }
        return Result<MutableMap<String?, String?>?>(values, failures, causes)
    }
}
