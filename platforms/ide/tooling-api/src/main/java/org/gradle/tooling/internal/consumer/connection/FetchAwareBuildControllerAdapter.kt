/*
 * Copyright 2025 the original author or authors.
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

import org.gradle.api.Action
import org.gradle.tooling.FetchModelResult
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter
import org.gradle.tooling.internal.consumer.DefaultFetchModelResult
import org.gradle.tooling.internal.consumer.versioning.ModelMapping
import org.gradle.tooling.internal.consumer.versioning.VersionDetails
import org.gradle.tooling.internal.protocol.InternalBuildControllerVersion2
import org.gradle.tooling.internal.protocol.InternalFetchAwareBuildController
import org.gradle.tooling.internal.protocol.InternalFetchModelResult
import org.gradle.tooling.model.Model
import org.jspecify.annotations.NullMarked
import java.io.File

@NullMarked
internal class FetchAwareBuildControllerAdapter(
    buildController: InternalBuildControllerVersion2,
    adapter: ProtocolToModelAdapter,
    modelMapping: ModelMapping,
    gradleVersion: VersionDetails,
    rootDir: File
) : StreamingAwareBuildControllerAdapter(buildController, adapter, modelMapping, gradleVersion, rootDir) {
    private val fetch: InternalFetchAwareBuildController

    init {
        fetch = buildController as InternalFetchAwareBuildController
    }

    override fun <M, P> fetch(
        target: Model?,
        modelType: Class<M?>?,
        parameterType: Class<P?>?,
        parameterInitializer: Action<in P?>?
    ): FetchModelResult<M?> {
        val originalTarget = unpackModelTarget(target)
        val modelIdentifier = getModelIdentifierFromModelType<M?>(modelType!!)
        val parameter: P? = if (parameterInitializer != null) UnparameterizedBuildController.Companion.initializeParameter<P?>(parameterType, parameterInitializer) else null
        val result = fetch.fetch<Any>(originalTarget, modelIdentifier, parameter)!!
        return adaptResult<Model?, M?>(target, modelType, result)
    }

    private fun <T : Model?, M> adaptResult(target: T?, modelType: Class<M?>, result: InternalFetchModelResult<Any?>): FetchModelResult<M?> {
        val model = result.model
        val adaptedModel = if (model != null) adaptModel<M?>(target, modelType, model) else null
        return DefaultFetchModelResult.of<M?>(adaptedModel, result.failures ?: mutableListOf())
    }
}
