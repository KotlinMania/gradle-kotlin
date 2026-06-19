/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.tooling.internal.consumer.connection

import org.gradle.tooling.BuildAction
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter
import org.gradle.tooling.internal.consumer.versioning.ModelMapping
import org.gradle.tooling.internal.consumer.versioning.VersionDetails
import org.gradle.tooling.internal.protocol.InternalActionAwareBuildController
import org.gradle.tooling.internal.protocol.InternalBuildControllerVersion2
import java.io.File
import java.util.function.Supplier

internal open class NestedActionAwareBuildControllerAdapter(
    buildController: InternalBuildControllerVersion2,
    adapter: ProtocolToModelAdapter,
    modelMapping: ModelMapping,
    gradleVersion: VersionDetails,
    rootDir: File
) : ParameterAwareBuildControllerAdapter(buildController, adapter, modelMapping, gradleVersion, rootDir) {
    private val controller: InternalActionAwareBuildController

    init {
        this.controller = buildController as InternalActionAwareBuildController
    }

    override fun getCanQueryProjectModelInParallel(modelType: Class<*>?): Boolean {
        return controller.getCanQueryProjectModelInParallel(modelType)
    }

    override fun <T> run(buildActions: MutableCollection<out BuildAction<out T?>?>?): MutableList<T?> {
        val wrappers: MutableList<Supplier<T?>?> = ArrayList<Supplier<T?>?>(buildActions?.size ?: 0)
        for (action in buildActions.orEmpty()) {
            wrappers.add(Supplier { action!!.execute(this@NestedActionAwareBuildControllerAdapter) })
        }
        return controller.run<T>(wrappers)!!
    }
}
