/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.tooling.internal.consumer

import org.gradle.tooling.BuildAction
import org.gradle.tooling.IntermediateResultHandler

class DefaultPhasedBuildAction internal constructor(
    private val projectsLoadedAction: PhasedBuildAction.BuildActionWrapper<*>?,
    private val buildFinishedAction: PhasedBuildAction.BuildActionWrapper<*>?
) : PhasedBuildAction {
    override fun getProjectsLoadedAction(): PhasedBuildAction.BuildActionWrapper<*>? {
        return projectsLoadedAction
    }

    override fun getBuildFinishedAction(): PhasedBuildAction.BuildActionWrapper<*>? {
        return buildFinishedAction
    }

    internal class DefaultBuildActionWrapper<T>(private val buildAction: BuildAction<T?>?, private val resultHandler: IntermediateResultHandler<in T?>?) : PhasedBuildAction.BuildActionWrapper<T?> {
        override fun getAction(): BuildAction<T?>? {
            return buildAction
        }

        override fun getHandler(): IntermediateResultHandler<in T?>? {
            return resultHandler
        }
    }
}
