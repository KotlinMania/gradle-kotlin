/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.integtests.tooling.r56

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

import org.gradle.api.Action
import java.io.Serializable
import kotlin.collections.ArrayList
import kotlin.collections.MutableCollection

class ParameterizedLoadCompositeEclipseModels<T>(workspace: EclipseWorkspace?, modelClass: Class<T?>?) : BuildAction<MutableCollection<T?>?>, Serializable {
    private val workspace: EclipseWorkspace?
    private val modelClass: Class<T?>?

    init {
        this.workspace = workspace
        this.modelClass = modelClass
    }

    public override fun execute(controller: BuildController?): MutableCollection<T?> {
        val models: MutableCollection<T?> = ArrayList<T?>()
        collectRootModels(controller, controller.getBuildModel(), models)
        return models
    }

    private fun collectRootModels(controller: BuildController?, build: GradleBuild, models: MutableCollection<T?>) {
        if (workspace != null) {
            models.add(controller.getModel(build.getRootProject(), modelClass, EclipseRuntime::class.java, EclipseRuntimeAction(workspace)))
        } else {
            models.add(controller.getModel(build.getRootProject(), modelClass))
        }
        for (includedBuild in build.getIncludedBuilds()) {
            collectRootModels(controller, includedBuild, models)
        }
    }

    private class EclipseRuntimeAction(workspace: EclipseWorkspace?) : Action<EclipseRuntime?>, Serializable {
        private val workspace: EclipseWorkspace?

        init {
            this.workspace = workspace
        }

        public override fun execute(eclipseRuntime: EclipseRuntime?) {
            eclipseRuntime.setWorkspace(workspace)
        }
    }
}
