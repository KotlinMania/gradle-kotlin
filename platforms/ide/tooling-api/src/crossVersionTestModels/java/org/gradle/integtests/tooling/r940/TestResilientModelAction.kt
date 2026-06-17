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
import kotlin.collections.ArrayList
import kotlin.collections.MutableList

class TestResilientModelAction(private val modelType: Class<*>, private val queryStrategy: QueryStrategy?) : BuildAction<TestResilientModelAction.Result?>, Serializable {
    public override fun execute(controller: BuildController): Result {
        val gradleBuild: GradleBuild = checkNotNull(controller.fetch(GradleBuild::class.java).getModel())
        val successfulQueriedProjects: MutableList<String?> = ArrayList<String?>()
        val failedQueriedProjects: MutableList<String?> = ArrayList<String?>()
        if (queryStrategy == QueryStrategy.ROOT_BUILD_FIRST) {
            queryRootBuild(controller, gradleBuild, successfulQueriedProjects, failedQueriedProjects)
            queryEditableBuilds(controller, gradleBuild, successfulQueriedProjects, failedQueriedProjects)
        } else {
            queryEditableBuilds(controller, gradleBuild, successfulQueriedProjects, failedQueriedProjects)
            queryRootBuild(controller, gradleBuild, successfulQueriedProjects, failedQueriedProjects)
        }
        return Result(successfulQueriedProjects, failedQueriedProjects)
    }

    private fun queryRootBuild(controller: BuildController, gradleBuild: GradleBuild, successfulQueriedProjects: MutableList<String?>, failedQueriedProjects: MutableList<String?>) {
        for (project in gradleBuild.getProjects()) {
            queryModelForProject(controller, project, successfulQueriedProjects, failedQueriedProjects)
        }
    }

    private fun queryEditableBuilds(controller: BuildController, gradleBuild: GradleBuild, successfulQueriedProjects: MutableList<String?>, failedQueriedProjects: MutableList<String?>) {
        for (includedBuild in gradleBuild.getEditableBuilds()) {
            for (project in includedBuild.getProjects()) {
                queryModelForProject(controller, project, successfulQueriedProjects, failedQueriedProjects)
            }
        }
    }

    private fun queryModelForProject(controller: BuildController, project: BasicGradleProject, successfulQueriedProjects: MutableList<String?>, failedQueriedProjects: MutableList<String?>) {
        val result: FetchModelResult<*> = controller.fetch(project, modelType)
        if (result.getFailures().isEmpty() && result.getModel() != null) {
            successfulQueriedProjects.add(project.getName())
        } else {
            println("Failed to query '" + modelType.getSimpleName() + "' for project '" + project.getName() + "'")
            failedQueriedProjects.add(project.getName())
        }
    }

    enum class QueryStrategy {
        ROOT_BUILD_FIRST,
        EDITABLE_BUILDS_FIRST
    }

    class Result(val successfullyQueriedProjects: MutableList<String?>?, val failedToQueryProjects: MutableList<String?>?) : Serializable
}
