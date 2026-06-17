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
package org.gradle.integtests.tooling.r55

import org.gradle.api.Action
import java.io.Serializable
import kotlin.collections.ArrayList
import kotlin.collections.MutableCollection

class SupplyRuntimeAndLoadCompositeEclipseModels(workspace: EclipseWorkspace?) : BuildAction<MutableCollection<EclipseProject?>?>, Serializable {
    private val workspace: EclipseWorkspace?

    init {
        this.workspace = workspace
    }

    public override fun execute(controller: BuildController): MutableCollection<EclipseProject?> {
        val models: MutableCollection<EclipseProject?> = ArrayList<EclipseProject?>()
        collectRootModels(controller, controller.getBuildModel(), models)
        return models
    }

    private fun collectRootModels(controller: BuildController, build: GradleBuild, models: MutableCollection<EclipseProject?>) {
        models.add(controller.getModel(build.getRootProject(), EclipseProject::class.java, EclipseRuntime::class.java, EclipseRuntimeAction(workspace)))

        for (includedBuild in build.getIncludedBuilds()) {
            collectRootModels(controller, includedBuild, models)
        }
    }

    private class EclipseRuntimeAction(eclipseWorkspace: EclipseWorkspace?) : Action<EclipseRuntime?>, Serializable {
        private val eclipseWorkspace: EclipseWorkspace?

        init {
            this.eclipseWorkspace = eclipseWorkspace
        }

        public override fun execute(eclipseRuntime: EclipseRuntime) {
            eclipseRuntime.setWorkspace(eclipseWorkspace)
        }
    }
}
