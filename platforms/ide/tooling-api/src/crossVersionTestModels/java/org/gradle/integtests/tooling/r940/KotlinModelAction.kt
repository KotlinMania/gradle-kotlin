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

import org.gradle.tooling.*
import org.gradle.tooling.model.*
import org.gradle.tooling.model.build.*
import org.gradle.tooling.model.eclipse.*
import org.gradle.tooling.model.gradle.*
import org.gradle.tooling.model.idea.*
import org.gradle.tooling.model.kotlin.dsl.*

import org.gradle.tooling.BuildAction
import java.io.File
import org.gradle.integtests.tooling.r48.*
import java.io.Serializable
import java.util.Optional
import kotlin.collections.HashMap
import kotlin.collections.MutableMap

internal class KotlinModelAction(val queryStrategy: QueryStrategy?, val resilient: Boolean) : BuildAction<KotlinModel?>, Serializable {
    enum class QueryStrategy {
        ROOT_PROJECT_FIRST,
        INCLUDED_BUILDS_FIRST
    }

    public override fun execute(controller: BuildController?): KotlinModel {
        val rootBuild: GradleBuild
        if (resilient) {
            rootBuild = checkNotNull(controller.fetch(GradleBuild::class.java).getModel())
        } else {
            rootBuild = controller.getModel(GradleBuild::class.java)
        }
        val scriptModels: MutableMap<File?, KotlinDslScriptModel?> = HashMap<File?, KotlinDslScriptModel?>()
        val failures: MutableMap<File?, Failure?> = HashMap<File?, Failure?>()

        if (queryStrategy == QueryStrategy.ROOT_PROJECT_FIRST) {
            queryKotlinDslScriptsModel(controller, rootBuild, scriptModels, failures)
            for (build in rootBuild.getEditableBuilds()) {
                queryKotlinDslScriptsModel(controller, build, scriptModels, failures)
            }
        } else if (queryStrategy == QueryStrategy.INCLUDED_BUILDS_FIRST) {
            for (build in rootBuild.getEditableBuilds()) {
                queryKotlinDslScriptsModel(controller, build, scriptModels, failures)
            }
            queryKotlinDslScriptsModel(controller, rootBuild, scriptModels, failures)
        }

        return KotlinModel(scriptModels, failures)
    }

    private fun queryKotlinDslScriptsModel(controller: BuildController?, build: GradleBuild, scriptModels: MutableMap<File?, KotlinDslScriptModel?>, failures: MutableMap<File?, Failure?>) {
        if (resilient) {
            queryResilientKotlinDslScriptsModel(controller, build, build.getRootProject(), scriptModels, failures)
        } else {
            queryBasicKotlinDslScriptsModel(controller, build, scriptModels)
        }
    }

    companion object {
        fun queryResilientKotlinDslScriptsModel(
            controller: BuildController?,
            build: GradleBuild,
            target: Model?,
            scriptModels: MutableMap<File?, KotlinDslScriptModel?>,
            failures: MutableMap<File?, Failure?>
        ) {
            val modelResult: FetchModelResult<KotlinDslScriptsModel?> = controller.fetch(target, KotlinDslScriptsModel::class.java)

            assert(modelResult.getFailures().size <= 1) { "Expected a single failure, but got multiple ones" }
            val failure: Optional<out Failure?> = modelResult.getFailures().stream().findAny()
            if (failure.isPresent()) {
                failures.put(build.getBuildIdentifier().getRootDir(), failure.get())
            }

            if (modelResult.getModel() != null) {
                scriptModels.putAll(modelResult.getModel().getScriptModels())
            }
        }

        private fun queryBasicKotlinDslScriptsModel(controller: BuildController?, build: GradleBuild, scriptModels: MutableMap<File?, KotlinDslScriptModel?>) {
            val buildScriptModel: KotlinDslScriptsModel = controller.getModel(build.getRootProject(), KotlinDslScriptsModel::class.java)
            scriptModels.putAll(buildScriptModel.getScriptModels())
        }
    }
}
