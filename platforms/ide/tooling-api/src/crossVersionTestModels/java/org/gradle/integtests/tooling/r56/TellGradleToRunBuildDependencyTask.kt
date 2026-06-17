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

import org.gradle.api.Action
import java.io.Serializable

class TellGradleToRunBuildDependencyTask(workspace: EclipseWorkspace?) : BuildAction<Void?>, Serializable {
    private val workspace: EclipseWorkspace?

    init {
        this.workspace = workspace
    }

    public override fun execute(controller: BuildController): Void? {
        if (workspace != null) {
            controller.getModel(RunClosedProjectBuildDependencies::class.java, EclipseRuntime::class.java, EclipseRuntimeAction(workspace))
        } else {
            controller.getModel(RunClosedProjectBuildDependencies::class.java)
        }
        return null
    }

    private class EclipseRuntimeAction(workspace: EclipseWorkspace?) : Action<EclipseRuntime?>, Serializable {
        private val workspace: EclipseWorkspace?

        init {
            this.workspace = workspace
        }

        public override fun execute(eclipseRuntime: EclipseRuntime) {
            eclipseRuntime.setWorkspace(workspace)
        }
    }
}
